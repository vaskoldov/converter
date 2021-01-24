package ru.hemulen.crypto.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import sun.security.x509.X509Key;

public class X509Util {
    public static final String ISO_RU_REG_2_CRYPTO_PRO_OID = "1.2.643.2.2";
    public static final String ISO_RU_REG7_TC26_OID = "1.2.643.7.1";
    public static final String RSA_OID = "1.2.840.113549.1.1";
    public static final String SNILS_OID = "1.2.643.100.3";
    public static final String OGRN_OID = "1.2.643.100.1";
    public static final String COMMON_NAME = "2.5.4.3";
    public static final String SURNAME = "2.5.4.4";
    public static final String GIVEN_NAME = "2.5.4.42";
    public static final String DA_GOST_R3411_OID = "1.2.643.2.2.9";
    public static final String DA_GOST_R3411_12_256_OID = "1.2.643.7.1.1.2.2";
    public static final String DA_GOST_R3411_12_512_OID = "1.2.643.7.1.1.2.3";
    public static final String PK_GOST_R3410_OID = "1.2.643.2.2.20";
    public static final String PK_GOST_R3410EL_OID = "1.2.643.2.2.19";
    public static final String PK_GOST_R3410_2012_256_OID = "1.2.643.7.1.1.1.1";
    public static final String PK_GOST_R3410_2012_512_OID = "1.2.643.7.1.1.1.2";
    public static final String SA_GOST_R3411_R3410_OID = "1.2.643.2.2.4";
    public static final String SA_GOST_R3411_R3410EL_OID = "1.2.643.2.2.3";
    public static final String SA_GOST_R3411_2012_256_R3410_OID = "1.2.643.7.1.1.3.2";
    public static final String SA_GOST_R3411_2012_512_R3410_OID = "1.2.643.7.1.1.3.3";
    public static final String SA_GOST_R3411_R3410EL_METHOD_HTTP = "http://www.w3.org/2001/04/xmldsig-more#gostr34102001-gostr3411";
    public static final String SA_GOST_R3411_R3410EL_METHOD_URN = "urn:ietf:params:xml:ns:cpxmlsec:algorithms:gostr34102001-gostr3411";
    public static final String SA_GOST_R3411_2012_256_R3410_METHOD_URN = "urn:ietf:params:xml:ns:cpxmlsec:algorithms:gostr34102012-gostr34112012-256";
    public static final String SA_GOST_R3411_2012_512_R3410_METHOD_URN = "urn:ietf:params:xml:ns:cpxmlsec:algorithms:gostr34102012-gostr34112012-512";
    public static final String DA_GOST_R3411_METHOD_HTTP = "http://www.w3.org/2001/04/xmldsig-more#gostr3411";
    public static final String DA_GOST_R3411_METHOD_URN = "urn:ietf:params:xml:ns:cpxmlsec:algorithms:gostr3411";
    public static final String DA_GOST_R3411_12_256_METHOD_URN = "urn:ietf:params:xml:ns:cpxmlsec:algorithms:gostr34112012-256";
    public static final String DA_GOST_R3411_12_512_METHOD_URN = "urn:ietf:params:xml:ns:cpxmlsec:algorithms:gostr34112012-512";
    private static final Map<String, String> signatureAlgorithmOIDByPublicKeyAlgorithmMap = new HashMap();
    private static final Map<String, String> digestAlgorithmOIDBySignatureAlgorithmOIDMap;
    private static final Map<String, String> signatureAlgorithmMethodMap;
    private static final Map<String, String> digestAlgorithmMethodMap;
    private static final Map<String, String> publicKeyNameMap;
    private static final Map<String, String> signatureNameMap;
    private static final Map<String, String> digestNameMap;
    private static final Map<String, String> gostNameMap;

    public static String getPublicKeyAlgorithm(X509Certificate certificate) {
        Objects.requireNonNull(certificate, "certificate is null");

        try {
            X509Key publicKey = new X509Key();
            publicKey.decode(certificate.getPublicKey().getEncoded());
            return publicKey.getAlgorithm();
        } catch (InvalidKeyException var3) {
            throw new UnsupportedOperationException("invalid public key : " + var3.getMessage(), var3);
        }
    }

    public static String getSignatureAlgorithmOID(X509Certificate certificate) {
        return getSignatureAlgorithmOIDByPublicKeyAlgorithm(getPublicKeyAlgorithm(certificate));
    }

