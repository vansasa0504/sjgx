package com.platform.auth;

import com.platform.common.auth.AuthPrincipal;
import com.platform.common.auth.JwtUtil;
import com.platform.common.exception.BusinessException;
import com.platform.common.security.Sm4Util;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.naming.ldap.LdapName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdvancedAuthService {
    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final Clock clock;
    private final String sm4Key;
    private final SsoAdapter ssoAdapter;
    private final AdvancedAuthRepository repository;
    private final CertificateTrustValidator certificateValidator;
    private final Map<String, String> ssoStates = new ConcurrentHashMap<>();
    private final Map<String, CertificateChallenge> certificateChallenges = new ConcurrentHashMap<>();

    @Autowired
    public AdvancedAuthService(AuthService authService, JwtUtil jwtUtil,
            @Autowired(required = false) JdbcTemplate jdbcTemplate, SsoAdapter ssoAdapter,
            @Value("${security.mfa.sm4-key}") String sm4Key,
            @Value("${security.cert.truststore}") String trustStore,
            @Value("${security.cert.truststore-password:}") String trustStorePassword,
            @Value("${security.cert.crl:}") String crlPath) {
        this(authService, jwtUtil, Clock.systemUTC(), sm4Key, ssoAdapter,
                new AdvancedAuthRepository(jdbcTemplate),
                CertificateTrustValidator.load(trustStore, trustStorePassword, crlPath, Clock.systemUTC()));
    }

    AdvancedAuthService(AuthService authService, JwtUtil jwtUtil, Clock clock, String sm4Key,
            SsoAdapter adapter, AdvancedAuthRepository repository, CertificateTrustValidator validator) {
        if (sm4Key == null || sm4Key.isBlank() || "change-me-in-env".equals(sm4Key)) {
            throw new IllegalStateException("security.mfa.sm4-key must be configured securely");
        }
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.clock = clock;
        this.sm4Key = sm4Key;
        this.ssoAdapter = adapter;
        this.repository = repository;
        this.certificateValidator = validator;
    }

    public boolean mfaEnabled(String username) {
        AdvancedAuthRepository.MfaState binding = repository.mfa(username);
        return binding != null && binding.enabled();
    }

    /** Stateless, signed first-factor challenge. */
    public String startChallenge(String username) {
        long expires = clock.instant().getEpochSecond() + 300;
        String payload = encode(username) + "." + expires + "." + UUID.randomUUID();
        return payload + "." + sign(payload);
    }

    public String completeChallenge(String challenge, String code) {
        ChallengePayload payload = verifyChallenge(challenge);
        AdvancedAuthRepository.MfaState binding = requiredMfa(payload.username());
        long counter = Totp.verify(Sm4Util.decrypt(binding.secretCipher(), sm4Key), code,
                clock.instant().getEpochSecond());
        if (counter < 0) throw new BusinessException("AUTH-401", "invalid MFA code");
        if (!repository.advanceCounter(payload.username(), counter)) {
            throw new BusinessException("AUTH-401", "MFA replay rejected");
        }
        return jwtUtil.issue(payload.username(), authService.permissionsFor(payload.username()), 3600);
    }

    public String beginMfaBinding(String bearerToken) {
        AuthPrincipal principal = authService.parse(bearer(bearerToken));
        String secret = Totp.secret();
        repository.saveMfa(principal.username(),
                new AdvancedAuthRepository.MfaState(Sm4Util.encrypt(secret, sm4Key), false, -1));
        return secret;
    }

    public void confirmMfaBinding(String bearerToken, String code) {
        AuthPrincipal principal = authService.parse(bearer(bearerToken));
        AdvancedAuthRepository.MfaState binding = requiredMfa(principal.username());
        long counter = Totp.verify(Sm4Util.decrypt(binding.secretCipher(), sm4Key), code,
                clock.instant().getEpochSecond());
        if (counter < 0) throw new BusinessException("AUTH-401", "invalid MFA code");
        repository.saveMfa(principal.username(),
                new AdvancedAuthRepository.MfaState(binding.secretCipher(), true, counter));
    }

    public void unbindMfa(String bearerToken, String code) {
        AuthPrincipal principal = authService.parse(bearer(bearerToken));
        AdvancedAuthRepository.MfaState binding = requiredMfa(principal.username());
        long counter = Totp.verify(Sm4Util.decrypt(binding.secretCipher(), sm4Key), code,
                clock.instant().getEpochSecond());
        if (counter < 0) {
            throw new BusinessException("AUTH-401", "invalid MFA code");
        }
        if (!repository.advanceCounter(principal.username(), counter)) {
            throw new BusinessException("AUTH-401", "MFA replay rejected");
        }
        repository.clearMfa(principal.username());
    }

    String encryptedMfaSecret(String username) {
        return requiredMfa(username).secretCipher();
    }

    public String bindCertificate(String bearerToken, String pem) {
        AuthPrincipal principal = authService.parse(bearer(bearerToken));
        List<X509Certificate> chain = parseChain(pem);
        X509Certificate certificate = chain.get(0);
        certificateValidator.validate(chain);
        String cn = commonName(certificate);
        if (!principal.username().equals(cn)) {
            throw new BusinessException("AUTH-401", "certificate CN mismatch");
        }
        String fingerprint = fingerprint(certificate);
        repository.bindCertificate(new AdvancedAuthRepository.CertificateState(
                fingerprint, principal.username(), cn, certificate.getSerialNumber().toString(16),
                Sm4Util.encrypt(pem, sm4Key), "ACTIVE", certificate.getNotBefore().toInstant(),
                certificate.getNotAfter().toInstant()));
        return fingerprint;
    }

    public void revokeCertificate(String bearerToken, String fingerprint) {
        AuthPrincipal principal = authService.parse(bearer(bearerToken));
        AdvancedAuthRepository.CertificateState binding = requiredCertificate(fingerprint);
        if (!binding.username().equals(principal.username())) {
            throw new BusinessException("AUTH-403", "certificate owner mismatch");
        }
        repository.revokeCertificate(fingerprint);
    }

    public String rotateCertificate(String bearerToken, String oldFingerprint, String newPem) {
        revokeCertificate(bearerToken, oldFingerprint);
        return bindCertificate(bearerToken, newPem);
    }

    public CertificateChallengeResponse certificateChallenge(String fingerprint) {
        AdvancedAuthRepository.CertificateState binding = requiredCertificate(fingerprint);
        if (!"ACTIVE".equals(binding.status())) throw new BusinessException("AUTH-401", "certificate revoked");
        String id = UUID.randomUUID().toString();
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(
                UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        certificateChallenges.put(id, new CertificateChallenge(fingerprint, value,
                clock.instant().getEpochSecond() + 120));
        return new CertificateChallengeResponse(id, value);
    }

    public String certificateLogin(String pem, String challengeId, String signatureBase64) {
        if (challengeId == null || signatureBase64 == null) {
            throw new BusinessException("AUTH-401", "certificate proof required");
        }
        List<X509Certificate> chain = parseChain(pem);
        X509Certificate certificate = chain.get(0);
        certificateValidator.validate(chain);
        String fingerprint = fingerprint(certificate);
        AdvancedAuthRepository.CertificateState binding = requiredCertificate(fingerprint);
        if (!"ACTIVE".equals(binding.status())) throw new BusinessException("AUTH-401", "certificate revoked");
        CertificateChallenge challenge = certificateChallenges.get(challengeId);
        if (challenge == null || challenge.expiresAt() < clock.instant().getEpochSecond()
                || !fingerprint.equals(challenge.fingerprint())) {
            throw new BusinessException("AUTH-401", "certificate challenge invalid");
        }
        verifyProof(certificate, challenge.value(), signatureBase64);
        certificateChallenges.remove(challengeId, challenge);
        return requireMfa(binding.username());
    }

    public SsoRedirect ssoRedirect() {
        String state = UUID.randomUUID().toString();
        ssoStates.put(state, state);
        return new SsoRedirect(ssoAdapter.redirect(state).toString(), state);
    }

    public String ssoCallback(String code, String state) {
        if (ssoStates.remove(state) == null) throw new BusinessException("AUTH-401", "invalid SSO state");
        SsoAdapter.SsoIdentity identity = ssoAdapter.callback(code, state);
        return requireMfa(identity.username());
    }

    private String requireMfa(String username) {
        authService.permissionsFor(username);
        if (!mfaEnabled(username)) throw new BusinessException("AUTH-403", "MFA enrollment required");
        return "MFA_REQUIRED:" + startChallenge(username);
    }

    private AdvancedAuthRepository.MfaState requiredMfa(String username) {
        AdvancedAuthRepository.MfaState binding = repository.mfa(username);
        if (binding == null) throw new BusinessException("AUTH-401", "MFA not bound");
        return binding;
    }

    private AdvancedAuthRepository.CertificateState requiredCertificate(String fingerprint) {
        AdvancedAuthRepository.CertificateState binding = repository.certificate(fingerprint);
        if (binding == null) throw new BusinessException("AUTH-401", "certificate not bound");
        return binding;
    }

    private ChallengePayload verifyChallenge(String value) {
        try {
            String[] parts = value.split("\\.");
            if (parts.length != 4) throw new IllegalArgumentException();
            String payload = String.join(".", parts[0], parts[1], parts[2]);
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                    parts[3].getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException();
            long expires = Long.parseLong(parts[1]);
            if (expires < clock.instant().getEpochSecond()) {
                throw new BusinessException("AUTH-401", "MFA challenge expired");
            }
            return new ChallengePayload(decode(parts[0]));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("AUTH-401", "MFA challenge invalid");
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sm4Key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("challenge signing failed", ex);
        }
    }

    private void verifyProof(X509Certificate certificate, String challenge, String signatureBase64) {
        try {
            String algorithm = "EC".equalsIgnoreCase(certificate.getPublicKey().getAlgorithm())
                    ? "SHA256withECDSA" : "SHA256withRSA";
            Signature verifier = Signature.getInstance(algorithm);
            verifier.initVerify(certificate.getPublicKey());
            verifier.update(challenge.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(signatureBase64))) {
                throw new BusinessException("AUTH-401", "certificate proof invalid");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("AUTH-401", "certificate proof invalid");
        }
    }

    private List<X509Certificate> parseChain(String pem) {
        try {
            Collection<? extends java.security.cert.Certificate> parsed = CertificateFactory.getInstance("X.509")
                    .generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
            List<X509Certificate> chain = new ArrayList<>(parsed.size());
            for (java.security.cert.Certificate certificate : parsed) {
                chain.add((X509Certificate) certificate);
            }
            if (chain.isEmpty()) throw new IllegalArgumentException();
            return List.copyOf(chain);
        } catch (Exception ex) {
            throw new BusinessException("AUTH-401", "invalid certificate");
        }
    }

    private String commonName(X509Certificate certificate) {
        try {
            return new LdapName(certificate.getSubjectX500Principal().getName()).getRdns().stream()
                    .filter(rdn -> "CN".equalsIgnoreCase(rdn.getType()))
                    .map(rdn -> String.valueOf(rdn.getValue())).findFirst()
                    .orElseThrow(() -> new BusinessException("AUTH-401", "certificate CN missing"));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("AUTH-401", "certificate CN invalid");
        }
    }

    private String fingerprint(X509Certificate certificate) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        } catch (Exception ex) {
            throw new BusinessException("AUTH-401", "certificate fingerprint failed");
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private String bearer(String value) {
        return value != null && value.startsWith("Bearer ") ? value.substring(7) : value;
    }

    record ChallengePayload(String username) {}
    record CertificateChallenge(String fingerprint, String value, long expiresAt) {}
    public record CertificateChallengeResponse(String challengeId, String challenge) {}
    public record SsoRedirect(String url, String state) {}
}
