package ru.hemulen.converter.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import ru.hemulen.crypto.exceptions.SignatureProcessingException;
import ru.hemulen.converter.exceptions.OverlimitException;
import ru.hemulen.converter.exceptions.ParsingException;
import ru.hemulen.converter.exceptions.RequestException;
import ru.hemulen.converter.exceptions.SignException;
import ru.hemulen.converter.thread.RequestProcessor;
import ru.hemulen.converter.utils.AbstractTools;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Date;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.UUID;

/**
 * Класс обрабатывает запросы ИС участника взаимодействия
 */
public class Request {
    private static final Logger LOG = LoggerFactory.getLogger(Request.class.getName());
    private File requestFile;               // Файл с запросом
    private Document requestDOM;            // Представление запроса в виде DOM-объекта
    private String requestNamespace;        // URI namespace вида сведений (используется для различения версий ВС ЕГРН)
    private String clientID;                // Клиентский идентификатор запроса
    private File resultFile;                // Файл с преобразованным в ClientMessage запросом
    private Document clientMessage;         // Представление clientMessage в виде DOM-объекта
    private VSInfo vsInfo;                  // Параметры вида сведений
    private Integer requestIndex;           // Порядковый номер запроса за сутки
    private Calendar timeoutDate;           // Дата, когда запрос перейдет в статус TIMEOUT
    private String keywords;                // Ключевые параметры запроса
    private String personalSign = "";       // Имя файла с подписью должностного лица
    private String attachmentFile = "";     // Имя файла вложения
    private String attachmentSign = "";     // Имя файла с подписью файла вложения
    private String documentKey;             // Идентификатор документа в запросе ФССП

    /**
     * Конструктор запроса
     *
     * @param requestFile Файл с исходным запросом
     * @throws RequestException   Исключение выбрасывается, если проблемы при преобразовании
     * @throws OverlimitException Исключение выбрасывается, если за текущие суткиотправлено слишком многозапросов этого ВС
     */
    public Request(File requestFile) throws RequestException, OverlimitException, ParsingException {
        this.requestFile = requestFile;
        // Парсим XML-файл в DOM-объект
        try {
            requestDOM = XMLTransformer.fileToDocument(requestFile);
        } catch (IOException e) {
            LOG.error(e.getMessage());
            throw new RequestException(String.format("Файловая ошибка с %s", requestFile.getName()), e);
        } catch (SAXException e) {
            LOG.error(e.getMessage());
            throw new ParsingException(String.format("Ошибка парсинга файла %s.", requestFile.getName()), e);
        }
        // Определяем вид сведений
        vsInfo = getVSInfo(requestDOM);
        if (vsInfo == null) {
            throw new RequestException(String.format("Неизвестный вид сведений в файле %s", requestFile), new Exception());
        }
        // Извлекаем ключевые параметры запроса
        try {
            keywords = XMLTransformer.getKeywords(requestDOM, vsInfo);
        } catch (XPathExpressionException e) {
            LOG.error(e.getMessage());
            // Продолжаем обработку запроса, но без ключевых слов
            keywords = "";
        }
        // Определяем порядковый номер запроса вида сведений за текущие сутки
        requestIndex = RequestProcessor.vsCounter.getIndex(vsInfo.namespace);
        if (requestIndex > vsInfo.dailyLimit) {
            // Фиксируем попытку отправки сверхлимитного запроса в БД
            try {
                RequestProcessor.dbConnection.logOverlimitRequest(requestFile.getName(), vsInfo.name, requestIndex, keywords);
            } catch (SQLException e) {
                LOG.error(e.getSQLState());
                throw new RequestException("Ошибка при обработке сверхлимитного запроса " + requestFile.getName(), new Exception());
            }
            // И выбрасываем исключение OverlimitException, чтобы перенести запрос в каталог overlimited и больше не обрабатывать сегодня
            throw new OverlimitException("Превышен дневной лимит " + vsInfo.name, new Exception());
        }
        // Если запрос не сверхлимитный, то рассчитываем крайнюю дату, когда должен быть получен ответ
        timeoutDate = (Calendar) RequestProcessor.currentDate.clone();
        timeoutDate.add(Calendar.DATE, vsInfo.timeout);

        // Формируем CLientID запроса
        clientID = UUID.randomUUID().toString();

    }

