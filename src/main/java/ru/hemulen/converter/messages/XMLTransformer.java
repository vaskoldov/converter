package ru.hemulen.converter.messages;

//import com.sun.xml.internal.messaging.saaj.util.FastInfosetReflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import ru.hemulen.converter.thread.RequestProcessor;
import ru.hemulen.converter.utils.AbstractTools;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Класс, содержащий статические методы для различных преобразований запросов и ответов
 * - бизнес-запрос => ClientMessage
 * - QueryResult => бизнес-ответ
 * - извлечение ключевых слов запроса по XPath-выражениям
 */
public class XMLTransformer {
    private static final String ToClientMessageStylesheet = "./src/main/resources/ClientMessage.xslt";
    private static final String FromQueryResultStyleSheet = "./src/main/resources/FromQueryResult.xslt";
    private static final String ToEGRNTechDescStylesheet = "./src/main/resources/EGRNStatement2TechDesc.xslt";
    // Новая версия технического описания ЕГРН
    private static final String ToEGRNTechDesc26Stylesheet = "./src/main/resources/EGRNStatement2TechDesc26.xslt";
    private static final String ToEGRNMainRequestStylesheet = "./src/main/resources/EGRNStatement2Request.xslt";
    // Новая версия запроса по ВС ЕГРН
    private static final String ToEGRNMainRequest26Stylesheet = "./src/main/resources/EGRNStatement2Request26.xslt";
    private static final String ToESIAClientMessage = "./src/main/resources/ESIA.xslt";
    private static final String ToFSSPRequest = "./src/main/resources/FSSPStatement2Request.xslt";
    private static final String SplitFSSPRequest = "./src/main/resources/SplitFSSPRequest.xslt";
    private static final String ToFSSPResponse = "./src/main/resources/AnswerToFSSPRequest.xslt";

    private static final Logger LOG = LoggerFactory.getLogger(XMLTransformer.class.getName());
    private static DocumentBuilderFactory factory;
    private static DocumentBuilder builder;
    private static TransformerFactory transformerFactory;
    private static Transformer transformerToClientMessage;
    private static Transformer transformerFromQueryResult;
    private static Transformer transformerEGRNToTechDesc;
    private static Transformer transformerEGRNToTechDesc26;
    private static Transformer transformerEGRNToMainRequest;
    private static Transformer transformerEGRNToMainRequest26;
    private static Transformer transformerESIAToClientMessage;
    private static Transformer transformerFSSPToRequest;
    private static Transformer transformerFSSPRequestToResponse;
    private static Transformer transformerAnswerFSSPRequest;
    private static XPathFactory xpathFactory;
    private static XPath xpath;

    static {
        try {
            factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setIgnoringElementContentWhitespace(true);
            builder = factory.newDocumentBuilder();
            transformerFactory = TransformerFactory.newInstance();

            // Создаем трансформер для преобразования бизнес-запроса в конверт адаптера
            transformerToClientMessage = transformerFactory.newTransformer(new StreamSource(new File(ToClientMessageStylesheet)));

            // Создаем трансформер для извлечения бизнес-ответа (содержимого content) из конверта адаптера
            transformerFromQueryResult = transformerFactory.newTransformer(new StreamSource(new File(FromQueryResultStyleSheet)));

            // Создаем трансформер для преобразования заявления ЕГРН в техническое описание
            transformerEGRNToTechDesc = transformerFactory.newTransformer(new StreamSource(new File(ToEGRNTechDescStylesheet)));

            // Создаем трансформер для преобразования заявления ЕГРН в техническое описание версии v.0.26
            transformerEGRNToTechDesc26 = transformerFactory.newTransformer(new StreamSource(new File(ToEGRNTechDesc26Stylesheet)));

            // Создаем трансформер для преобразования заявления ЕГРН в основной запрос
            transformerEGRNToMainRequest = transformerFactory.newTransformer(new StreamSource(new File(ToEGRNMainRequestStylesheet)));

            // Создаем трансформер для преобразования заявления ЕГРН в основной запрос по версии 1.2.2
            transformerEGRNToMainRequest26 = transformerFactory.newTransformer(new StreamSource(new File(ToEGRNMainRequest26Stylesheet)));

            // Создаем трансформер для преобразования запроса персональных данных пользователя ЕСИА в ClientMessage
            transformerESIAToClientMessage = transformerFactory.newTransformer(new StreamSource(new File(ToESIAClientMessage)));

            // Создаем трансформер для преобразования вложения ФССП в ClientMessage
            transformerFSSPToRequest = transformerFactory.newTransformer(new StreamSource(new File(ToFSSPRequest)));

            // Создаем трансформер для преобразования запроса ФССП в ответ ИС УВ
            transformerFSSPRequestToResponse = transformerFactory.newTransformer(new StreamSource(new File(SplitFSSPRequest)));

            // Создаем трансформер для формирования ответа ФССП
            transformerAnswerFSSPRequest = transformerFactory.newTransformer(new StreamSource(new File(ToFSSPResponse)));

            // Создаем обработчик XPath запросов
            xpathFactory = XPathFactory.newInstance();
            xpath = xpathFactory.newXPath();

        } catch (ParserConfigurationException | TransformerConfigurationException e) {
            LOG.error(e.getMessage());
        }
    }

