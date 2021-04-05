package ru.hemulen.converter.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hemulen.converter.db.PG;
import ru.hemulen.converter.exceptions.AttachmentException;
import ru.hemulen.converter.exceptions.ParsingException;
import ru.hemulen.converter.exceptions.ResponseException;
import ru.hemulen.converter.messages.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Класс выполняет сканирование каталога с ответами адаптера, определенного в параметре ADAPTER_PATH конфигурации.
 * Каждый обнаруженный в каталоге файл передается на обработку, в ходе которой из файла извлекается бизнес-часть ответа
 * и сохраняется в каталоге ИС УВ, определенном в параметре EXCHANGE_PATH конфигурации.
 * Если обработка завершилась успешно, то файл перемещается в подкаталог processed.
 * Если обработка завершилась с ошибкой, то файл перемещается в подкаталог failed, а ошибка записывается в лог.
 */
public class ResponseProcessor extends Thread {
    private static Logger LOG = LoggerFactory.getLogger(ResponseProcessor.class.getName());
    public static PG dbConnection;  // Отдельное подключение к БД PostgreSQL для обработчика ответов
    private Boolean isRunnable;
    private long sleepTime;         // Время задержки перед следующим опросом каталога, если он оказывается пуст
    private Path inputDir;          // Каталог, в который адаптер помещает ответы СМЭВ (IN)
    public static Path attachmentDir;      // Каталог, в который адаптер помещает файлы вложений
    public static Path outputDir;   // Каталог, из которого ответы забирает ИС УВ (responses)
    private Path processedDir;      // Каталог, в который складываются обработанные ответы
    private Path failedDir;         // Каталог, в который складываются ответы, при обработке которых возникло исключение
    private Path requestsDir;       // Каталог с исходными запросами
    public static Path processedRequestsDir;    // Каталог с отправленными запросами
    public static Path errorDir;    // Каталог, в который перемещаются запросы, на которые из СМЭВ пришла ошибка