    public String getVSName() {
        if (vsInfo == null) {
            return "Unknown";
        }
        return vsInfo.name;
    }

    /**
     * Метод преобразует запрос в формат ClientMessage его в каталог с соответствующим приоритетом
     */
    public void process() throws RequestException {
        // Определяем каталог для сохранения результирующего файла в соответствии с приоритетом вида сведений
        Path resultDir = RequestProcessor.outputDir.resolve(vsInfo.priority);
        if (Files.notExists(resultDir)) {
            // Если такого каталога нет, то создаем его
            try {
                Files.createDirectory(resultDir);
            } catch (IOException e) {
                LOG.error(e.getMessage());
                throw new RequestException("Не удалось создать каталог " + resultDir, e);
            }
        }
        // Формируем имя результирующего файла
        resultFile = resultDir.resolve(requestFile.toPath().getFileName()).toFile();
        // Выполняем преобразование
        try {
            // Читаем подпись вложения в строку
            String attachmentSignString = "";
            if (!attachmentSign.isEmpty()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(attachmentSign))));
                attachmentSignString = reader.readLine();
                reader.close();
            }
            XMLTransformer.createClientMessage(requestDOM, resultFile, clientID, personalSign, attachmentFile, attachmentSignString);
        } catch (TransformerException | IOException e) {
            LOG.error(e.getMessage());
            throw new RequestException("Не удалось преобразовать в ClientMessage запрос " + resultFile.getName(), new Exception());
        }
    }

    /**
     * Метод преобразует запрос в ClientMessage и помещает его в каталог исходящих сообщений адаптера по версии схем СМЭВ 1.3
     * Механизм приоритетных очередей при этом не используется
     */
    public void processTo13() throws RequestException {
        Path outputDir = RequestProcessor.outputDir13;
        resultFile = outputDir.resolve(requestFile.toPath().getFileName()).toFile();
        // Выполняем преобразование
        try {
            // Читаем подпись вложения в строку
            String attachmentSignString = "";
            if (!attachmentSign.isEmpty()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(attachmentSign))));
                attachmentSignString = reader.readLine();
                reader.close();
            }
            XMLTransformer.createClientMessage(requestDOM, resultFile, clientID, personalSign, attachmentFile, attachmentSignString);
        } catch (TransformerException | IOException e) {
            LOG.error(e.getMessage());
            throw new RequestException("Не удалось преобразовать в ClientMessage запрос " + resultFile.getName(), new Exception());
        }
    }

    public void log() {
        try {
            Date timeoutSQL = new Date(timeoutDate.getTimeInMillis());
            RequestProcessor.dbConnection.logRequest(requestFile.getName(), clientID, getVSName(), timeoutSQL, requestIndex, keywords, documentKey);
        } catch (SQLException e) {
            LOG.error(e.getMessage());
        }
    }

    /**
     * Метод возвращает информацию вида сведений
     *
     * @param requestDOM XML-документ с видом сведений
     * @return Информация вида сведений, если оно зарегистрировано, или null в противном случае
     */
    private static VSInfo getVSInfo(Document requestDOM) {
        Element root = requestDOM.getDocumentElement();
        String namespace = root.getNamespaceURI();
        if (RequestProcessor.vsInfoArray.isNamespaceRegistered(namespace)) {
            return RequestProcessor.vsInfoArray.getVSInfo(namespace);
        } else {
            return null;
        }
    }

    public void signFNSRequest() throws SignException {
        Element elementToSign = requestDOM.getDocumentElement();
        try {
            // Подписываем запрос
            Element signElement = RequestProcessor.fnsSigner.signXMLDSigDetached(elementToSign, null);
            // Сохраняем запрос в файл .sig
            String signFileName = requestFile.getName().substring(0, requestFile.getName().lastIndexOf('.')) + ".sig";
            Path signFile = RequestProcessor.signDir.resolve(signFileName);
            AbstractTools.writeOutput(signElement, signFile.toFile());
            // Присваиваем полный путь к файлу с подписью члену personalSign
            personalSign = signFile.toString();
        } catch (SignatureProcessingException | IOException e) {
            RequestProcessor.isFNSSignRegistered = false;
            LOG.error(e.getMessage());
            throw new SignException(String.format("Не удалось подписать файл %s.", requestFile.getName()), e);
        }
    }

    public void signMVDRequest() throws SignException {
        Element elementToSign = requestDOM.getDocumentElement();
        try {
            // Подписываем запрос
            Element signElement = RequestProcessor.egrnSigner.signXMLDSigDetached(elementToSign, null);
            // Сохраняем запрос в файл .sig
            String signFileName = requestFile.getName().substring(0, requestFile.getName().lastIndexOf('.')) + ".sig";
            Path signFile = RequestProcessor.signDir.resolve(signFileName);
            AbstractTools.writeOutput(signElement, signFile.toFile());
            // Присваиваем полный путь к файлу с подписью члену personalSign
            personalSign = signFile.toString();
        } catch (SignatureProcessingException | IOException e) {
            RequestProcessor.isEGRNSignRegistered = false;
            LOG.error(e.getMessage());
            throw new SignException(String.format("Не удалось подписать файл %s.", requestFile.getName()), e);
        }
    }

    /**
     * Метод на основании заявления (statement) в ЕГРН создает файлы с техническим описанием и запрос
     *
     * @param vsName По этому параметру определяется версия ВС ЕГРН, а также версия технического описания, которые
     *               должны быть созданы данным методом. Допустимые на 24.08.24 значения - "ЕГРН" и "ЕГРН_26" (см. VSInfoArray.xml)
     * @throws RequestException ошибка при обоработке запроса
     */
    public void generateEGRNRequest(String vsName) throws RequestException, ParserConfigurationException, SAXException, IOException, SignException {
        // Копируем файл в каталог для подписания.
        // Перемещать нельзя, т.к. в каталоге requests должен остаться исходный файл на случай исключений,
        // после которых он перемещается в каталог failed
        Path targetPath = RequestProcessor.signDir.resolve(requestFile.toPath().getFileName());
        try {
            Files.copy(requestFile.toPath(), targetPath);
        } catch (IOException e) {
            // Оставляем запрос в каталоге requests до следующего прохода RequestProcessor'а (возможно не закончилось копирование файла)
            return;
        }
        File statement = targetPath.toFile(); // Заявление уже в новом каталоге лежит
        // На основании заявления создаем техническое описание
        File techDesc;
        try {
            techDesc = XMLTransformer.createTechDesc(statement, vsName);
            LOG.info("***TEST*** Сформировано техническое описание к запросу " + requestFile.getName());
        } catch (ParserConfigurationException | SAXException | IOException | TransformerException e) {
            LOG.error(e.getMessage());
            throw new RequestException(String.format("Не удалось преобразовать заявление ЕГРН %s в техническое описание.", statement.getName()), e);
        }
        // Заявление и техническое описание подписываются PKCS7
        File statementSign;
        File techDescSign;
        try {
            statementSign = RequestProcessor.egrnSigner.signPKCS7Detached(statement);
            LOG.info("***TEST*** Подписан запрос EGRNRequest " + requestFile.getName());
            techDescSign = RequestProcessor.egrnSigner.signPKCS7Detached(techDesc);
            LOG.info("***TEST*** Подписано техническое описание к запросу " + requestFile.getName());
        } catch (IOException e) {
            LOG.error(e.getMessage());
            throw new RequestException(String.format("Файловая проблема с элементами вложения к заявлению ЕГРН %s.", statement.getName()), e);
        } catch (SignatureProcessingException e) {
            RequestProcessor.isEGRNSignRegistered = false;
            LOG.error(e.getMessage());
            throw new SignException(String.format("Не удалось подписать элементы вложения к заявлению ЕГРН %s.", statement.getName()), e);
        }
        // Заявление и техническое описание упаковываются в архив zip c именем, равным clientID запроса
        // с префиксом "a" (имя файла в схеме СМЭВ должно начинаться с буквы)
        String archiveName = "a" + clientID;
        // Создаем новый каталог в base-storage
        Path attachmentFolder = RequestProcessor.attachmentDir.resolve(archiveName);
        Path attachmentFile = attachmentFolder.resolve(archiveName + ".zip");
        try {
            Files.createDirectory(attachmentFolder);
            AbstractTools.zipElements(new File[]{statement, statementSign, techDesc, techDescSign}, attachmentFile.toString());
            LOG.info("***TEST*** Сформирован архив вложения к запросу " + requestFile.getName());
        } catch (IOException e) {
            LOG.error(e.getMessage());
            throw new RequestException(String.format("Не удалось заархивировать файл вложения для заявления ЕГРН %s.", statement.getName()), e);
        }
        // Архив тоже подписывается PKCS7
        File attachmentSign;
        try {
            attachmentSign = RequestProcessor.egrnSigner.signPKCS7Detached(attachmentFile.toFile());
            LOG.info("***TEST*** Подписан архив вложения к запросу " + requestFile.getName());
        } catch (IOException e) {
            LOG.error(e.getMessage());
            throw new RequestException(String.format("Файловая проблема с вложением для заявления ЕГРН %s.", statement.getName()), e);
        } catch (SignatureProcessingException e) {
            RequestProcessor.isEGRNSignRegistered = false;
            LOG.error(e.getMessage());
            throw new SignException(String.format("Не удалось подписать файл вложения для заявления ЕГРН %s.", statement.getName()), e);
        }
        // Создаем основной запрос для последующего формирования ClientMessage
        // При этом исходный файл с заявлением перезаписывается файлом с основным запросом
        File mainRequestFile;
        try {
            mainRequestFile = XMLTransformer.createMainRequest(this.requestFile, this.clientID, vsName);
            LOG.info("***TEST*** Сформирован ClientMessage с запросом " + requestFile.getName());
            // Перезаписываем исходный файл с заявлением файлом с основным запросом
            Files.move(mainRequestFile.toPath(), this.requestFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (ParserConfigurationException | SAXException | IOException | TransformerException |
                 XPathExpressionException e) {
            LOG.error(e.getMessage());
            throw new RequestException(String.format("Не удалось сформировать запрос Request для заявления ЕГРН %s.", this.requestFile.getName()), e);
        }

        // Присваиваем имена сформированных файлов членам класса Request, кроме requestFile,
        // который был заменен сгенерированным файлом
        this.requestDOM = AbstractTools.fileToElement(this.requestFile).getOwnerDocument();
        this.attachmentFile = attachmentFile.toString();
        this.attachmentSign = attachmentSign.toString();
    }

    /**
     * Метод на основании полученного из ИС УВ запроса генерирует запрос ClientMessage с заполненной секцией RoutingInformation
     */
    public void generateESIARequest() throws RequestException {
        // Сгенерированный ClientMessage сохраняем в каталоге integration/files адаптера, работающего по версии 1.3 схем СМЭВ.
        // Запросы персональных данных пользователей ЕСИА не помещаются в очереди на отправку по приоритетам, поскольку
        // отправляются через отдельный instance адаптера.
        Path targetPath = RequestProcessor.outputDir13;

        // Формируем имя результирующего файла
        resultFile = targetPath.resolve(requestFile.toPath().getFileName()).toFile();

        // Выполняем преобразование файла ИС УВ в ClientMessage
        try {
            XMLTransformer.createESIAClientMessage(requestDOM, resultFile, clientID);
        } catch (TransformerException e) {
            LOG.error(e.getMessage());
            throw new RequestException("Не удалось преобразовать в ClientMessage запрос " + resultFile.getName(), new Exception());
        }
    }

    /**
     * Метод на основании полученного из ИС УВ файла вложения генерирует запрос ClientMessage,
     * подписывает вложение подписью ЭП-СП, упаковывает вложение в архив, который также подписывается ЭП-СП
     */
    public void generateFSSPRequest() throws RequestException, ParserConfigurationException, SAXException, IOException, SignException {
        // Копируем файл вложения в каталог для подписания.
        // Перемещать нельзя, т.к. в каталоге requests должен остаться исходный файл на случай исключений,
        // после которых он перемещается в каталог failed
        Path targetPath = RequestProcessor.signDir.resolve(requestFile.toPath().getFileName());
        try {
            Files.copy(requestFile.toPath(), targetPath);
        } catch (IOException e) {
            // Оставляем запрос в каталоге requests до следующего прохода RequestProcessor'а (возможно не закончилось копирование файла)
            return;
        }
        File statement = targetPath.toFile(); // Файл вложения лежит уже в новом каталоге
        File statementSign;
        try {
            statementSign = RequestProcessor.egrnSigner.signPKCS7Detached(statement);
        } catch (IOException e) {
            LOG.error(e.getMessage());
            throw new RequestException(String.format("Файловая проблема с вложением ФССП %s.", statement.getName()), e);
        } catch (SignatureProcessingException e) {
            RequestProcessor.isEGRNSignRegistered = false;
            LOG.error(e.getMessage());
            throw new SignException(String.format("Не удалось подписать вложение ФССП %s.", statement.getName()), e);
        }
        // Вложение и его подпись упаковываются в архив zip c именем, равным clientID запроса
        // с префиксом "a" (имя файла в схеме СМЭВ должно начинаться с буквы)
        String archiveName = this.requestFile.getName();
        // Заменяем расширение .xml на .zip
        archiveName = archiveName.replaceFirst(".xml", ".zip");
        // Создаем новый каталог в base-storage
        Path attachmentFolder = RequestProcessor.attachmentDir.resolve("a" + clientID);
        Path attachmentFile = attachmentFolder.resolve(archiveName);
        try {
            Files.createDirectory(attachmentFolder);
            AbstractTools.zipElements(new File[]{statement, statementSign}, attachmentFile.toString());
        } catch (IOException e) {
            LOG.error(e.getMessage());
            throw new RequestException(String.format("Не удалось заархивировать файл вложения ФССП %s.", statement.getName()), e);
        }
        // Архив тоже подписывается PKCS7
        File attachmentSign;
        try {
            attachmentSign = RequestProcessor.egrnSigner.signPKCS7Detached(attachmentFile.toFile());
        } catch (IOException e) {
            LOG.error(e.getMessage());
            throw new RequestException(String.format("Файловая проблема с архивом вложений ФССП %s.", statement.getName()), e);
        } catch (SignatureProcessingException e) {
            RequestProcessor.isEGRNSignRegistered = false;
            LOG.error(e.getMessage());
            throw new SignException(String.format("Не удалось подписать архив вложений ФССП %s.", statement.getName()), e);
        }
        // Создаем основной запрос для последующего формирования ClientMessage
        // При этом исходный файл с заявлением перезаписывается файлом с основным запросом
        File mainRequestFile;
        try {
            mainRequestFile = XMLTransformer.createFSSPRequest(this.requestFile, attachmentFile.toFile(), this.clientID);
            // Перезаписываем исходный файл с заявлением файлом с основным запросом
            Files.move(mainRequestFile.toPath(), this.requestFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (ParserConfigurationException | SAXException | IOException | TransformerException |
                 XPathExpressionException e) {
            LOG.error(e.getMessage());
            throw new RequestException(String.format("Не удалось сформировать запрос Request для заявления ЕГРН %s.", this.requestFile.getName()), e);
        }

        // Присваиваем имена сформированных файлов членам класса Request, кроме requestFile,
        // который был заменен сгенерированным файлом
        requestDOM = AbstractTools.fileToElement(this.requestFile).getOwnerDocument();
        this.attachmentFile = attachmentFile.toString();
        this.attachmentSign = attachmentSign.toString();
        try {
            documentKey = XMLTransformer.getFSSPDocumentKey(this.requestDOM.getDocumentElement());
        } catch (XPathExpressionException e) {
            LOG.error(String.format("Не удалось получить ExternalId из запроса ФССП %s", this.requestFile.getName()));
            LOG.error(e.getMessage());
        }
    }

    public File getRequestFile() {
        return requestFile;
    }
}