    /**
     * Метод парсит XML-файл в DOM-объект
     *
     * @param file XML-файл
     * @return DOM-объект
     * @throws IOException  ошибка файловой операции
     * @throws SAXException ошибка парсинга XML
     */
    public synchronized static Document fileToDocument(File file) throws IOException, SAXException {
        if (file == null) {
            return null;
        }
        return builder.parse(file);
    }

    /**
     * Метод выполняет над DOM-объектом XPath-запросы, описанные в объекте VSInfo, и формирует из их результатов строку с ключевыми параметрами запроса
     *
     * @param dom    DOM-объект запроса
     * @param vsInfo Описание вида сведений, включающее массив XPath-запросов
     * @return Возвращаемые ключевые параметры запроса
     */
    public synchronized static String getKeywords(Document dom, VSInfo vsInfo) throws XPathExpressionException {
        String keywords = "";
        for (int i = 0; i < vsInfo.keywords.length; i++) {
            XPathExpression xpathExpression = xpath.compile(vsInfo.keywords[i]);
            NodeList nodeList = (NodeList) xpathExpression.evaluate(dom, XPathConstants.NODESET);
            for (int j = 0; j < nodeList.getLength(); j++) {
                keywords += nodeList.item(j).getTextContent() + " ";
            }
        }
        return keywords.trim();
    }

    /**
     * Метод преобразует XML-запрос вида сведений в сообщение ClientMessage СМЭВ-адаптера
     *
     * @param requestDOM     DOM-объект запроса ВС
     * @param resultFile     Файл, в который сохраняется результат
     * @param clientID       Клиентский идентификатор запроса
     * @param personalSign   Подпись должностного лица
     * @param attachmentFile Файл вложения
     * @param attachmentSign Подпись файла вложения
     */
    public synchronized static void createClientMessage(Document requestDOM,
                                                        File resultFile,
                                                        String clientID,
                                                        String personalSign,
                                                        String attachmentFile,
                                                        String attachmentSign) throws TransformerException {
        transformerToClientMessage.setParameter("ClientID", clientID);
        transformerToClientMessage.setParameter("PersonalSign", personalSign);
        transformerToClientMessage.setParameter("AttachmentFile", attachmentFile);
        transformerToClientMessage.setParameter("AttachmentSign", attachmentSign);
        Source source = new DOMSource(requestDOM);
        Result target = new StreamResult(resultFile);
        transformerToClientMessage.transform(source, target);
    }

    public synchronized static void createESIAClientMessage(Document requestDOM, File resultFile, String clientID) throws TransformerException {
        transformerESIAToClientMessage.setParameter("ClientID", clientID);
        Source source = new DOMSource(requestDOM);
        Result target = new StreamResult(resultFile);
        transformerESIAToClientMessage.transform(source, target);
    }

    /**
     * Метод извлекает содержимое MessagePrimaryContent из ответа responseDOM в файл resultFile
     *
     * @param responseDOM Ответ вида сведений в конверте QueryMessage
     * @param resultFile  Файл, в который сохраняется содержимое бизнес-ответа
     * @throws TransformerException ошибка преобразования XML
     */
    public synchronized static void extractPrimaryContent(Document responseDOM, File resultFile) throws TransformerException {
        Source source = new DOMSource(responseDOM);
        Result target = new StreamResult(resultFile);
        transformerFromQueryResult.transform(source, target);
    }

