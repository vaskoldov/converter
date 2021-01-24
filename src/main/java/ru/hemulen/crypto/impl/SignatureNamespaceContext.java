package ru.hemulen.crypto.impl;

import java.util.Iterator;
import javax.xml.namespace.NamespaceContext;

public class SignatureNamespaceContext implements NamespaceContext {
    public static final String XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#";

    public SignatureNamespaceContext() {
    }

    public String getNamespaceURI(String prefix) {
        return "ds".equals(prefix) ? "http://www.w3.org/2000/09/xmldsig#" : null;
    }

    public String getPrefix(String namespaceURI) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Iterator<String> getPrefixes(String namespaceURI) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
