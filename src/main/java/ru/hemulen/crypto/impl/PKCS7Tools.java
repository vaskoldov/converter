package ru.hemulen.crypto.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.util.Selector;
import ru.hemulen.crypto.DigitalSignatureFactory;
import ru.hemulen.crypto.KeyStoreWrapper;
import ru.hemulen.crypto.exceptions.SignatureProcessingException;
import ru.hemulen.crypto.exceptions.SignatureValidationException;
import sun.security.pkcs.ContentInfo;
import sun.security.pkcs.PKCS7;
import sun.security.pkcs.PKCS9Attribute;
import sun.security.pkcs.PKCS9Attributes;
import sun.security.pkcs.SignerInfo;
import sun.security.util.DerValue;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X500Name;

public class PKCS7Tools {

    public static byte[] signPKCS7SunSecurity(byte[] digestedContent, PrivateKey privateKey, X509Certificate certificate) throws SignatureProcessingException {
        try {
            // Данные для подписи
            PKCS9Attribute[] authenticatedAttributeList = new PKCS9Attribute[]{
                    new PKCS9Attribute(PKCS9Attribute.CONTENT_TYPE_OID, ContentInfo.DATA_OID),
                    new PKCS9Attribute(PKCS9Attribute.SIGNING_TIME_OID, new Date()),
                    new PKCS9Attribute(PKCS9Attribute.MESSAGE_DIGEST_OID, digestedContent)
            };
            PKCS9Attributes authenticatedAttributes = new PKCS9Attributes(authenticatedAttributeList);
            String digestAlgorithmOID = X509Util.getDigestAlgorithmOID(certificate);
            String publicKeyAlgorithmOID = X509Util.getPublicKeyAlgorithm(certificate);
            String signatureAlgorithmOID = X509Util.getSignatureAlgorithmOID(certificate);
            // Подписываем
            byte[] signedAttributes = sign(privateKey, authenticatedAttributes.getDerEncoding(), signatureAlgorithmOID);
            // SignerInfo
            BigInteger serial = certificate.getSerialNumber();
            SignerInfo si = new SignerInfo(new X500Name(certificate.getIssuerDN().getName()), serial, AlgorithmId.get(digestAlgorithmOID), authenticatedAttributes, new AlgorithmId(new ObjectIdentifier(publicKeyAlgorithmOID)), signedAttributes, null);
            SignerInfo[] signerInfos = new SignerInfo[]{si};
            // Сертификат
            X509Certificate[] certificates = new X509Certificate[]{certificate};
            // Алгоритм подписи
            AlgorithmId[] digestAlgorithmIds = new AlgorithmId[]{AlgorithmId.get(digestAlgorithmOID)};
            ContentInfo contentInfo = new ContentInfo(ContentInfo.DATA_OID, null);
            // Собираем все вместе и пишем в стрим
            PKCS7 p7 = new PKCS7(digestAlgorithmIds, contentInfo, certificates, signerInfos);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            p7.encodeSignedData(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new SignatureProcessingException(e);
        }
    }

    private static byte[] sign(PrivateKey key, byte[] data, String signatureAlgorithmOID) throws SignatureProcessingException {
        try {
            KeyStoreWrapper keyStoreWrapper = DigitalSignatureFactory.getEGRNKeyStoreWrapper();
            Provider provider = keyStoreWrapper.getKeyStore().getProvider();
            Signature signer = Signature.getInstance(signatureAlgorithmOID, provider);
            signer.initSign(key);
            signer.update(data);
            return signer.sign();
        } catch (InvalidKeyException | SignatureException | NoSuchAlgorithmException ex) {
            throw new SignatureProcessingException(ex);
        }
    }

    public static X509Certificate verifyPKCS7BcProv(byte[] argDigestedData, byte[] signedDataByteArray) throws SignatureValidationException {
        try {
            // Загоняем подписанные данные в объект
            CMSSignedData signedData = new CMSSignedData(signedDataByteArray);
            // Создаем фабрику сертификатов
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            // Вытягиваем все сертификаты
            List<X509Certificate> certificates = new ArrayList<>();
            for (X509CertificateHolder holder : (Collection<X509CertificateHolder>) signedData.getCertificates().getMatches(null)) {
                certificates.add((X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(holder.getEncoded())));
            }
            // Сертификат, который будет возвращен из метода
            X509Certificate certificateToReturn = null;
            // Листаем подписантов
            SignerInformationStore signers = signedData.getSignerInfos();
            for (SignerInformation signer : (Collection<SignerInformation>) signers.getSigners()) {

                // Проверяем что подписанные данные есть.
                if (signer.getSignedAttributes() == null) {
                    throw new SignatureValidationException("Подпись в формате PKCS#7 не содержет подписанных данных!");
                }
                // Извлекаем дайджест, использованный при подписи, сравниваем с актуальным.
                org.bouncycastle.asn1.cms.Attribute attribute = signer.getSignedAttributes().get(new ASN1ObjectIdentifier("1.2.840.113549.1.9.4"));
                DEROctetString oct = (DEROctetString) attribute.getAttributeValues()[0];
                byte[] dig = oct.getOctets();
                if (!java.util.Arrays.equals(argDigestedData, dig)) {
                    throw new SignatureValidationException("Дайджест не прошел проверку!");
                }

                // Листаем сертификаты.
                byte[] signatureAsByteArray = signer.getSignature();
                for (X509Certificate providedCertificate : certificates) {
                    // Каждый раз обновляем поток isCheckData.
                    InputStream isCheckData = new ByteArrayInputStream(signer.getEncodedSignedAttributes());
                    boolean signatureIsVerified = checkOnCert(isCheckData, providedCertificate, signatureAsByteArray);
                    // TODO Loskutov Интересно почиму возвращается только первый удачно проверенный сертификат?
                    if (signatureIsVerified && certificateToReturn == null) {
                        certificateToReturn = providedCertificate;
                    }
                }
            }

            if (certificateToReturn != null) {
                return certificateToReturn;
            } else {
                throw new SignatureValidationException("Подпись не прошла проверку по сертификату.");
            }
        } catch (SignatureValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new SignatureValidationException(e);
        }
    }

    public static X509Certificate getCertificateFromSignature(byte[] signatureData) throws SignatureValidationException {
        try {
            CMSSignedData cmsSignedData = new CMSSignedData(signatureData);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            List<X509Certificate> certificates = new ArrayList();
            Iterator var4 = cmsSignedData.getCertificates().getMatches(null).iterator();

            while (var4.hasNext()) {
                X509CertificateHolder holder = (X509CertificateHolder) var4.next();
                certificates.add((X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(holder.getEncoded())));
            }

            SignerInformationStore signerInformationStore = cmsSignedData.getSignerInfos();
            Iterator var30 = signerInformationStore.getSigners().iterator();

            label162:
            while (var30.hasNext()) {
                SignerInformation signer = (SignerInformation) var30.next();
                if (signer.getSignedAttributes() == null) {
                    throw new SignatureValidationException("Подпись в формате PKCS#7 не содержет подписанных данных!");
                }

                byte[] signatureAsByteArray = signer.getSignature();
                Iterator var8 = certificates.iterator();

                while (true) {
                    X509Certificate certificate;
                    do {
                        if (!var8.hasNext()) {
                            continue label162;
                        }

                        certificate = (X509Certificate) var8.next();
                    } while (certificate.getKeyUsage() != null && !certificate.getKeyUsage()[0]);

                    InputStream isCheckData = new ByteArrayInputStream(signer.getEncodedSignedAttributes());
                    Throwable var11 = null;

                    try {
                        boolean signatureIsVerified = checkOnCert(isCheckData, certificate, signatureAsByteArray);
                        if (signatureIsVerified) {
                            return certificate;
                        }
                    } catch (Throwable var25) {
                        throw var25;
                    } finally {
                        if (isCheckData != null) {
                            if (var11 != null) {
                                try {
                                    isCheckData.close();
                                } catch (Throwable var24) {
                                    var11.addSuppressed(var24);
                                }
                            } else {
                                isCheckData.close();
                            }
                        }

                    }
                }
            }

            return null;
        } catch (SignatureValidationException var27) {
            throw var27;
        } catch (Exception var28) {
            throw new SignatureValidationException(var28);
        }
    }

    public static boolean checkOnCert(InputStream isCheckData, X509Certificate certificate, byte[] argSignatureAsByteArray) throws SignatureProcessingException {
        boolean var25;
        try {
            String signatureAlgorithmOID = X509Util.getSignatureAlgorithmOID(certificate);

            Signature signature;
            try {
                KeyStoreWrapper keyStoreWrapper = DigitalSignatureFactory.getFNSKeyStoreWrapper();
                Provider provider = keyStoreWrapper.getKeyStore().getProvider();
                signature = Signature.getInstance(signatureAlgorithmOID, provider);
                signature.initVerify(certificate);
            } catch (InvalidKeyException e) {
                throw new SignatureProcessingException("Открытый ключ поврежден.", e);
            } catch (NoSuchAlgorithmException e) {
                throw new SignatureProcessingException("Не поддерживается алгоритм подписи " + X509Util.getGostName(signatureAlgorithmOID) + ". Убедитесь, что установлен нужный криптопровайдер.", e);
            }

            try {
                byte[] localBuffer = new byte[4096];

                int readBytesCount;
                while ((readBytesCount = isCheckData.read(localBuffer)) > 0) {
                    signature.update(localBuffer, 0, readBytesCount);
                }
            } catch (SignatureException e) {
                throw new SignatureProcessingException("Сбой при генерации message digest.", e);
            } catch (IOException e) {
                throw new SignatureProcessingException("Невозможно прочитать подписываемые данные из потока.", e);
            }

            try {
                var25 = signature.verify(argSignatureAsByteArray);
            } catch (SignatureException e) {
                throw new SignatureProcessingException("Сбой на фазе верификации ЭЦП.", e);
            }
        } finally {
            try {
                isCheckData.close();
            } catch (IOException e) {
            }

        }

        return var25;
    }

}