    public static String getSignatureAlgorithmOIDByPublicKeyAlgorithm(String publicKeyAlgorithm) {
        if (!signatureAlgorithmOIDByPublicKeyAlgorithmMap.containsKey(publicKeyAlgorithm)) {
            throw new UnsupportedOperationException("unknown public key algorithm: " + publicKeyAlgorithm);
        } else {
            return signatureAlgorithmOIDByPublicKeyAlgorithmMap.get(publicKeyAlgorithm);
        }
    }

    public static String getDigestAlgorithmOIDBySignatureAlgorithmOID(String signatureAlgorithmOID) {
        if (!digestAlgorithmOIDBySignatureAlgorithmOIDMap.containsKey(signatureAlgorithmOID)) {
            throw new UnsupportedOperationException("unknown signature algorithm OID: " + signatureAlgorithmOID);
        } else {
            return digestAlgorithmOIDBySignatureAlgorithmOIDMap.get(signatureAlgorithmOID);
        }
    }

    public static String getDigestAlgorithmOID(X509Certificate certificate) {
        return getDigestAlgorithmOIDBySignatureAlgorithmOID(getSignatureAlgorithmOID(certificate));
    }

    public static String getSignatureAlgorithmMethod(String signatureAlgorithmOID) {
        if (!signatureAlgorithmMethodMap.containsKey(signatureAlgorithmOID)) {
            throw new UnsupportedOperationException("unknown signature algorithm OID: " + signatureAlgorithmOID);
        } else {
            return signatureAlgorithmMethodMap.get(signatureAlgorithmOID);
        }
    }

    public static String getDigestAlgorithmMethod(String digestAlgorithmOID) {
        if (!digestAlgorithmMethodMap.containsKey(digestAlgorithmOID)) {
            throw new UnsupportedOperationException("unknown digest algorithm OID: " + digestAlgorithmOID);
        } else {
            return digestAlgorithmMethodMap.get(digestAlgorithmOID);
        }
    }

    public static String getPublicKeyName(String signatureAlgorithmOID) {
        if (!publicKeyNameMap.containsKey(signatureAlgorithmOID)) {
            throw new UnsupportedOperationException("unknown public key algorithm OID: " + signatureAlgorithmOID);
        } else {
            return publicKeyNameMap.get(signatureAlgorithmOID);
        }
    }

    public static String getSignatureName(String signatureAlgorithmOID) {
        if (!signatureNameMap.containsKey(signatureAlgorithmOID)) {
            throw new UnsupportedOperationException("unknown signature algorithm OID: " + signatureAlgorithmOID);
        } else {
            return signatureNameMap.get(signatureAlgorithmOID);
        }
    }

    public static String getDigestName(String digestAlgorithmOID) {
        if (!digestNameMap.containsKey(digestAlgorithmOID)) {
            throw new UnsupportedOperationException("unknown digest algorithm OID: " + digestAlgorithmOID);
        } else {
            return digestNameMap.get(digestAlgorithmOID);
        }
    }

    public static String getGostName(String algorithmOID) {
        return !gostNameMap.containsKey(algorithmOID) ? algorithmOID : (String)gostNameMap.get(algorithmOID);
    }

    private X509Util() {
    }

    public static Collection<? extends Certificate> readX509Certificates(InputStream inputStream) throws CertificateException {
        Objects.requireNonNull(inputStream, "inputStream is null");
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificates(inputStream);
    }

    public static Collection<? extends Certificate> readX509Certificates(byte[] data) throws CertificateException, IOException {
        Objects.requireNonNull(data, "data is null");
        InputStream inputStream = new ByteArrayInputStream(data);
        Throwable var2 = null;

        Collection var3;
        try {
            var3 = readX509Certificates(inputStream);
        } catch (Throwable var12) {
            var2 = var12;
            throw var12;
        } finally {
            if (inputStream != null) {
                if (var2 != null) {
                    try {
                        inputStream.close();
                    } catch (Throwable var11) {
                        var2.addSuppressed(var11);
                    }
                } else {
                    inputStream.close();
                }
            }

        }

        return var3;
    }

