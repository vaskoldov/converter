package ru.hemulen.converter.signer;

import org.w3c.dom.Element;
import ru.hemulen.crypto.DigitalSignatureFactory;
import ru.hemulen.crypto.DigitalSignatureProcessor;
import ru.hemulen.crypto.KeyStoreWrapper;
import ru.hemulen.crypto.exceptions.SignatureProcessingException;

import java.io.*;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class EGRNSigner {
    private static volatile boolean init = false; // Признак инициализации класса Signer
    private DigitalSignatureProcessor digitalSignatureProcessor;    // Процессор подписей из библиотеки ru.hemulen.crypto
    private PrivateKey privateKey;
    private X509Certificate certificate;

    public EGRNSigner(String sign, String password) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        String keystoreName = null;
        String keyAlias;
        String signAlias = sign;
        if (signAlias.indexOf('\\') != -1) {
            // Если в config.ini алиас ключа подписи для ЕГРН указан через слеш, значит в нем есть имя хранилища ключей
            keystoreName = signAlias.substring(0, signAlias.indexOf('\\'));
            keyAlias = signAlias.substring(signAlias.indexOf('\\') + 1);
        } else {
            keystoreName = "HDImageStore";
            keyAlias = signAlias;
        }

        digitalSignatureProcessor = DigitalSignatureFactory.getEGRNDigitalSignatureProcessor();
        KeyStoreWrapper keyStoreWrapper = DigitalSignatureFactory.getEGRNKeyStoreWrapper();
        privateKey = keyStoreWrapper.getPrivateKey(keyAlias, password.toCharArray());
        certificate = keyStoreWrapper.getX509Certificate(keyAlias);
    }

    public Element signXMLDSigDetached(Element document2Sign, String signatureId) throws SignatureProcessingException {
        return digitalSignatureProcessor.signXMLDSigDetached(document2Sign, signatureId, privateKey, certificate);
    }

    public byte[] signPKCS7Detached(InputStream argContent2Sign, PrivateKey argPrivateKey, X509Certificate argCertificate) throws SignatureProcessingException {
        return digitalSignatureProcessor.signPKCS7Detached(argContent2Sign, argPrivateKey, argCertificate);
    }

    public File signPKCS7Detached(File file2sign) throws SignatureProcessingException, IOException {
        InputStream inputStream = new FileInputStream(file2sign);
        byte[] result = digitalSignatureProcessor.signPKCS7Detached(inputStream, privateKey, certificate);
        // Файл с подписью создается в том же каталоге и с тем же именем, но с расширением sig
        String signFileName = file2sign.toString() + ".sig";
        File signFile = new File(signFileName);
        FileOutputStream signOutputStream = new FileOutputStream(signFile);
        signOutputStream.write(Base64.getEncoder().encode(result));
        signOutputStream.flush();
        signOutputStream.close();
        return signFile;
    }
}
