package ru.hemulen.crypto.impl.jcp;

import ru.hemulen.crypto.KeyStoreWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class KeyStoreWrapperJCP implements KeyStoreWrapper {
    private KeyStore ks;

    public KeyStoreWrapperJCP(String keyStoreName) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        if (keyStoreName == null) {
            ks = KeyStore.getInstance("HDImageStore");
        } else {
            ks = KeyStore.getInstance(keyStoreName);
        }
        this.ks.load(null);
    }

    public PrivateKey getPrivateKey(String alias, char[] password) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        return (PrivateKey)this.ks.getKey(alias, password);
    }

    public X509Certificate getX509Certificate(String alias) throws CertificateException, KeyStoreException {
        X509Certificate certificate = (X509Certificate)this.ks.getCertificate(alias);
        return certificate == null ? null : (X509Certificate) CertificateFactory.getInstance("X509").generateCertificate(new ByteArrayInputStream(certificate.getEncoded()));
    }

    public KeyStore getKeyStore() {
        return this.ks;
    }
}
