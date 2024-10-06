package ru.hemulen.converter.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import ru.hemulen.converter.exceptions.AttachmentException;
import ru.hemulen.converter.exceptions.ParsingException;
import ru.hemulen.converter.exceptions.ResponseException;
import ru.hemulen.converter.thread.ResponseProcessor;
import ru.hemulen.converter.thread.Response13Processor;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Response {
    private final static Logger LOG = LoggerFactory.getLogger(Response.class.getName());
    private final static String FSSP_NAMESPACE = "urn://x-artifacts-fssp-ru/mvv/smev3/application-documents/1.1.1";
    private final static String EGRN_NAMESPACE = "urn://x-artefacts-rosreestr-gov-ru/virtual-services/egrn-statement/1.2.2";
    private File responseFile;
    private Document responseDOM;
    private File resultFile;
    private String responseType;    // Тип ответа: PrimaryMessage, StatusMessage, RejectMessage
    private Long log_id;            // Идентификатор записи log, к которой относится ответ
    private String clientID;        // Идентификатор ответа
    private String messageID;       // СМЭВ-идентификатор ответа
    private String requestID;       // Идентификатор запроса, ответом на который является собственно ответ
    private String currentStatus;   // Текущий статус запроса, ответ на который обрабатывается
    private String requestFileName; // Имя файла с запросом, под которым нужно сохранить ответ
    private String errSource;       // Тип источника ошибки
    private String errCode;         // Код ошибки
    private String errDescription;  // Описание ошибки
    private Boolean isESIAResponse; // Признак, что ответ пришел во второй instance адаптера
    private Boolean isFSSPRequest;  // Признак, что ответ является входящим запросом ФССП
    private Boolean isFSSPResponse; // Признак, что ответ является ответом ФССП (там по-другому обрабатываются статусы)
    private Boolean isEGRNResponse; // Признак, что ответ является ответом ЕГРН (в новой версии ВС по другому обрабатываются статусы)

    public Response(File responseFile) throws ResponseException, ParsingException {
        // Определяем, в каком каталоге находится обрабатываемый запрос
        isESIAResponse = responseFile.getPath().startsWith(Response13Processor.inputDir.toString());
        this.responseFile = responseFile;
        errCode = "";
        errDescription = "";
        try {
            this.responseDOM = XMLTransformer.fileToDocument(responseFile);
            Element root = this.responseDOM.getDocumentElement();
            // Проверяем, что ответ является входящим запросом ФССП
            NodeList fsspRequest = root.getElementsByTagNameNS(FSSP_NAMESPACE, "ApplicationDocumentsRequest");
            if (fsspRequest.getLength() != 0) {
                isFSSPRequest = true;
            } else {
                isFSSPRequest = false;
            }
            // Проверяем, что ответ является ответом ФССП
            NodeList fsspResponse = root.getElementsByTagNameNS(FSSP_NAMESPACE, "ApplicationDocumentsResponse");
            if (fsspResponse.getLength() != 0) {
                isFSSPResponse = true;
            } else {
                isFSSPResponse = false;
            }
            // Проверяем, что ответ является ответом по новой версии ЕГРН
            NodeList egrnResponse = root.getElementsByTagNameNS(EGRN_NAMESPACE,"Response");
            if (egrnResponse.getLength() != 0) {
                isEGRNResponse = true;
            } else {
                isEGRNResponse = false;
            }
            // Считываем clientID ответа
            NodeList clientIDNodes = root.getElementsByTagName("clientId");
            if (clientIDNodes.getLength() != 0) {
                clientID = clientIDNodes.item(0).getTextContent();
            }
            if (isFSSPRequest) {
                    responseType = "FSSPRequest";
                    // Запросы ФССП обрабатываются по отдельному алгоритму, поэтому здесь не заполняем остальные члены класса
                    return;
                }
            // Считываем messageID ответа
            NodeList messageIDNodes = root.getElementsByTagName("MessageId");
            if (messageIDNodes.getLength() != 0) {
                messageID = messageIDNodes.item(0).getTextContent();
            }
            // Определяем тип ответа
            NodeList nodeList = root.getElementsByTagName("messageType");
            if (nodeList.getLength() != 0) {
                responseType = nodeList.item(0).getTextContent();
                if (responseType.equals("StatusMessage")) {
                    // Бизнес-статусы возвращаются в сообщении StatusMessage, но в отличие от статусов СМЭВ
                    // у них есть sender
                    NodeList sender = root.getElementsByTagName("Sender");
                    if (sender.getLength() != 0) {
                        responseType = "BusinessStatus";
                    }
                }
                if (responseType.equals("PrimaryMessage") && isFSSPResponse) {
                    responseType = "FSSPBusinessStatus";
                }
                // В ответах новой версии ЕГРН смотрим на статусы - не все они являются финальными
                if (responseType.equals("PrimaryMessage") && isEGRNResponse) {
                    String code = root.getElementsByTagNameNS(EGRN_NAMESPACE, "code").item(0).getTextContent();
                    switch (code) {
                        case "8":   // Принято от заявителя
                        case "10":  // Ожидание оплаты
                        case "5":   // Передача обращения в работу
                        case "7":   // Приостановление обработки
                        case "9":   // Возобновление обработки
                            responseType = "BusinessStatus";
                            break;
                        case "4":   // Завершение обработки
                        default:
                            // responseType не изменяется
                            break;
                    }
                }
            } else {
                // Если нельзя определить тип ответа, то нельзя и обработать его - выбрасываем исключение
                throw new ResponseException("Невозможно определить тип ответа " + responseFile.getName(), new Exception());
            }
            // В сообщениях PrimaryMessage и BusinessStatus ссылка на запрос называется replyToClientId
            nodeList = root.getElementsByTagName("replyToClientId");
            if (nodeList.getLength() != 0) {
                requestID = nodeList.item(0).getTextContent();
            } else {
                // В сообщениях StatusMessage и ErrorMessage ссылка на запрос называется originalClientId
                nodeList = root.getElementsByTagName("originalClientId");
                if (nodeList.getLength() != 0) {
                    requestID = nodeList.item(0).getTextContent();
                } else {
                    nodeList = root.getElementsByTagName("OriginalMessageID");
                    if (nodeList.getLength() != 0) {
                        // Получаем данные запроса из БД по его MessageID
                        String messageID = nodeList.item(0).getTextContent();
                        ResultSet resultSet = ResponseProcessor.dbConnection.getRequestByMessageID(messageID);
                        if (resultSet.next()) {
                            requestID = resultSet.getString(1);
                        } else {
                            // Пришел ответ на незарегистрированный запрос, который сохраняем в логе в новой строке
                            LOG.info(String.format("Пришел ответ %s без ссылки на запрос.", responseFile.getName()));
                            requestID = "";
                        }
                        resultSet.close();
                    }
                }
            }
            // Получаем идентификатор записи лога, имя файла запроса и текущий статус запроса в базе данных
            ResultSet resultSet = ResponseProcessor.dbConnection.getRequest(requestID);
            if (resultSet.next()) {
                log_id = resultSet.getLong(1);
                requestFileName = resultSet.getString(2);
                currentStatus = resultSet.getString(4);
                if (currentStatus == null) {
                    // Если пришел ответ на запрос с неопределенным статусом, то очевидно, что этот запрос был отправлен
                    // Просто почему-то не обновился его статус в процессе RequestUpdater
                    currentStatus = "SENT";
                }
            }
            resultSet.close();
            if (log_id == null) {
                throw new ResponseException(String.format("Получен ответ %s на запрос %s, который отсутствует в log.", responseFile.getName(), requestID), new Exception());
            }
            resultFile = ResponseProcessor.outputDir.resolve(Paths.get(requestFileName)).toFile();
        } catch (IOException | SQLException e) {
            throw new ResponseException(String.format("Ошибка обработки ответа %s", responseFile.getName()), e);
        } catch (SAXException e) {
            throw new ParsingException(String.format("Ошибка парсинга ответа %s.", responseFile.getName()), e);
        }

    }

    //=============================== МЕТОДЫ ОБРАБОТКИ ОТВЕТА ===============================

    /**
     * Метод обрабатывает сообщение типа PrimaryMessage, извлекает из него бизнес-ответ
     * и сохраняет в каталоге responses с именем файла исходного запроса
     *
     * @throws ResponseException ошибка при обработке ответа
     */
    public void processPrimaryMessage() throws ResponseException, AttachmentException, ParsingException {
        try {
            // Извлекаем секцию под MessagePrimaryContent в отдельный файл
            XMLTransformer.extractPrimaryContent(responseDOM, resultFile);
            // Проверяем наличие вложений в ответе
            Element root = responseDOM.getDocumentElement();
            NodeList attachmentHeaders = root.getElementsByTagName("AttachmentHeader");
            if (attachmentHeaders.getLength() != 0) {
                // Определяем путь к каталогу с вложениями, который зависит от instance адаптера, получившего ответ
                Path attachmentDir = (this.isESIAResponse)?Response13Processor.attachmentDir:ResponseProcessor.attachmentDir;

                // Определяем массив файлов вложений
                List<File> attachmentFiles = new LinkedList<>();
                // Формируем архив из XML ответа и файлов вложений, который сохраняется с именем исходного запроса и расширением ZIP
                for (int i = 0; i < attachmentHeaders.getLength(); i++) {
                    Element attachmentHeader = (Element) attachmentHeaders.item(i);
                    // Имя каталога с файлом - это элемент Id из AttachmentHeader, если файл передан через FTP
                    Path attachmentPath = null;
                    NodeList attachmentPathNodes = attachmentHeader.getElementsByTagName("Id");
                    if (attachmentPathNodes.getLength() != 0) {
                        attachmentPath = Paths.get(attachmentPathNodes.item(0).getTextContent());
                    }
                    // Получаем имя подкаталога вложений из элемента clientID - это второй подкаталог, если файл передан через FTP
                    // Если файл передан MTOM (внутри ответа), то clientID - имя первого подкаталога в attachmentDir
                    Path attachmentSubfolder = Paths.get(clientID);
                    // Получаем имя файла
                    Path attachmentFile = null;
                    NodeList attachmentFileNodes = attachmentHeader.getElementsByTagName("filePath");
                    if (attachmentFileNodes.getLength() != 0) {
                        attachmentFile = Paths.get(attachmentFileNodes.item(0).getTextContent());
                    }

                    // Проверяем первый вариант пути в base-storage для FTP-вложений: каталог Id/clientId
                    Path attachmentFilePath = attachmentDir.resolve(attachmentPath).resolve(attachmentSubfolder).resolve(attachmentFile);
                    if (!attachmentFilePath.toFile().exists()) {
                        // Если файл не существует, то проверяем второй вариант пути в base-storage для MTOM-вложений: каталог clientId
                        attachmentFilePath = attachmentDir.resolve(attachmentSubfolder).resolve(attachmentFile);
                    }
                    if (attachmentFilePath.toFile().exists()) {
                        // Если attachmentFilePath существует, то добавляем файл в список
                        attachmentFiles.add(attachmentFilePath.toFile());
                    } else {
                        // Удаляем файл с содержимым MessagePrimaryContent, созданный ранее
                        try {
                            Files.delete(resultFile.toPath());
                        } catch (IOException e) {
                            LOG.info(String.format("Не удалось удалить файл с ответом %s из каталога responses.", resultFile.toString()));
                            LOG.error(e.getMessage());
                        }
                        // Выбрасываем ошибку обработки ответа и разбираемся потом вручную
                        throw new ResponseException(String.format("Отсутствует файл вложения %s к запросу %s.", attachmentPath, requestFileName), new Exception());
                    }
                }
                // Расширение resultFile меняем с XML на ZIP
                String archiveFileName = resultFile.toString();
                archiveFileName = archiveFileName.substring(0, archiveFileName.lastIndexOf(".")) + ".zip";
                // Формируем архив из основного (XML) файла ответа и файлов вложений
                try {
                    FileOutputStream fos = new FileOutputStream(archiveFileName);
                    ZipOutputStream zip = new ZipOutputStream(fos, StandardCharsets.UTF_8);
                    // Добавляем в архив основной XML файл ответа
                    ZipEntry entry = new ZipEntry(resultFile.getName());
                    zip.putNextEntry(entry);
                    // Добавляем содержимое XML файла ответа
                    FileInputStream fis = new FileInputStream(resultFile);
                    byte[] buffer = new byte[fis.available()];
                    fis.read(buffer);
                    zip.write(buffer);
                    zip.closeEntry();
                    fis.close();
                    // Добавляем файлы вложений
                    for (File entryFile : attachmentFiles) {
                        String entryName = entryFile.getName();
                        // От некоторых поставщиков приходят архивы без расширения zip
                        if (!entryName.contains(".")) {
                            // Если в имени файла нет расширения, то добавляем расширение .zip
                            entryName += ".zip";
                        }
                        entry = new ZipEntry(entryName);
                        zip.putNextEntry(entry);
                        fis = new FileInputStream(entryFile);
                        buffer = new byte[fis.available()];
                        fis.read(buffer);
                        zip.write(buffer);
                        zip.closeEntry();
                        fis.close();
                    }
                    // Закрываем архив
                    zip.close();
                    // Проверяем размер получившегося архива
                    File archiveFile = new File(archiveFileName);
                    if (archiveFile.exists() && archiveFile.length() == 0) {
                        // Если архив нулевого размера, значит не все вложения были сформированы адаптером на момент обработки ответа
                        // Выбрасываем исключение
                        throw new AttachmentException("Нулевой размер архива с вложениями", new Exception());
                    } else {
                        // Удаляем XML-файл с ответом
                        Files.delete(resultFile.toPath());
                    }
                } catch (IOException e) {
                    LOG.info(String.format("Произошла ошибка при формировании архива %s с вложениями.", archiveFileName));
                    throw new ResponseException(String.format("Не удалось сформировать архив с вложениями из ответа %s", responseFile.getName()), e);
                }
            }
        } catch (TransformerException e) {
            throw new ParsingException("Не удалось извлечь PrimaryContent из " + responseFile.getName(), e);
        }

    }

    /**
     * Метод обрабатывает сообщение типа StatusMessage, в котором указан отправитель.
     * Такие сообщения приходят от поставщиков и содержат различные бизнес-статусы запросов.
     */
    public void processBusinessStatus() {
        Element root = responseDOM.getDocumentElement();
        NodeList nodeList;
        if (isEGRNResponse) {
            nodeList = root.getElementsByTagNameNS(EGRN_NAMESPACE, "code");
            if (nodeList.getLength() != 0) {
                errCode = nodeList.item(0).getTextContent();
            }
            nodeList = root.getElementsByTagNameNS(EGRN_NAMESPACE, "name");
            if (nodeList.getLength() != 0) {
                errDescription = nodeList.item(0).getTextContent();
            }
            // Обрабатываем StateDescription
            nodeList = root.getElementsByTagNameNS(EGRN_NAMESPACE, "StateDescription");
            if (nodeList.getLength() != 0) {
                errDescription += ";" + nodeList.item(0).getTextContent();
            }
            // Обрабатываем StateParameter
            nodeList = root.getElementsByTagNameNS(EGRN_NAMESPACE, "StateParameter");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Element stateParameter = (Element) nodeList.item(i);
                NodeList key = stateParameter.getElementsByTagNameNS(EGRN_NAMESPACE, "Key");
                errCode += ";" + key.item(0).getTextContent();
                NodeList value = stateParameter.getElementsByTagNameNS(EGRN_NAMESPACE, "Value");
                errDescription += ";" + value.item(0).getTextContent();
            }
        } else {
            nodeList = root.getElementsByTagName("code");
            if (nodeList.getLength() != 0) {
                errCode = nodeList.item(0).getTextContent();
            }
            nodeList = root.getElementsByTagName("description");
            if (nodeList.getLength() != 0) {
                errDescription = nodeList.item(0).getTextContent();
            }
            // Обрабатываем массив parameter
            nodeList = root.getElementsByTagName("parameter");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Element parameter = (Element) nodeList.item(i);
                NodeList key = parameter.getElementsByTagName("key");
                if (key.getLength() != 0) {
                    errDescription += "; " + key.item(0).getTextContent();
                }
                NodeList value = parameter.getElementsByTagName("value");
                if (value.getLength() != 0) {
                    errDescription += ":" + value.item(0).getTextContent();
                }
            }
        }
    }

    /**
     * Метод обрабатывает сообщение типа RejectMessage, извлекает из него коды и причины отказов,
     * которые сохраняет в элементы errCode и errDescription соответственно.
     */
    public void processRejectMessage() throws ResponseException {

        try {
            // Извлекаем данные об отказе в результирующий файл
            XMLTransformer.extractPrimaryContent(responseDOM, resultFile);
            // Собираем данные для лога
            Element root = responseDOM.getDocumentElement();
            NodeList nodeList = root.getElementsByTagName("rejects");
            // Согласно схеме этих элементов может быть много
            for (int i = 0; i < nodeList.getLength(); i++) {
                Element reject = (Element) nodeList.item(i);
                NodeList code = reject.getElementsByTagName("code");
                if (code.getLength() != 0) {
                    // Добавляем коды отказов через пробел
                    errCode += code.item(0).getTextContent() + " ";
                }
                NodeList description = reject.getElementsByTagName("description");
                if (description.getLength() != 0) {
                    // Добавляем описания отказов через пробел
                    errDescription += description.item(0).getTextContent() + " ";
                }
            }
            // Убираем лишние пробелы справа
            errCode = errCode.trim();
            errDescription = errDescription.trim();
        } catch (TransformerException e) {
            throw new ResponseException(String.format("Не удалось обработать ответ %s.", responseFile.getName()), new Exception());
        }
    }

    /**
     * Метод обрабатывает сообщение типа ErrorMessage, извлекает из него коды и причины ошибок,
     * которые сохраняет в элементы errCode и errDescription соответственно.
     * Namespace этих сообщений отличается от RejectMessage, поэтому понадобился отдельный метод.
     */
    public void processErrorMessage() {
        Element root = responseDOM.getDocumentElement();
        NodeList nodeList = root.getElementsByTagName("type");
        if (nodeList.getLength() != 0) {
            errSource = nodeList.item(0).getTextContent();
        }
        nodeList = root.getElementsByTagNameNS("urn://x-artefacts-smev-gov-ru/services/service-adapter/types/faults", "code");
        if (nodeList.getLength() != 0) {
            errCode = nodeList.item(0).getTextContent();
        }
        nodeList = root.getElementsByTagNameNS("urn://x-artefacts-smev-gov-ru/services/service-adapter/types/faults", "description");
        if (nodeList.getLength() != 0) {
            errDescription = nodeList.item(0).getTextContent();
        }
        nodeList = root.getElementsByTagName("details");
        if (nodeList.getLength() != 0) {
            errDescription += "\n" + nodeList.item(0).getTextContent();
        }
    }

    //================================== МЕТОДЫ ЛОГИРОВАНИЯ =================================

    public void logAnswer() throws SQLException {
        ResponseProcessor.dbConnection.logStatus(log_id, "ANSWERED");
    }

    public void logStatus() throws SQLException {
        // Определяем статус
        String description = "";
        String status;
        Element root = responseDOM.getDocumentElement();
        NodeList nodeList = root.getElementsByTagName("description");
        if (nodeList.getLength() != 0) {
            description = nodeList.item(0).getTextContent();
        }
        if (description.startsWith("Сообщение отправлено в СМЭВ")) {
            status = "SENT";
        } else if (description.startsWith("Сообщение помещено в очередь")) {
            status = "POSTED";
        } else if (description.startsWith("Сообщение доставлено")) {
            status = "DELIVERED";
        } else {
            status = "UNKNOWN";
        }
        switch (currentStatus) {
            // Если запрос находится в одном из финальных статусов, то новый статус игнорируется
            case "ANSWERED":
            case "REJECTED":
            case "FAILED":
                return;
            case "POSTED":
                // Доставленные в очередь поставщика запросы не меняют статус на SENT
                if (status.equals("SENT")) {
                    return;
                }
                break;
            case "DELIVERED":
                // Прочитанные поставщиком запросы не меняют статус на SENT или POSTED
                if ((status.equals("SENT")) || (status.equals("POSTED"))) {
                    return;
                }
                break;
        }
        ResponseProcessor.dbConnection.logStatus(log_id, status);
    }

    public void logBusinessStatus() throws SQLException {
        switch (currentStatus) {
            // Если запрос находится в одном из финальных статусов, то новый статус игнорируется
            case "ANSWERED":
            case "REJECTED":
            case "FAILED":
                return;
        }
        ResponseProcessor.dbConnection.logError(log_id, "BUSINESS", "", errCode, errDescription);
    }

    public void logReject() throws SQLException {
        switch (currentStatus) {
            // Запрос не может перейти в REJECTED из конечного состояния ANSWERED
            case "ANSWERED":
                return;
            default:
                ResponseProcessor.dbConnection.logError(log_id, "REJECTED", "", errCode, errDescription);
        }
    }

    public void logError() throws SQLException {
        switch (currentStatus) {
            // Запрос не может перейти в статус FAILED из конечных состояний ANSWERED или REJECTED
            case "ANSWERED":
            case "REJECTED":
                return;
            default:
                ResponseProcessor.dbConnection.logError(log_id, "FAILED", errSource, errCode, errDescription);
        }
    }

    //================================ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===============================

    /**
     * Getter для свойства responseType
     *
     * @return тип ответа
     */
    public String getType() {
        return responseType;
    }

    /**
     * Метод перемещает запрос, на который вернулся ответ ErrorMessage, из каталога requests/processed
     * в каталог requests/error, чтобы ИС УВ могла автоматически обработать такие запросы
     */
    public void moveRequest() {
        // Необходимо достать файл исходного запроса из каталога requests/processed
        Path source = ResponseProcessor.processedRequestsDir.resolve(requestFileName);
        // и переложить его в каталог requests/error
        Path target = ResponseProcessor.errorDir.resolve(requestFileName);
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error(String.format("Невозможно переместить файл запроса %s в каталог requests/error.", requestFileName));
        }
    }

    public void processFSSPRequest() throws ResponseException {
        Element root = responseDOM.getDocumentElement();
        NodeList documentNodes = root.getElementsByTagNameNS("urn://x-artifacts-fssp-ru/mvv/smev3/container/1.1.0", "Document");
        for (int i = 0; i < documentNodes.getLength(); i++) {
            processDocument(documentNodes.item(i));
        }
        //Отправляем ответ на запрос
        // Файл с именем UUID помещаем в каталог /opt/adapter/integration/files/FSOR01_3S/out
        File targetFile = ResponseProcessor.integrationOut.resolve(UUID.randomUUID().toString() + ".xml").toFile();
        File sourceFile = ResponseProcessor.inputDir.resolve(responseFile.getName()).toFile();
        try {
            XMLTransformer.answerFSSPRequest(sourceFile, targetFile);
        } catch (IOException | ParserConfigurationException | SAXException | TransformerException e) {
            throw new ResponseException("Ошибка формирования ответа на запрос ФССП.", new Exception());
        }
    }

    private void processDocument(Node documentNode) {
        XPath xPath = XPathFactory.newInstance().newXPath();
        XPathExpression exp;
        String query;
        String docKey;
        String attachmentFile;
        try {
            query = "./*[local-name()='IncomingDocKey']";
            exp = xPath.compile(query);
            docKey = (String) exp.evaluate(documentNode, XPathConstants.STRING);
            query = "./*[local-name()='AttachmentsBlock']/*[local-name()='AttachmentDescription']/*[local-name()='AttachmentFilename']";
            exp = xPath.compile(query);
            attachmentFile = (String) exp.evaluate(documentNode, XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            LOG.error(String.format("Не удалось преобразовать в ответ запрос ФССП %s", messageID));
            LOG.error(e.getMessage());
            return;
        }
        Path attachmentFilePath = Paths.get(ResponseProcessor.attachmentDir.toString(), clientID, attachmentFile);
        String requestFileName = ResponseProcessor.dbConnection.getFSSPRequestFileName(docKey);
        if (requestFileName == null) {
            requestFileName = clientID + ".xml";
        }
        String responseFileName = ResponseProcessor.outputDir.resolve(requestFileName.replace(".xml", ".zip")).toString();
        try {
            // Оставляем в запросе ФССП элемент, относящийся к указанному документу
            String response = XMLTransformer.splitFSSPRequest(responseDOM, docKey);
            // Пакуем xml-ответ и файл вложения в файл с именем responseFileName
            ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(responseFileName));
            ZipEntry xmlEntry = new ZipEntry(requestFileName);
            zout.putNextEntry(xmlEntry);
            zout.write(response.getBytes(StandardCharsets.UTF_8));
            zout.closeEntry();
            ZipEntry attachmentEntry = new ZipEntry(attachmentFilePath.getFileName().toString());
            zout.putNextEntry(attachmentEntry);
            FileInputStream fis = new FileInputStream(attachmentFilePath.toString());
            byte[] buffer = new byte[fis.available()];
            fis.read(buffer);
            zout.write(buffer);
            zout.closeEntry();
            fis.close();
            zout.close();
            // Меняем статус исходного запроса в таблице log
            Long logID = ResponseProcessor.dbConnection.getFSSPRequestLogId(docKey);
            if (logID != null) {
                ResponseProcessor.dbConnection.logStatus(logID, "ANSWERED");
            } else {
                LOG.error(String.format("Не удалось получить идентификатор лога для исходного запроса ФССП с идентификатором документа %ы", docKey));
            }
        } catch (TransformerException | IOException e) {
            LOG.error(String.format("Не удалось преобразовать в ответ запрос ФССП %s", clientID));
            LOG.error(e.getMessage());
        } catch (SQLException e) {
            LOG.error("Не удалось изменить статус запроса ФССП");
            LOG.error(e.getMessage());
        }
    }

    /**
     * PrimaryMessage из ответа ФССП обрабатывается как бизнес-статус запроса
     * В качестве значения errCode подставляется значение элемента ReceiptResult
     * В качестве значения errDescription подставляется значение элемента MessageText
     */
    public void processFSSPResponse() throws ResponseException {
        XPath xPath = XPathFactory.newInstance().newXPath();
        XPathExpression exp;
        String query;
        try {
            query = "//*[local-name()='ReceiptResult']";
            exp = xPath.compile(query);
            errCode = (String) exp.evaluate(responseDOM.getDocumentElement(), XPathConstants.STRING);
            query = "//*[local-name()='MessageText']";
            exp = xPath.compile(query);
            errDescription = (String) exp.evaluate(responseDOM.getDocumentElement(), XPathConstants.STRING);
            logBusinessStatus();
        } catch (XPathExpressionException | SQLException e) {
            throw new ResponseException(String.format("Не удалось обработать ответ %s.", responseFile.getName()), new Exception());
        }

    }
}
