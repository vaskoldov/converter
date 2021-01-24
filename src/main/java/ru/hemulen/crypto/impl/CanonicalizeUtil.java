package ru.hemulen.crypto.impl;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.CanonicalizationException;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.c14n.InvalidCanonicalizerException;

public class CanonicalizeUtil {
    private CanonicalizeUtil() {
    }

    public static byte[] canonicalize(byte[] xml, String method) throws Exception {
        Objects.requireNonNull(xml, "xml is null");

        try {
            Init.init();
            return Canonicalizer.getInstance(method).canonicalize(xml);
        } catch (CanonicalizationException var3) {
            if (var3.getOriginalException() != null) {
                throw new Exception(var3.getMsgID(), var3.getOriginalException());
            } else {
                throw new Exception(var3.getMsgID(), var3);
            }
        } catch (InvalidCanonicalizerException var4) {
            throw new Exception(var4.getMsgID(), var4);
        }
    }

    public static String canonicalize(String xml, String method) throws Exception {
        Objects.requireNonNull(xml, "xml is null");
        return new String(canonicalize(xml.getBytes(StandardCharsets.UTF_8), method), StandardCharsets.UTF_8);
    }

    public static byte[] canonicalizeC14nExclOmitComments(byte[] xml) throws Exception {
        Objects.requireNonNull(xml, "xml is null");
        return canonicalize(xml, "http://www.w3.org/2001/10/xml-exc-c14n#");
    }

    public static String canonicalizeC14nExclOmitComments(String xml) throws Exception {
        Objects.requireNonNull(xml, "xml is null");
        return canonicalize(xml, "http://www.w3.org/2001/10/xml-exc-c14n#");
    }

}
