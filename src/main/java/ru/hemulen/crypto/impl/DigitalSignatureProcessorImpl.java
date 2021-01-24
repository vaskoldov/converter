package ru.hemulen.crypto.impl;

import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import ru.hemulen.crypto.exceptions.SignatureProcessingException;
import ru.hemulen.crypto.exceptions.SignatureValidationException;
import ru.hemulen.crypto.DigitalSignatureProcessor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class DigitalSignatureProcessorImpl extends AbstractDigitalSignatureProcessor {
    protected static final String XMLDSIG_DETACHED_TRANSFORM_METHOD = "http://www.w3.org/2001/10/xml-exc-c14n#";
    protected static final String XMLDSIG_ENVELOPED_TRANSFORM_METHOD = "http://www.w3.org/2000/09/xmldsig#enveloped-signature";
    protected static final String WSSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    protected static final String EDS_ERROR_SIGNATURE_INVALID = "Ошибка проверки ЭП: Нарушена целостность ЭП";

    // ================================ Подпись XML

    protected Element signXMLDSig(Document argDocument, Element element2Sign, PrivateKey argPrivateKey, X509Certificate argCertificate, String argSignatureId, boolean enveloped) throws SignatureProcessingException {
        try {
            String digestAlgorithmOID = X509Util.getDigestAlgorithmOID(argCertificate);
            String signatureAlgorithmOID = X509Util.getSignatureAlgorithmOID(argCertificate);
            String digestAlgorithmMethod = X509Util.getDigestAlgorithmMethod(digestAlgorithmOID);
            String signatureAlgorithmMethod = X509Util.getSignatureAlgorithmMethod(signatureAlgorithmOID);
            Element _element2Sign = element2Sign != null ? element2Sign : argDocument.getDocumentElement();
            String referenceURI = _element2Sign.getAttribute("Id");
            if (referenceURI == null || "".equals(referenceURI.trim())) {
                referenceURI = _element2Sign.getAttributeNS(WSSU_NS, "Id");
            }

            if (referenceURI == null || "".equals(referenceURI.trim())) {
                referenceURI = "";
            }

            /* Добавление узла подписи <ds:Signature> в загруженный XML-документ */

            XMLSignature xmlSignature = new XMLSignature(argDocument, "", signatureAlgorithmMethod, XMLDSIG_DETACHED_TRANSFORM_METHOD);
            if (argSignatureId != null) {
                xmlSignature.setId(argSignatureId);
            }

            Transforms transforms = new Transforms(argDocument);
            if (enveloped) {
                transforms.addTransform(XMLDSIG_ENVELOPED_TRANSFORM_METHOD);
            }

            transforms.addTransform(XMLDSIG_DETACHED_TRANSFORM_METHOD);
            transforms.addTransform(SmevTransformSpi.ALGORITHM_URN);
            String refURI = referenceURI;
            if (!referenceURI.isEmpty() && !referenceURI.startsWith("#")) {
                refURI = "#" + referenceURI;
            }

            xmlSignature.addDocument(refURI, transforms, digestAlgorithmMethod);
            xmlSignature.addKeyInfo(argCertificate);
            xmlSignature.sign(argPrivateKey);
            return xmlSignature.getElement();
        } catch (Exception e) {
            throw new SignatureProcessingException(e);
        }
    }

    protected X509Certificate getCertificate(Element signatureElement) throws SignatureValidationException {
        try {
            XMLSignature signature = new XMLSignature(signatureElement, "");
            KeyInfo keyInfoFromSignature = signature.getKeyInfo();
            X509Certificate certificate = keyInfoFromSignature.getX509Certificate();
            if (certificate != null) {
                boolean signatureIsValid = signature.checkSignatureValue(certificate);
                if (!signatureIsValid) {
                    throw new SignatureValidationException(EDS_ERROR_SIGNATURE_INVALID);
                }
            }

            return certificate;
        } catch (XMLSecurityException e) {
            throw new SignatureValidationException(e);
        }
    }

    @Override
    public void signXMLDSigEnveloped(Element argDocument2Sign, String argSignatureId, DigitalSignatureProcessor.SIG_POSITION var3, String var4, PrivateKey argPrivateKey, X509Certificate argCertificate) throws SignatureProcessingException {

    }

    // ================================ PKCS7

    public MessageDigest getDigest(String digestAlgorithmOID) throws SignatureProcessingException {
        try {
            return MessageDigest.getInstance(digestAlgorithmOID);
        } catch (NoSuchAlgorithmException e) {
            throw new SignatureProcessingException("Криптопровайдер не поддерживает алгоритм " + X509Util.getGostName(digestAlgorithmOID), e);
        }
    }

    public MessageDigest getDigest(X509Certificate certificate) throws SignatureProcessingException {
        return this.getDigest(X509Util.getDigestAlgorithmOID(certificate));
    }

    public byte[] signPKCS7Detached(byte[] digest, PrivateKey privateKey, X509Certificate certificate) throws SignatureProcessingException {
        return PKCS7Tools.signPKCS7SunSecurity(digest, privateKey, certificate);
    }

    public X509Certificate validatePKCS7Signature(byte[] digest, byte[] signature) throws SignatureProcessingException, SignatureValidationException {
        return PKCS7Tools.verifyPKCS7BcProv(digest, signature);
    }

}