    public static X509Certificate readX509Certificate(InputStream inputStream) throws CertificateException {
        Objects.requireNonNull(inputStream, "inputStream is null");
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate)certificateFactory.generateCertificate(inputStream);
    }

    public static X509Certificate readX509CertificateFromBytes(byte[] data) throws CertificateException, IOException {
        Objects.requireNonNull(data, "data is null");
        InputStream inputStream = new ByteArrayInputStream(data);
        Throwable var2 = null;

        X509Certificate var3;
        try {
            var3 = readX509Certificate(inputStream);
        } catch (Throwable var12) {
            var2 = var12;
            throw var12;
        } finally {
            if (inputStream != null) {
                if (var2 != null) {
                    try {
                        inputStream.close();
                    } catch (Throwable var11) {
                        var2.addSuppressed(var11);
                    }
                } else {
                    inputStream.close();
                }
            }

        }

        return var3;
    }

    public static void writeX509Certificate(OutputStream outputStream, X509Certificate certificate) throws CertificateEncodingException, IOException {
        Objects.requireNonNull(outputStream, "outputStream is null");
        Objects.requireNonNull(certificate, "certificate is null");
        outputStream.write(certificate.getEncoded());
    }

    public static byte[] writeX509CertificateToBytes(X509Certificate certificate) throws CertificateEncodingException {
        Objects.requireNonNull(certificate, "certificate is null");
        return certificate.getEncoded();
    }

    public static boolean isGOST(String oid) {
        if (oid == null) {
            return false;
        } else if (oid.startsWith("1.2.643.2.2")) {
            return true;
        } else {
            return oid.startsWith("1.2.643.7.1");
        }
    }

    public static boolean isExpired(X509Certificate cert) {
        Objects.requireNonNull(cert, "X509Certificate is null");

        try {
            cert.checkValidity();
            return false;
        } catch (CertificateExpiredException var2) {
            return true;
        } catch (CertificateNotYetValidException var3) {
            return false;
        }
    }

    public static Map<String, String> getSubjectAttrs(X509Certificate certificate) throws CertificateEncodingException {
        RDN[] rdns = (new JcaX509CertificateHolder(certificate)).getSubject().getRDNs();
        Map<String, String> attrs = new HashMap(rdns.length);
        RDN[] var3 = rdns;
        int var4 = rdns.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            RDN rdn = var3[var5];
            attrs.put(rdn.getFirst().getType().getId(), IETFUtils.valueToString(rdn.getFirst().getValue()));
        }

        return attrs;
    }

    public static Map<String, String> getIssuerAttrs(X509Certificate certificate) throws CertificateEncodingException {
        RDN[] rdns = (new JcaX509CertificateHolder(certificate)).getIssuer().getRDNs();
        Map<String, String> attrs = new HashMap(rdns.length);
        RDN[] var3 = rdns;
        int var4 = rdns.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            RDN rdn = var3[var5];
            attrs.put(rdn.getFirst().getType().getId(), IETFUtils.valueToString(rdn.getFirst().getValue()));
        }

        return attrs;
    }

    public static String join(Object[] array, String separator, int startIndex, int endIndex) {
        if (array == null) {
            return null;
        } else {
            if (separator == null) {
                separator = "";
            }

            int bufSize = endIndex - startIndex;
            if (bufSize <= 0) {
                return "";
            } else {
                bufSize *= (array[startIndex] == null ? 16 : array[startIndex].toString().length()) + separator.length();
                StringBuilder buf = new StringBuilder(bufSize);

                for(int i = startIndex; i < endIndex; ++i) {
                    if (i > startIndex) {
                        buf.append(separator);
                    }

                    if (array[i] != null) {
                        buf.append(array[i]);
                    }
                }

                return buf.toString();
            }
        }
    }

    static {
        signatureAlgorithmOIDByPublicKeyAlgorithmMap.put("1.2.643.2.2.20", "1.2.643.2.2.4");
        signatureAlgorithmOIDByPublicKeyAlgorithmMap.put("1.2.643.2.2.19", "1.2.643.2.2.3");
        signatureAlgorithmOIDByPublicKeyAlgorithmMap.put("1.2.643.7.1.1.1.1", "1.2.643.7.1.1.3.2");
        signatureAlgorithmOIDByPublicKeyAlgorithmMap.put("1.2.643.7.1.1.1.2", "1.2.643.7.1.1.3.3");
        signatureAlgorithmOIDByPublicKeyAlgorithmMap.put("GOST3410", "1.2.643.2.2.3");
        signatureAlgorithmOIDByPublicKeyAlgorithmMap.put("GOST3410EL", "1.2.643.2.2.3");
        signatureAlgorithmOIDByPublicKeyAlgorithmMap.put("GOST3410_2012_256", "1.2.643.7.1.1.3.2");
        signatureAlgorithmOIDByPublicKeyAlgorithmMap.put("GOST3410_2012_512", "1.2.643.7.1.1.3.3");
        signatureAlgorithmOIDByPublicKeyAlgorithmMap.put("ECGOST3410", "1.2.643.2.2.3");
        digestAlgorithmOIDBySignatureAlgorithmOIDMap = new HashMap();
        digestAlgorithmOIDBySignatureAlgorithmOIDMap.put("1.2.643.2.2.4", "1.2.643.2.2.9");
        digestAlgorithmOIDBySignatureAlgorithmOIDMap.put("1.2.643.2.2.3", "1.2.643.2.2.9");
        digestAlgorithmOIDBySignatureAlgorithmOIDMap.put("1.2.643.7.1.1.3.2", "1.2.643.7.1.1.2.2");
        digestAlgorithmOIDBySignatureAlgorithmOIDMap.put("1.2.643.7.1.1.3.3", "1.2.643.7.1.1.2.3");
        signatureAlgorithmMethodMap = new HashMap();
        signatureAlgorithmMethodMap.put("1.2.643.2.2.3", "http://www.w3.org/2001/04/xmldsig-more#gostr34102001-gostr3411");
        signatureAlgorithmMethodMap.put("1.2.643.2.2.4", "http://www.w3.org/2001/04/xmldsig-more#gostr34102001-gostr3411");
        signatureAlgorithmMethodMap.put("1.2.643.7.1.1.3.2", "urn:ietf:params:xml:ns:cpxmlsec:algorithms:gostr34102012-gostr34112012-256");
        signatureAlgorithmMethodMap.put("1.2.643.7.1.1.3.3", "urn:ietf:params:xml:ns:cpxmlsec:algorithms:gostr34102012-gostr34112012-512");
        digestAlgorithmMethodMap = new HashMap();
        digestAlgorithmMethodMap.put("1.2.643.2.2.9", "http://www.w3.org/2001/04/xmldsig-more#gostr3411");
        digestAlgorithmMethodMap.put("1.2.643.7.1.1.2.2", "urn:ietf:params:xml:ns:cpxmlsec:algorithms:gostr34112012-256");
        digestAlgorithmMethodMap.put("1.2.643.7.1.1.2.3", "urn:ietf:params:xml:ns:cpxmlsec:algorithms:gostr34112012-512");
        publicKeyNameMap = new HashMap();
        publicKeyNameMap.put("1.2.643.2.2.3", "GOST3411withGOST3410EL");
        publicKeyNameMap.put("1.2.643.7.1.1.3.2", "GOST3411_2012_256withGOST3410_2012_256");
        publicKeyNameMap.put("1.2.643.7.1.1.3.3", "GOST3411_2012_512withGOST3410_2012_512");
        signatureNameMap = new HashMap();
        signatureNameMap.put("1.2.643.2.2.4", "GOST3410");
        signatureNameMap.put("1.2.643.2.2.3", "GOST3410EL");
        signatureNameMap.put("1.2.643.7.1.1.3.2", "GOST3410_2012_256");
        signatureNameMap.put("1.2.643.7.1.1.3.3", "GOST3410_2012_512");
        digestNameMap = new HashMap();
        digestNameMap.put("1.2.643.2.2.9", "GOST3411");
        digestNameMap.put("1.2.643.7.1.1.2.2", "GOST3411_2012_256");
        digestNameMap.put("1.2.643.7.1.1.2.3", "GOST3411_2012_512");
        gostNameMap = new HashMap();
        gostNameMap.put("1.2.643.2.2.4", "ГОСТ Р 34.10-94");
        gostNameMap.put("1.2.643.2.2.3", "ГОСТ Р 34.10-2001");
        gostNameMap.put("1.2.643.7.1.1.3.2", "ГОСТ Р 34.10-2012 для ключей длины 256 бит");
        gostNameMap.put("1.2.643.7.1.1.3.3", "ГОСТ Р 34.10-2012 для ключей длины 512 бит");
        gostNameMap.put("1.2.643.2.2.9", "ГОСТ Р 34.11-94");
        gostNameMap.put("1.2.643.7.1.1.2.2", "ГОСТ Р 34.11-2012, длина выхода 256 бит");
        gostNameMap.put("1.2.643.7.1.1.2.3", "ГОСТ Р 34.11-2012, длина выхода 512 бит");
    }

}
