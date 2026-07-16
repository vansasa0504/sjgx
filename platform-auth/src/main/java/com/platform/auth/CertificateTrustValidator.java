package com.platform.auth;

import com.platform.common.exception.BusinessException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/** PKIX validation against an explicitly configured trust store. */
public class CertificateTrustValidator {
    private final KeyStore trustStore;
    private final List<X509CRL> crls;
    private final Clock clock;

    public CertificateTrustValidator(KeyStore trustStore, X509CRL crl, Clock clock) {
        this(trustStore, crl == null ? List.of() : List.of(crl), clock);
    }

    public CertificateTrustValidator(KeyStore trustStore, Collection<X509CRL> crls, Clock clock) {
        this.trustStore = trustStore;
        this.crls = List.copyOf(crls);
        this.clock = clock;
    }

    public static CertificateTrustValidator load(String trustStorePath, String password, String crlPath, Clock clock) {
        if (trustStorePath == null || trustStorePath.isBlank()) {
            throw new IllegalStateException("security.cert.truststore must be configured");
        }
        try {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream in = Files.newInputStream(Path.of(trustStorePath))) {
                store.load(in, password == null ? new char[0] : password.toCharArray());
            }
            List<X509CRL> loadedCrls = List.of();
            if (crlPath != null && !crlPath.isBlank()) {
                try (InputStream in = Files.newInputStream(Path.of(crlPath))) {
                    Collection<? extends java.security.cert.CRL> parsed =
                            CertificateFactory.getInstance("X.509").generateCRLs(in);
                    loadedCrls = parsed.stream().map(value -> (X509CRL) value).toList();
                }
            }
            return new CertificateTrustValidator(store, loadedCrls, clock);
        } catch (Exception ex) {
            throw new IllegalStateException("certificate trust configuration invalid", ex);
        }
    }

    public void validate(X509Certificate certificate) {
        validate(List.of(certificate));
    }

    public void validate(List<X509Certificate> certificates) {
        try {
            if (certificates.isEmpty()) throw new IllegalArgumentException("certificate chain is empty");
            if (crls.isEmpty()) {
                throw new BusinessException("AUTH-401", "certificate revocation status unavailable");
            }

            List<X509Certificate> pathCertificates = new ArrayList<>(certificates);
            while (pathCertificates.size() > 1 && isTrustAnchor(pathCertificates.get(pathCertificates.size() - 1))) {
                pathCertificates.remove(pathCertificates.size() - 1);
            }
            for (X509Certificate certificate : pathCertificates) {
                certificate.checkValidity(Date.from(clock.instant()));
                for (X509CRL crl : crls) {
                    if (certificate.getIssuerX500Principal().equals(crl.getIssuerX500Principal())
                            && crl.isRevoked(certificate)) {
                        throw new BusinessException("AUTH-401", "certificate revoked by CA");
                    }
                }
            }

            CertPath path = CertificateFactory.getInstance("X.509").generateCertPath(pathCertificates);
            PKIXParameters params = new PKIXParameters(trustStore);
            params.setDate(Date.from(clock.instant()));
            params.addCertStore(CertStore.getInstance(
                    "Collection", new CollectionCertStoreParameters(crls)));
            params.setRevocationEnabled(true);
            CertPathValidator.getInstance("PKIX").validate(path, params);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("AUTH-401", "certificate chain validation failed");
        }
    }

    private boolean isTrustAnchor(X509Certificate certificate) throws Exception {
        var aliases = trustStore.aliases();
        while (aliases.hasMoreElements()) {
            java.security.cert.Certificate trusted = trustStore.getCertificate(aliases.nextElement());
            if (certificate.equals(trusted)) return true;
        }
        return false;
    }
}
