package com.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.common.auth.JwtUtil;
import com.platform.common.exception.BusinessException;
import com.platform.common.security.Sm4Util;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AdvancedAuthServiceTest {
    private MutableClock clock;
    private JwtUtil jwt;
    private AuthService auth;
    private CertificateFixtures.Fixture certificates;
    private CertificateTrustValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        clock = new MutableClock(Instant.parse("2026-07-15T08:00:00Z"));
        jwt = new JwtUtil("jwt-test-secret", clock);
        auth = new AuthService(jwt);
        certificates = CertificateFixtures.create(clock.instant());
        validator = new CertificateTrustValidator(certificates.trustStore(), certificates.cleanCrl(), clock);
    }

    @Test
    void mfaBindingEncryptsSecretAndSecondFactorRejectsReplay() {
        AdvancedAuthService service = service(new AdvancedAuthRepository(null));
        String token = auth.login("admin", "admin123");
        String secret = bindMfa(service, token);
        assertNotEquals(secret, service.encryptedMfaSecret("admin"));
        assertEquals(secret, Sm4Util.decrypt(service.encryptedMfaSecret("admin"), "mfa-sm4-test-key"));

        clock.advanceSeconds(30);
        String code = Totp.generate(secret, clock.instant().getEpochSecond() / 30);
        assertEquals("admin", auth.parse(service.completeChallenge(
                service.startChallenge("admin"), code)).username());
        assertThrows(BusinessException.class, () -> service.completeChallenge(
                service.startChallenge("admin"), code));
    }

    @Test
    void consumedTotpCannotUnbindMfaButNewTotpCan() {
        AdvancedAuthRepository repository = new AdvancedAuthRepository(null);
        AdvancedAuthService service = service(repository);
        String token = auth.login("admin", "admin123");
        String secret = bindMfa(service, token);

        clock.advanceSeconds(30);
        String consumedCode = Totp.generate(secret, clock.instant().getEpochSecond() / 30);
        service.completeChallenge(service.startChallenge("admin"), consumedCode);

        BusinessException replay = assertThrows(BusinessException.class,
                () -> service.unbindMfa(token, consumedCode));
        assertEquals("AUTH-401", replay.code());
        assertEquals("MFA replay rejected", replay.getMessage());
        assertTrue(service.mfaEnabled("admin"));

        clock.advanceSeconds(30);
        String newCode = Totp.generate(secret, clock.instant().getEpochSecond() / 30);
        service.unbindMfa(token, newCode);
        assertFalse(service.mfaEnabled("admin"));
    }

    @Test
    void mfaChallengeCannotBeDecodedIntoBearerTokenWithoutSecondFactor() {
        AdvancedAuthService service = service(new AdvancedAuthRepository(null));
        String firstFactorToken = auth.login("admin", "admin123");
        bindMfa(service, firstFactorToken);

        String challenge = service.startChallenge("admin");
        String[] parts = challenge.split("\\.");
        assertEquals(4, parts.length);
        assertFalse(challenge.contains(firstFactorToken));
        assertThrows(BusinessException.class, () -> jwt.parse(challenge));

        String oldAttackExtraction = new String(
                Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThrows(BusinessException.class, () -> jwt.parse(oldAttackExtraction));
    }

    @Test
    void restartWithJdbcStillRejectsOldTotpReplay() {
        JdbcTemplate jdbc = jdbc();
        System.setProperty("AUTH_BOOTSTRAP_ADMIN_PASSWORD", "test-admin-pw");
        AuthService jdbcAuth = new AuthService(jwt, jdbc);
        AdvancedAuthRepository repository = new AdvancedAuthRepository(jdbc);
        AdvancedAuthService first = new AdvancedAuthService(jdbcAuth, jwt, clock, "mfa-sm4-test-key",
                new DisabledSsoAdapter(), repository, validator);
        String token = jdbcAuth.login("admin", "test-admin-pw");
        String secret = bindMfa(first, token);
        clock.advanceSeconds(30);
        String code = Totp.generate(secret, clock.instant().getEpochSecond() / 30);
        first.completeChallenge(first.startChallenge("admin"), code);

        AdvancedAuthService restarted = new AdvancedAuthService(
                new AuthService(jwt, jdbc), jwt, clock, "mfa-sm4-test-key",
                new DisabledSsoAdapter(), new AdvancedAuthRepository(jdbc), validator);
        assertThrows(BusinessException.class, () -> restarted.completeChallenge(
                restarted.startChallenge("admin"), code));
    }

    @Test
    void concurrentUseOfSameTotpAllowsOnlyOne() throws Exception {
        AdvancedAuthService service = service(new AdvancedAuthRepository(null));
        String token = auth.login("admin", "admin123");
        String secret = bindMfa(service, token);
        clock.advanceSeconds(30);
        String code = Totp.generate(secret, clock.instant().getEpochSecond() / 30);
        String one = service.startChallenge("admin");
        String two = service.startChallenge("admin");
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> first = () -> accepted(() -> service.completeChallenge(one, code));
            Callable<Boolean> second = () -> accepted(() -> service.completeChallenge(two, code));
            var results = pool.invokeAll(java.util.List.of(first, second));
            long accepted = results.stream().filter(value -> {
                try { return value.get(); } catch (Exception ex) { return false; }
            }).count();
            assertEquals(1, accepted);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void caCertificatePassesWhileSelfSignedExpiredAndRevokedFail() {
        validator.validate(certificates.leaf());
        assertThrows(BusinessException.class, () -> validator.validate(certificates.selfSigned()));
        assertThrows(BusinessException.class, () -> validator.validate(certificates.expired()));
        CertificateTrustValidator revokedValidator =
                new CertificateTrustValidator(certificates.trustStore(), certificates.crl(), clock);
        assertThrows(BusinessException.class, () -> revokedValidator.validate(certificates.leaf()));
    }

    @Test
    void revocationStatusIsFailClosedAndMultiLevelCaChainPasses() throws Exception {
        CertificateTrustValidator missingRevocation =
                new CertificateTrustValidator(certificates.trustStore(), (java.security.cert.X509CRL) null, clock);
        assertThrows(BusinessException.class, () -> missingRevocation.validate(certificates.leaf()));

        CertificateTrustValidator multiLevelValidator = new CertificateTrustValidator(
                certificates.trustStore(),
                java.util.List.of(certificates.cleanCrl(), certificates.intermediateCrl()), clock);
        multiLevelValidator.validate(java.util.List.of(certificates.multiLeaf(), certificates.intermediate()));

        AdvancedAuthService service = new AdvancedAuthService(auth, jwt, clock, "mfa-sm4-test-key",
                new DisabledSsoAdapter(), new AdvancedAuthRepository(null), multiLevelValidator);
        String token = auth.login("admin", "admin123");
        String secret = bindMfa(service, token);
        String pemChain = CertificateFixtures.chainPem(certificates.multiLeaf(), certificates.intermediate());
        String fingerprint = service.bindCertificate(token, pemChain);
        AdvancedAuthService.CertificateChallengeResponse challenge = service.certificateChallenge(fingerprint);
        String result = service.certificateLogin(pemChain, challenge.challengeId(),
                sign(challenge.challenge(), certificates.multiLeafPrivateKey()));
        clock.advanceSeconds(30);
        String code = Totp.generate(secret, clock.instant().getEpochSecond() / 30);
        assertEquals("admin", auth.parse(service.completeChallenge(
                result.substring("MFA_REQUIRED:".length()), code)).username());
    }

    @Test
    void certificateRequiresPrivateKeyProofAndCurrentPermissions() throws Exception {
        AdvancedAuthService service = service(new AdvancedAuthRepository(null));
        String token = auth.login("admin", "admin123");
        String secret = bindMfa(service, token);
        String pem = CertificateFixtures.pem(certificates.leaf());
        String fingerprint = service.bindCertificate(token, pem);
        AdvancedAuthService.CertificateChallengeResponse challenge =
                service.certificateChallenge(fingerprint);

        assertThrows(BusinessException.class, () -> service.certificateLogin(pem, null, null));
        String signature = sign(challenge.challenge(), certificates.leafPrivateKey());
        auth.updateUser("admin", Set.of("stats:view"));
        String result = service.certificateLogin(pem, challenge.challengeId(), signature);
        assertTrue(result.startsWith("MFA_REQUIRED:"));
        clock.advanceSeconds(30);
        String mfaCode = Totp.generate(secret, clock.instant().getEpochSecond() / 30);
        String issued = service.completeChallenge(result.substring("MFA_REQUIRED:".length()), mfaCode);
        assertEquals(Set.of("stats:view"), auth.parse(issued).permissions());
    }

    @Test
    void revokedCertificateCannotStartLogin() throws Exception {
        AdvancedAuthService service = service(new AdvancedAuthRepository(null));
        String token = auth.login("admin", "admin123");
        String fingerprint = service.bindCertificate(token, CertificateFixtures.pem(certificates.leaf()));
        service.revokeCertificate(token, fingerprint);
        assertThrows(BusinessException.class, () -> service.certificateChallenge(fingerprint));
    }

    @Test
    void certificateBindingAndRevocationPersistAcrossRepositoryRestart() throws Exception {
        JdbcTemplate jdbc = jdbc();
        System.setProperty("AUTH_BOOTSTRAP_ADMIN_PASSWORD", "test-admin-pw");
        AuthService jdbcAuth = new AuthService(jwt, jdbc);
        AdvancedAuthRepository firstRepository = new AdvancedAuthRepository(jdbc);
        AdvancedAuthService first = new AdvancedAuthService(jdbcAuth, jwt, clock, "mfa-sm4-test-key",
                new DisabledSsoAdapter(), firstRepository, validator);
        String token = jdbcAuth.login("admin", "test-admin-pw");
        String fingerprint = first.bindCertificate(token, CertificateFixtures.pem(certificates.leaf()));

        AdvancedAuthRepository restartedRepository = new AdvancedAuthRepository(jdbc);
        assertEquals("ACTIVE", restartedRepository.certificate(fingerprint).status());
        restartedRepository.revokeCertificate(fingerprint);
        assertEquals("REVOKED", new AdvancedAuthRepository(jdbc).certificate(fingerprint).status());
    }

    @Test
    void missingOrWeakSm4KeyFailsFast() {
        assertThrows(IllegalStateException.class, () -> new AdvancedAuthService(
                auth, jwt, clock, "", new DisabledSsoAdapter(),
                new AdvancedAuthRepository(null), validator));
        assertThrows(IllegalStateException.class, () -> new AdvancedAuthService(
                auth, jwt, clock, "change-me-in-env", new DisabledSsoAdapter(),
                new AdvancedAuthRepository(null), validator));
    }

    @Test
    void productionSsoRejectsArbitraryUsernameCodeAndMockConfiguration() {
        AdvancedAuthService service = service(new AdvancedAuthRepository(null));
        assertThrows(BusinessException.class, service::ssoRedirect);
        MockSsoAdapter mock = new MockSsoAdapter("https://mock.invalid", "client");
        assertThrows(BusinessException.class, () -> mock.callback("admin", "state"));

        org.springframework.mock.env.MockEnvironment prod =
                new org.springframework.mock.env.MockEnvironment().withProperty("spring.profiles.active", "prod");
        prod.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, () ->
                new SsoConfiguration().ssoAdapter(true, "https://mock.invalid", "client", prod));

        org.springframework.mock.env.MockEnvironment production =
                new org.springframework.mock.env.MockEnvironment();
        production.setActiveProfiles("production");
        assertThrows(IllegalStateException.class, () ->
                new SsoConfiguration().ssoAdapter(true, "https://mock.invalid", "client", production));

        org.springframework.mock.env.MockEnvironment defaultProfile =
                new org.springframework.mock.env.MockEnvironment();
        assertThrows(IllegalStateException.class, () ->
                new SsoConfiguration().ssoAdapter(true, "https://mock.invalid", "client", defaultProfile));

        assertTrue(new SsoConfiguration().ssoAdapter(
                false, "https://mock.invalid", "client", defaultProfile) instanceof DisabledSsoAdapter);

        org.springframework.mock.env.MockEnvironment test =
                new org.springframework.mock.env.MockEnvironment();
        test.setActiveProfiles("test");
        assertTrue(new SsoConfiguration().ssoAdapter(
                true, "https://mock.invalid", "client", test) instanceof MockSsoAdapter);

        org.springframework.mock.env.MockEnvironment dev =
                new org.springframework.mock.env.MockEnvironment();
        dev.setActiveProfiles("dev");
        assertTrue(new SsoConfiguration().ssoAdapter(
                true, "https://mock.invalid", "client", dev) instanceof MockSsoAdapter);
    }

    private AdvancedAuthService service(AdvancedAuthRepository repository) {
        return new AdvancedAuthService(auth, jwt, clock, "mfa-sm4-test-key",
                new DisabledSsoAdapter(), repository, validator);
    }

    private String bindMfa(AdvancedAuthService service, String token) {
        String secret = service.beginMfaBinding(token);
        service.confirmMfaBinding(token,
                Totp.generate(secret, clock.instant().getEpochSecond() / 30));
        assertTrue(service.mfaEnabled("admin"));
        return secret;
    }

    private String sign(String challenge, java.security.PrivateKey key) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(challenge.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private boolean accepted(Runnable action) {
        try {
            action.run();
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    private JdbcTemplate jdbc() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:advanced_auth_rework;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        Flyway.configure().dataSource(dataSource)
                .locations("filesystem:../db/migration").cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource)
                .locations("filesystem:../db/migration").load().migrate();
        return new JdbcTemplate(dataSource);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
