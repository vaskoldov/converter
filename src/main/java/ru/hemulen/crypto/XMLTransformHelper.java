package ru.hemulen.crypto;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XMLTransformHelper {
    private static final Logger LOGGER = Logger.getLogger(XMLTransformHelper.class.getName());

    public XMLTransformHelper() {
    }

    public static String elementToString(Element element) {
        return elementToString(element, false);
    }

    public static Element findElement(Element parent, String uri, String localName) {
        NodeList nl = parent.getElementsByTagNameNS(uri, localName);
        return nl.getLength() > 0 ? (Element)nl.item(0) : null;
    }

    public static synchronized Transformer getSyncTransformer() throws TransformerConfigurationException {
        TransformerFactory tf = TransformerFactory.newInstance();
        return tf.newTransformer();
    }

    public static String elementToString(Element element, boolean omitxmldeclaration) {
        try {
            DOMSource domSource = new DOMSource(element);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            Transformer transformer = getSyncTransformer();
            if (omitxmldeclaration) {
                transformer.setOutputProperty("omit-xml-declaration", "yes");
            }

            transformer.transform(domSource, result);
            writer.flush();
            String xml = writer.toString();
            writer.close();
            transformer.reset();
            return xml;
        } catch (Exception var7) {
            LOGGER.log(Level.SEVERE, null, var7);
            return null;
        }
    }

    public static String documentToString(Document arg, boolean omitxmldeclaration) {
        try {
            DOMSource domSource = new DOMSource(arg);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            Transformer transformer = getSyncTransformer();
            if (omitxmldeclaration) {
                transformer.setOutputProperty("omit-xml-declaration", "yes");
            }

            transformer.transform(domSource, result);
            writer.flush();
            String xml = writer.toString();
            writer.close();
            transformer.reset();
            return xml;
        } catch (Exception var7) {
            LOGGER.log(Level.SEVERE, null, var7);
            return null;
        }
    }

    public static void documentToFile(Document arg, File dst, boolean omitxmldeclaration) {
        try {
            DOMSource domSource = new DOMSource(arg);
            OutputStream out = new FileOutputStream(dst);
            StreamResult result = new StreamResult(out);
            Transformer transformer = getSyncTransformer();
            if (omitxmldeclaration) {
                transformer.setOutputProperty("omit-xml-declaration", "yes");
            }

            transformer.transform(domSource, result);
            out.flush();
            out.close();
            transformer.reset();
        } catch (Exception var7) {
            LOGGER.log(Level.SEVERE, null, var7);
        }

    }

    public static Document buildDocumentFromString(String xml) {
        return buildDocumentFromString(xml, true);
    }

    public static Document buildDocumentFromString(String xml, boolean namespaceaware) {
        try {
            StringReader reader = new StringReader(xml);
            InputSource is = new InputSource();
            is.setCharacterStream(reader);
            return buildDocumentFromSource(is, namespaceaware);
        } catch (Exception var4) {
            LOGGER.log(Level.SEVERE, null, var4);
            throw new RuntimeException(var4);
        }
    }

    public static Document buildDocumentFromFile(String fileName) {
        return buildDocumentFromFile(fileName, true);
    }

    public static Document buildDocumentFromFile(String fileName, boolean namespaceaware) {
        try {
            Reader reader = new FileReader(fileName);
            Throwable var3 = null;

            Document var5;
            try {
                InputSource is = new InputSource();
                is.setCharacterStream(reader);
                var5 = buildDocumentFromSource(is, namespaceaware);
            } catch (Throwable var15) {
                var3 = var15;
                throw var15;
            } finally {
                if (reader != null) {
                    if (var3 != null) {
                        try {
                            reader.close();
                        } catch (Throwable var14) {
                            var3.addSuppressed(var14);
                        }
                    } else {
                        reader.close();
                    }
                }

            }

            return var5;
        } catch (Exception var17) {
            LOGGER.log(Level.SEVERE, null, var17);
            throw new RuntimeException(var17);
        }
    }

    public static DocumentBuilder getSyncDocumentBuilder(boolean namespaceAware) throws ParserConfigurationException {
        return getSyncDocumentBuilder(namespaceAware, false, false);
    }

    public static synchronized DocumentBuilder getSyncDocumentBuilder(boolean namespaceAware, boolean coalescing, boolean ignoringElementContentWhitespace) throws ParserConfigurationException {
        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(namespaceAware);
        domFactory.setCoalescing(coalescing);
        domFactory.setIgnoringElementContentWhitespace(ignoringElementContentWhitespace);
        return domFactory.newDocumentBuilder();
    }

    public static Document getXMLDocument(String xml) throws Exception {
        DocumentBuilder builder = getSyncDocumentBuilder(true);

        Document var5;
        try {
            InputSource is = new InputSource();
            StringReader reader = new StringReader(xml);
            is.setCharacterStream(reader);
            Document document = builder.parse(is);
            builder.reset();
            var5 = document;
        } finally {
            builder.reset();
        }

        return var5;
    }

    public static Document newDocument(boolean namespaceaware) throws Exception {
        DocumentBuilder builder = getSyncDocumentBuilder(namespaceaware);
        Document doc = builder.newDocument();
        builder.reset();
        return doc;
    }

    public static Document buildDocumentFromSource(InputSource is, boolean namespaceaware) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilder builder = getSyncDocumentBuilder(namespaceaware);
        Document doc = builder.parse(is);
        builder.reset();
        return doc;
    }

    public static String getXMLDocumentNamespace(Element doc) {
        return doc.getNamespaceURI();
    }

}