    public synchronized static File createTechDesc(File egrnRequest, String vsName ) throws ParserConfigurationException, SAXException, IOException, TransformerException {
        // Запрос в преобразовании не участвует, но для удовлетворения сигнатуры метода
        // ему нужно подать DOMSource, который получаем из элемента egrnDom
        Element egrnDom = AbstractTools.fileToElement(egrnRequest);
        DOMSource source = new DOMSource(egrnDom.getOwnerDocument());
        // Определяем файл с результатом преобразования (он всегда называется request.xml)
        File targetFile = RequestProcessor.signDir.resolve("request.xml").toFile();
        StreamResult target = new StreamResult(targetFile);
        switch (vsName) {
            case "ЕГРН":
                transformerEGRNToTechDesc.setParameter("fileName", egrnRequest.getName());
                transformerEGRNToTechDesc.transform(source, target);
                break;
            case "ЕГРН_26":
                transformerEGRNToTechDesc26.setParameter("fileName", egrnRequest.getName());
                transformerEGRNToTechDesc26.transform(source, target);
                break;
        }
        return targetFile;
    }

    public synchronized static File createMainRequest(File egrnStatement, String clientID, String vsName) throws ParserConfigurationException, SAXException, IOException, TransformerException, XPathExpressionException {
        // Получаем корневой элемент из заявления
        Element egrnElement = AbstractTools.fileToElement(egrnStatement);
        // Извлекаем значения параметров для преобразования
        String actionCode = getActionCode(egrnElement);
        String regionCode = getRegionCode(egrnElement);
        String fileName = egrnStatement.getName();
        // Преобразовываем заявления и параметры в основной запрос для ClientMessage,
        // который сохраняется с расширением .cm (ClientMessage)
        String targetFileName = egrnStatement.getName() + ".cm";
        File targetFile = RequestProcessor.inputDir.resolve(targetFileName).toFile();
        StreamResult target = new StreamResult(targetFile);
        DOMSource source = new DOMSource(egrnElement.getOwnerDocument());
        switch (vsName) {
            case "ЕГРН":
                transformerEGRNToMainRequest.setParameter("regionCode", regionCode);
                transformerEGRNToMainRequest.setParameter("actionCode", actionCode);
                transformerEGRNToMainRequest.setParameter("fileName", fileName);
                transformerEGRNToMainRequest.setParameter("clientID", clientID);
                transformerEGRNToMainRequest.transform(source, target);
                break;
            case "ЕГРН_26":
                transformerEGRNToMainRequest26.setParameter("regionCode", regionCode);
                transformerEGRNToMainRequest26.setParameter("actionCode", actionCode);
                transformerEGRNToMainRequest26.setParameter("fileName", fileName);
                transformerEGRNToMainRequest26.setParameter("clientID", clientID);
                transformerEGRNToMainRequest26.transform(source, target);
                break;
        }
        return targetFile;
    }

    public synchronized static File createFSSPRequest(File fsspStatement, File attachmentFile, String clientID) throws ParserConfigurationException, SAXException, IOException, TransformerException, XPathExpressionException {
        String targetFileName = fsspStatement.getName() + ".cm";
        File targetFile = RequestProcessor.inputDir.resolve(targetFileName).toFile();
        StreamResult target = new StreamResult(targetFile);
        Element fsspDOM = AbstractTools.fileToElement(fsspStatement);
        DOMSource source = new DOMSource(fsspDOM.getOwnerDocument());
        String requestDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(Calendar.getInstance().getTime());
        transformerFSSPToRequest.setParameter("fileName", attachmentFile.getName());
        transformerFSSPToRequest.setParameter("requestDate", requestDate);
        transformerFSSPToRequest.setParameter("clientID", clientID);
        transformerFSSPToRequest.transform(source, target);
        return targetFile;
    }

    private synchronized static String getActionCode(Element egrnDom) throws XPathExpressionException {
        // Определяем код учетно-регистрационного действия
        String actionCodeQuery = "/*[local-name()='EGRNRequest']/*[local-name()='header']/*[local-name()='actionCode']/text()";
        XPathExpression exp = xpath.compile(actionCodeQuery);
        Object actionCodeResult = exp.evaluate(egrnDom, XPathConstants.STRING);
        String actionCode = null;
        if (actionCodeResult != null) {
            actionCode = (String) actionCodeResult;
        }
        return actionCode;
    }

