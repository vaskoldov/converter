package ru.hemulen.converter.signer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import ru.hemulen.crypto.DigitalSignatureFactory;
import ru.hemulen.crypto.DigitalSignatureProcessor;
import ru.hemulen.crypto.KeyStoreWrapper;
import ru.hemulen.crypto.exceptions.SignatureProcessingException;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;


public class FNSSigner {
    private static Logger LOG = LoggerFactory.getLogger(FNSSigner.class.getName());
    private static volatile boolean init = false; // Признак инициализации класса Signer
    private DigitalSignatureProcessor digitalSignatureProcessor;    // Процессор подписей из библиотеки ru.voskhod.crypto
    private PrivateKey privateKey;
    private X509Certificate certificate;

    public FNSSigner(String sign, String password) throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException {
        String keystoreName = null;
        String keyAlias = null;
        String signAlias = sign;
        if (signAlias.indexOf('\\') != -1) {
            // Если в config.ini алиас ключа подписи для ФНС указан через слеш, значит в нем есть имя хранилища ключей
            keystoreName = signAlias.substring(0, signAlias.indexOf('\\'));
            keyAlias = signAlias.substring(signAlias.indexOf('\\') + 1);
            if (!keystoreName.startsWith("RutokenStore")) {
                // Если имя хранилища не RutokenStore, то подставляем имя HDImageStore
                keystoreName = "HDImageStore";
                LOG.info(String.format("В config.ini указано неизвестное хранилище %s. Используется хранилище HDImageStore."), keystoreName);
            }
        } else {
            // Иначе параметр в config.ini является "чистым" алиасом ключа
            keyAlias = signAlias;
        }
        digitalSignatureProcessor = DigitalSignatureFactory.getFNSDigitalSignatureProcessor();
        KeyStoreWrapper keyStoreWrapper = DigitalSignatureFactory.getFNSKeyStoreWrapper();
        privateKey = keyStoreWrapper.getPrivateKey(keyAlias, password.toCharArray());
        certificate = keyStoreWrapper.getX509Certificate(keyAlias);
    }

    public Element signXMLDSigDetached(Element document2Sign, String signatureId) throws SignatureProcessingException {
        return digitalSignatureProcessor.signXMLDSigDetached(document2Sign, signatureId, privateKey, certificate);
    }

}
