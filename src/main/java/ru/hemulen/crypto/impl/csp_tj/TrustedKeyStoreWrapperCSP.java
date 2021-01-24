package ru.hemulen.crypto.impl.csp_tj;

import ru.hemulen.crypto.KeyStoreWrapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class TrustedKeyStoreWrapperCSP implements KeyStoreWrapper {
    public static final String CURRENT_USER_KS_TYPE = "CurrentUser";
    public static final String LOCAL_COMPUTER_KS_TYPE = "LocalComputer";
    public static final String CURRENT_USER_CONTAINERS_KS_TYPE = "CurrentUser/Containers";
    private final KeyStore ks = KeyStore.getInstance("CryptoProCSPKeyStore");

    public TrustedKeyStoreWrapperCSP() throws Exception {
        InputStream stream = new ByteArrayInputStream("CurrentUser/Containers".getBytes("UTF-8"));
        this.ks.load(stream, (char[])null);
    }

    public PrivateKey getPrivateKey(String alias, char[] password) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        alias = this.resolveAlias(alias);
        return (PrivateKey)this.ks.getKey(alias, password);
    }

    public X509Certificate getX509Certificate(String alias) throws CertificateException, KeyStoreException {
        alias = this.resolveAlias(alias);
        X509Certificate certificate = (X509Certificate)this.ks.getCertificate(alias);
        return certificate == null ? null : (X509Certificate) CertificateFactory.getInstance("X509").generateCertificate(new ByteArrayInputStream(certificate.getEncoded()));
    }

    public KeyStore getKeyStore() {
        return this.ks;
    }

    private String resolveAlias(String alias) {
        return alias;
    }

}
