package ru.hemulen.converter.messages;

import com.sun.xml.internal.messaging.saaj.util.FastInfosetReflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
import java.io.File;
import java.io.IOException;
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
    private static final String ToEGRNMainRequestStylesheet = "./src/main/resources/EGRNStatement2Request.xslt";
    private static final String ToESIAClientMessage = "./src/main/resources/ESIA.xslt";
    private static final String ToFSSPClientMessage = "./src/main/resources/FSSPStatement2Request.xslt";
    private static Logger LOG = LoggerFactory.getLogger(XMLTransformer.class.getName());
    private static DocumentBuilderFactory factory;
    private static DocumentBuilder builder;
    private static TransformerFactory transformerFactory;
    private static Transformer transformerToClientMessage;
    private static Transformer transformerFromQueryResult;
    private static Transformer transformerEGRNToTechDesc;
    private static Transformer transformerEGRNToMainRequest;
    private static Transformer transformerESIAToClientMessage;
    private static Transformer transformerFSSPToClientMessage;
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
            File toClientMessageStylesheet = new File(ToClientMessageStylesheet);
            StreamSource toClientMessageStyleSource = new StreamSource(toClientMessageStylesheet);
            transformerToClientMessage = transformerFactory.newTransformer(toClientMessageStyleSource);

            // Создаем трансформер для извлечения бизнес-ответа (содержимого content) из конверта адаптера
            File fromQueryResultStylesheet = new File(FromQueryResultStyleSheet);
            StreamSource fromQueryResultStyleSource = new StreamSource(fromQueryResultStylesheet);
            transformerFromQueryResult = transformerFactory.newTransformer(fromQueryResultStyleSource);

            // Создаем трансформер для преобразования заявления ЕГРН в техническое описание
            File toEGRNTechDescStylesheet = new File(ToEGRNTechDescStylesheet);
            StreamSource toEGRNTechDescStyleSource = new StreamSource(toEGRNTechDescStylesheet);
            transformerEGRNToTechDesc = transformerFactory.newTransformer(toEGRNTechDescStyleSource);

            // Создаем трансформер для преобразования заявления ЕГРН в основной запрос
            File toEGRNMainRequestStyleSheet = new File(ToEGRNMainRequestStylesheet);
            StreamSource toEGRNMainRequestStyleSource = new StreamSource(toEGRNMainRequestStyleSheet);
            transformerEGRNToMainRequest = transformerFactory.newTransformer(toEGRNMainRequestStyleSource);

            // Создаем трансформер для преобразования запроса персональных данных пользователя ЕСИА в ClientMessage
            File toESIAClientMessageStylesheet = new File(ToESIAClientMessage);
            StreamSource toESIAClientMessageStyleSource = new StreamSource(toESIAClientMessageStylesheet);
            transformerESIAToClientMessage = transformerFactory.newTransformer(toESIAClientMessageStyleSource);

            // Создаем трансформер для преобразования вложения ФССП в ClientMessage
            File toFSSPClientMessageStylesheet = new File(ToFSSPClientMessage);
            StreamSource toFSSPClientMessageStyleSource = new StreamSource(toFSSPClientMessageStylesheet);
            transformerFSSPToClientMessage = transformerFactory.newTransformer(toFSSPClientMessageStyleSource);

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

    public synchronized static File createTechDesc(File egrnRequest) throws ParserConfigurationException, SAXException, IOException, TransformerException {
        // Запрос в преобразовании не участвует, но для удовлетворения сигнатуры метода
        // ему нужно подать DOMSource, который получаем из элемента egrnDom
        Element egrnDom = AbstractTools.fileToElement(egrnRequest);
        DOMSource source = new DOMSource(egrnDom.getOwnerDocument());
        transformerEGRNToTechDesc.setParameter("fileName", egrnRequest.getName());
        // Определяем файл с результатом преобразования (он всегда называется request.xml)
        File targetFile = RequestProcessor.signDir.resolve("request.xml").toFile();
        StreamResult target = new StreamResult(targetFile);
        transformerEGRNToTechDesc.transform(source, target);
        return targetFile;
    }

    public synchronized static File createMainRequest(File egrnStatement, String clientID) throws ParserConfigurationException, SAXException, IOException, TransformerException, XPathExpressionException {
        // Получаем корневой элемент из заявления
        Element egrnElement = AbstractTools.fileToElement(egrnStatement);
        // Извлекаем значения параметров для преобразования
        String actionCode = getActionCode(egrnElement);
        String regionCode = getRegionCode(egrnElement);
        String fileName = egrnStatement.getName();
        // Преобразовываем заявления и параметры в основной запрос для ClientMessage,
        // который сохраняется с расширением .cm
        String targetFileName = egrnStatement.getName() + ".cm";
        File targetFile = RequestProcessor.inputDir.resolve(targetFileName).toFile();
        StreamResult target = new StreamResult(targetFile);
        DOMSource source = new DOMSource(egrnElement.getOwnerDocument());
        transformerEGRNToMainRequest.setParameter("regionCode", regionCode);
        transformerEGRNToMainRequest.setParameter("actionCode", actionCode);
        transformerEGRNToMainRequest.setParameter("fileName", fileName);
        transformerEGRNToMainRequest.setParameter("clientID", clientID);
        transformerEGRNToMainRequest.transform(source, target);
        return targetFile;
    }

    public synchronized static File createFSSPClientMessage(File fsspStatement, String clientID) throws ParserConfigurationException, SAXException, IOException, TransformerException, XPathExpressionException {
        String targetFileName = fsspStatement.getName() + ".cm";
        File targetFile = RequestProcessor.inputDir.resolve(targetFileName).toFile();
        StreamResult target = new StreamResult(targetFile);
        Element fsspDOM = AbstractTools.fileToElement(fsspStatement);
        DOMSource source = new DOMSource(fsspDOM.getOwnerDocument());
        String requestDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(Calendar.getInstance().getTime());;
        transformerFSSPToClientMessage.setParameter("fileName", fsspStatement);
        transformerFSSPToClientMessage.setParameter("requestDate", requestDate);
        transformerFSSPToClientMessage.setParameter("clientID", clientID);
        transformerFSSPToClientMessage.transform(source, target);
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
}
