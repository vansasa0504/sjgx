package com.platform.auth;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

final class CertificateFixtures {
    private CertificateFixtures() {}

    static Fixture create(Instant now) throws Exception {
        KeyPair caKeys = keys();
        X500Name caName = new X500Name("CN=Test Root CA");
        X509Certificate ca = issue(caName, caName, caKeys.getPublic(), caKeys.getPrivate(),
                now.minusSeconds(3600), now.plusSeconds(86400), true);

        KeyPair leafKeys = keys();
        X509Certificate leaf = issue(caName, new X500Name("CN=admin"), leafKeys.getPublic(),
                caKeys.getPrivate(), now.minusSeconds(60), now.plusSeconds(3600), false);

        KeyPair intermediateKeys = keys();
        X500Name intermediateName = new X500Name("CN=Test Intermediate CA");
        X509Certificate intermediate = issue(caName, intermediateName, intermediateKeys.getPublic(),
                caKeys.getPrivate(), now.minusSeconds(3600), now.plusSeconds(43200), true);
        KeyPair multiLeafKeys = keys();
        X509Certificate multiLeaf = issue(intermediateName, new X500Name("CN=admin"),
                multiLeafKeys.getPublic(), intermediateKeys.getPrivate(),
                now.minusSeconds(60), now.plusSeconds(3600), false);

        KeyPair selfKeys = keys();
        X500Name selfName = new X500Name("CN=admin");
        X509Certificate self = issue(selfName, selfName, selfKeys.getPublic(), selfKeys.getPrivate(),
                now.minusSeconds(60), now.plusSeconds(3600), false);
        X509Certificate expired = issue(caName, new X500Name("CN=admin"), keys().getPublic(),
                caKeys.getPrivate(), now.minusSeconds(7200), now.minusSeconds(3600), false);

        X509CRL revokedCrl = crl(caName, caKeys.getPrivate(), leaf.getSerialNumber(), now);
        X509CRL cleanRootCrl = crl(caName, caKeys.getPrivate(), null, now);
        X509CRL cleanIntermediateCrl = crl(intermediateName, intermediateKeys.getPrivate(), null, now);
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("test-root", ca);
        return new Fixture(ca, leaf, leafKeys.getPrivate(), self, expired, revokedCrl,
                cleanRootCrl, intermediate, multiLeaf, multiLeafKeys.getPrivate(),
                cleanIntermediateCrl, trustStore);
    }

    private static X509Certificate issue(X500Name issuer, X500Name subject, java.security.PublicKey publicKey,
            PrivateKey signerKey, Instant notBefore, Instant notAfter, boolean ca) throws Exception {
        BigInteger serial = new BigInteger(96, new SecureRandom()).abs();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial, Date.from(notBefore), Date.from(notAfter), subject, publicKey);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(ca ? KeyUsage.keyCertSign | KeyUsage.cRLSign : KeyUsage.digitalSignature));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(signerKey);
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static X509CRL crl(X500Name issuer, PrivateKey signerKey, BigInteger serial, Instant now)
            throws Exception {
        X509v2CRLBuilder builder = new X509v2CRLBuilder(issuer, Date.from(now.minusSeconds(60)));
        builder.setNextUpdate(Date.from(now.plusSeconds(3600)));
        if (serial != null) {
            builder.addCRLEntry(serial, Date.from(now.minusSeconds(30)), 0);
        }
        X509CRLHolder holder = builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(signerKey));
        return new JcaX509CRLConverter().getCRL(holder);
    }

    private static KeyPair keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    static String pem(X509Certificate certificate) throws Exception {
        return "-----BEGIN CERTIFICATE-----\n"
                + java.util.Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(certificate.getEncoded())
                + "\n-----END CERTIFICATE-----";
    }

    static String chainPem(X509Certificate... certificates) throws Exception {
        StringBuilder chain = new StringBuilder();
        for (X509Certificate certificate : certificates) {
            if (!chain.isEmpty()) chain.append('\n');
            chain.append(pem(certificate));
        }
        return chain.toString();
    }

    record Fixture(X509Certificate ca, X509Certificate leaf, PrivateKey leafPrivateKey,
            X509Certificate selfSigned, X509Certificate expired, X509CRL crl,
            X509CRL cleanCrl, X509Certificate intermediate, X509Certificate multiLeaf,
            PrivateKey multiLeafPrivateKey, X509CRL intermediateCrl, KeyStore trustStore) {}
}