    public ResponseProcessor(Properties props) {
        // Устанавливаем имя потока
        setName("ResponseProcessorThread");
        // Запуск процесса настраивается в конфигурации
        isRunnable = Boolean.parseBoolean(props.getProperty("RESPONSE_PROCESSOR"));
        // Подключаемся к базе данных
        dbConnection = new PG(props);
        LOG.info("Создано подключение к PostgreSQL.");
        // Частота опроса каталога IN
        sleepTime = Long.parseLong(props.getProperty("RESPONSE_FREQ"));
        // Настраиваем каталоги
        if (!Files.exists(Paths.get(props.getProperty("ADAPTER_PATH")))) {
            // Если в параметре передан некорректный каталог, то прекращаем работу приложения
            LOG.error("В настройках указан некорректный каталог СМЭВ-адаптера. Работа завершается.");
            System.exit(1);
        }
        inputDir = Paths.get(props.getProperty("ADAPTER_PATH"), "integration", "files", props.getProperty("MNEMONIC"), "in");
        //attachmentDir = Paths.get(props.getProperty("ADAPTER_PATH"), "data", "base-storage", props.getProperty("MNEMONIC")); - с версии 3.1.8 это уже не так
        attachmentDir = Paths.get(props.getProperty("ADAPTER_PATH"), "data", props.getProperty("VERSION"), "base-storage", "in");
        outputDir = Paths.get(props.getProperty("EXCHANGE_PATH"), "responses");
        requestsDir = Paths.get(props.getProperty("EXCHANGE_PATH"), "requests");
        processedRequestsDir = requestsDir.resolve("processed");
        if (!Files.exists(outputDir)) {
            // Если каталога с ответами нет, то создадим его
            try {
                Files.createDirectory(outputDir);
            } catch (IOException e) {
                LOG.error(e.getMessage());
            }
        }
        processedDir = inputDir.resolve("processed");
        if (!Files.exists(processedDir)) {
            try {
                Files.createDirectory(processedDir);
            } catch (IOException e) {
                LOG.error(e.getMessage());
            }
        }
        failedDir = inputDir.resolve("failed");
        if (!Files.exists(failedDir)) {
            try {
                Files.createDirectory(failedDir);
            } catch (IOException e) {
                LOG.error(e.getMessage());
            }
        }
        errorDir = requestsDir.resolve("error");
        if (!Files.exists(errorDir)) {
            try {
                Files.createDirectory(errorDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        LOG.info("Настроены рабочие каталоги.");
        LOG.info("ResponseProcessor инициализирован.");
    }

    @Override
    public void run() {
        //LOG.info(String.format("Параметр запуска потока isRunnable: %s.", Boolean.toString(isRunnable)));
        while (isRunnable) {
            // Читаем файлы в каталоге ответов
            File[] files = inputDir.toFile().listFiles();
            //LOG.info(String.format("Количество элементов (включая подкаталоги) в IN: %d.", files.length));
            if (files.length <= 2) {
                // Каталог пуст, не считая подкаталогов processed и failed - спим какое-то время и снова опрашиваем каталог
                try {
                    LOG.info("Ответы отсутствуют.");
                    sleep(sleepTime);
                } catch (InterruptedException e) {
                    LOG.error(e.getMessage());
                }
                continue;
            }
            int filesNum = 0; // Счетчик ответов
            for (File file : files) {
                if (!file.isDirectory() && isFileAccessible(file)) {
                    // Создаем объект Response
                    try {
                        Response response = new Response(file);
                        // Определяем тип ответа
                        String responseType = response.getType();
                        switch (responseType) {
                            case "PrimaryMessage":
                                // Извлекаем из конверта бизнес-сообщение и сохраняем его в файл в каталоге responses
                                response.processPrimaryMessage();
                                // Логируем получение ответа в базе данных
                                response.logAnswer();
                                break;
                            case "StatusMessage":
                                // Логируем статус запроса в базе данных
                                response.logStatus();
                                break;
                            case "BusinessStatus":
                                // Считываем бизнес-статус
                                response.processBusinessStatus();
                                // Логируем статус BUSINESS
                                response.logBusinessStatus();
                                break;
                            case "ErrorMessage":
                                // Выбираем из сообщения источник, код и описание ошибки
                                response.processErrorMessage();
                                // Логируем статус FAILED и описание ошибки в базе данных
                                response.logError();
                                // Перемещаем соответствующий запрос в каталог error
                                response.moveRequest();
                                break;
                            case "RejectMessage":
                                // Выбираем из сообщения информацию об отказе
                                response.processRejectMessage();
                                // Логируем статус REJECTED и описание причин отказа в базе данных
                                response.logReject();
                                break;
                            default:
                                // Не удалось определить статус ответа
                                // Выкидываем исключение и помещаем ответ в failed для последующего разбора
                                throw new ResponseException("Неизвестный тип ответа " + file.getName(), new Exception());
                        }
                        // Перемещаем обработанный ответ в каталог processed
                        Path target = processedDir.resolve(file.toPath().getFileName());
                        Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                        filesNum++; // Увеличиваем счетчик ответов
                    } catch (ResponseException | SQLException e) {
                        LOG.error(e.getMessage());
                        LOG.info(String.format("Не удалось обработать ответ %s.", file.getName()));
                        // Перемещаем файл с ответом, вызвавший исключение, в каталог failed
                        Path target = failedDir.resolve(file.toPath().getFileName());
                        try {
                            Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException ex) {
                            LOG.error(e.getMessage());
                            LOG.info(String.format("Не удалось переместить файл %s в каталог failed.", file.getName()));
                        }
                    } catch (IOException e) {
                        LOG.error(e.getMessage());
                        LOG.info(String.format("Произошла ошибка ввода-вывода при обработке ответа %s.", file.getName()));
                    } catch (ParsingException e) {
                        // Это исключение возникает в случае, если пытались парсить не до конца скопированный файл
                        LOG.info(String.format("Не удалось распарсить ответ %s. Ответ будет обработан в следующем цикле.", file.getName()));
                        // Больше ничего не делаем и оставляем файл ответа в каталоге IN до следующего цикла.
                    } catch (AttachmentException e) {
                        // Это исключение возникает, когда адаптер не успел обработать (сохранить) вложения, на которые
                        // ссылается обрабатываемый ответ.
                        LOG.info(String.format("Нулевой размер файла с архивом, полученном при обработке ответа %s. Ответ будет обработан в следующем цикле.", file.getName()));
                        // Больше ничего не делаем и оставляем файл ответа в каталоге IN до следующего цикла.
                    }
                }
            }
            LOG.info(String.format("Обработано %d ответов из %d.", filesNum, files.length-2));
        }
    }

    /**
     * Метод пытается открыть файл на чтение, и если это не получается, то возвращается false.
     * Иначе возвращается true.
     * @param file Проверяемый файл
     * @return true, если файл получилось открыть на чтение, и false в противном случае.
     */
    private boolean isFileAccessible(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            fis.close();
            return true;
        } catch (IOException e) {
            LOG.info(String.format("Файл %s недоступен для чтения.", file.getName()));
            return false;
        }
    }

}