    private synchronized static String getRegionCode(Element egrnDom) throws XPathExpressionException {
        // Определяем все коды регионов в заявлении
        String regionCodeQuery = "//*[local-name()='region']/*[local-name()='code']";
        XPathExpression exp = xpath.compile(regionCodeQuery);
        Object regionCodeResult = exp.evaluate(egrnDom, XPathConstants.NODESET);
        NodeList regionCodes = (NodeList) regionCodeResult;
        String regionCode = "";
        if (regionCodes.getLength() == 0) {
            // Если в явном виде коды региона в заявлении не указаны, то
            // определяем код региона из кадастрового номера объекта недвижимости
            String cadastralNumberQuery = "//*[local-name()='cadastralNumber']/*[local-name()='cadastralNumber']";
            exp = xpath.compile(cadastralNumberQuery);
            Object cadastralNumberResult = exp.evaluate(egrnDom, XPathConstants.STRING);
            if (cadastralNumberResult != null) {
                String[] cadastralNumberParts = ((String) cadastralNumberResult).split(":");
                regionCode = cadastralNumberParts[0];
                if (regionCode.length() == 1) {
                    regionCode = "0" + regionCode;
                }
                switch (regionCode) {
                    case "90":
                        regionCode = "91"; // Кадастровую область "Крым" приводим к коду региона
                        break;
                    case "91":
                        regionCode = "92"; // Кадастровую область "Севастополь" приводим к коду региона
                        break;
                    case "80":
                        regionCode = "75";
                        break;
                    case "81":
                        regionCode = "59";
                        break;
                    case "82":
                        regionCode = "41";
                        break;
                    case "84":
                    case "88":
                        regionCode = "24";
                        break;
                    case "85":
                        regionCode = "38";
                        break;
                }
            }
        } else {
            // А иначе ищем первый код длиной 2
            for (int i = 0; i < regionCodes.getLength(); i++) {
                if (regionCodes.item(i).getTextContent().length() == 2) {
                    regionCode = regionCodes.item(i).getTextContent();
                    break;
                }
            }
        }
        // Если ничего не нашлось, то Красноярский край
        if (regionCode.isEmpty()) {
            regionCode = "24";
        }

        return regionCode;
    }

    public static String getFSSPDocumentKey(Element request) throws XPathExpressionException {
        String documentIdQuery = "//*[local-name()='Document']/*[local-name()='ID']";
        XPathExpression exp = xpath.compile(documentIdQuery);
        NodeList documentIdNodes = (NodeList) exp.evaluate(request, XPathConstants.NODESET);
        if (documentIdNodes.getLength() !=0) {
            return documentIdNodes.item(0).getTextContent();
        }
        return "";
    }

    public static String getElementValue(Node root, String tagName){
        String query = String.format("//*[local-name()='%s']/text()", tagName);
        try {
            XPathExpression exp = xpath.compile(query);
            return  (String) exp.evaluate(root, XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            return null;
        }
    }

    public static String splitFSSPRequest(Document requestDOM, String docKey) throws TransformerException {
        StreamResult target = new StreamResult(new StringWriter());
        DOMSource source = new DOMSource(requestDOM);
        transformerFSSPRequestToResponse.setParameter("DocKey", docKey);
        transformerFSSPRequestToResponse.transform(source, target);
        return target.getWriter().toString();
    }

    public static File answerFSSPRequest(File request, File targetFile) throws IOException, ParserConfigurationException, SAXException, TransformerException {
        String currentTimestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(Calendar.getInstance().getTime());
        transformerAnswerFSSPRequest.setParameter("Timestamp", currentTimestamp);
        StreamResult target = new StreamResult(targetFile);
        Element fsspDOM = AbstractTools.fileToElement(request);
        DOMSource source = new DOMSource(fsspDOM.getOwnerDocument());
        transformerAnswerFSSPRequest.transform(source, target);
        // Читаем файл в строку
        String content = new String(Files.readAllBytes(targetFile.toPath()));
        // Удаляем namespace xmlns:uuid="java.util.UUID", из-за которого адаптер не подписывает ответ
        content = content.replace("xmlns:uuid=\"java.util.UUID\"", "");
        // Записываем строку обратно в файл
        FileWriter fw = new FileWriter(targetFile);
        fw.write(content);
        fw.close();
        return targetFile;
    }
}
