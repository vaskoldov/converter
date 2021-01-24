package ru.hemulen.crypto;

import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public interface KeyStoreWrapper {
    PrivateKey getPrivateKey(String alias, char[] password) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException;

    X509Certificate getX509Certificate(String alias) throws CertificateException, KeyStoreException;

    KeyStore getKeyStore();
}
