package ru.hemulen.converter.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import ru.hemulen.converter.exceptions.ParsingException;
import ru.hemulen.converter.exceptions.ResponseException;
import ru.hemulen.converter.thread.ResponseProcessor;

import javax.xml.transform.TransformerException;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Response {
    private static Logger LOG = LoggerFactory.getLogger(Response.class.getName());
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

    public Response(File responseFile) throws ResponseException, ParsingException {
        this.responseFile = responseFile;
        this.errCode = "";
        this.errDescription = "";
        try {
            responseDOM = XMLTransformer.fileToDocument(responseFile);
            Element root = responseDOM.getDocumentElement();
            // Считываем clientID ответа
            NodeList clientIDNodes = root.getElementsByTagName("clientId");
            if (clientIDNodes.getLength() != 0) {
                clientID = clientIDNodes.item(0).getTextContent();
            }
            // Считываем messageID ответа (начиная с версии 3.1.8 каталоги с вложениями именуются по MessageId ответа)
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
    public void processPrimaryMessage() throws ResponseException, ParsingException {
        try {
            // Извлекаем секцию под MessagePrimaryContent в отдельный файл
            XMLTransformer.extractPrimaryContent(responseDOM, resultFile);
            // Проверяем наличие вложений в ответе
            Element root = responseDOM.getDocumentElement();
            NodeList attachmentHeaders = root.getElementsByTagName("AttachmentHeader");
            if (attachmentHeaders.getLength() != 0) {
                // Определяем массив файлов вложений
                List<File> attachmentFiles = new LinkedList<>();
                // Формируем архив из XML ответа и файлов вложений, который сохраняется с именем исходного запроса и расширением ZIP
                for (int i = 0; i < attachmentHeaders.getLength(); i++) {
                    Element attachmentHeader = (Element) attachmentHeaders.item(i);
                    // Имя каталога с файлом - это элемент Id из AttachmentHeader
                    Path attachmentPath = null;
                    NodeList attachmentPathNodes = attachmentHeader.getElementsByTagName("Id");
                    if (attachmentPathNodes.getLength() != 0) {
                        attachmentPath = Paths.get(attachmentPathNodes.item(0).getTextContent());
                    }
                    // Получаем имя подкаталога вложений из элемента clientID - тоже новшество 4.0, но работает
                    // не для всех ВС, почему-то. Вложения ЕИСУКС создаются в подкаталогах, ЕГРН - нет.
                    Path attachmentSubfolder = Paths.get(clientID);
                    // Получаем имя файла
                    Path attachmentFile = null;
                    NodeList attachmentFileNodes = attachmentHeader.getElementsByTagName("filePath");
                    if (attachmentFileNodes.getLength() != 0) {
                        attachmentFile = Paths.get(attachmentFileNodes.item(0).getTextContent());
                    }
                    // Проверяем наличие файла без подкаталога
                    Path attachmentFilePath = ResponseProcessor.attachmentDir.resolve(attachmentPath).resolve(attachmentFile);
                    if (!attachmentFilePath.toFile().exists()) {
                        // Если файл не существует, тогда добавляем подкаталог
                        attachmentFilePath = ResponseProcessor.attachmentDir.resolve(attachmentPath).resolve(attachmentSubfolder).resolve(attachmentFile);
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
                        throw new ResponseException(String.format("Отсутствует файл вложения %s к запросу %s.", attachmentPath.toString(), requestFileName), new Exception());
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
                        entry = new ZipEntry(entryFile.getName());
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
                    // Удаляем XML-файл с ответом
                    Files.delete(resultFile.toPath());
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
        NodeList nodeList = root.getElementsByTagName("code");
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
            Element parameter = (Element) nodeList.item(0);
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

}
