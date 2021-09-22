package ru.hemulen.converter.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import ru.hemulen.crypto.DigitalSignatureFactory;
import ru.hemulen.converter.db.PG;
import ru.hemulen.converter.exceptions.OverlimitException;
import ru.hemulen.converter.exceptions.ParsingException;
import ru.hemulen.converter.exceptions.RequestException;
import ru.hemulen.converter.exceptions.SignException;
import ru.hemulen.converter.messages.Request;
import ru.hemulen.converter.messages.VSCounter;
import ru.hemulen.converter.messages.VSInfoArray;
import ru.hemulen.converter.signer.EGRNSigner;
import ru.hemulen.converter.signer.FNSSigner;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Класс выполняет сканирование каталога, определенного в параметре EXCHANGE_PATH конфигурации.
 * Каждый обнаруженный в каталоге файл передается на обработку.
 * Если обработка завершилась успешно, то файл перемещается в подкаталог processed.
 * Если обработка завершилась с ошибкой, то файл перемещается в подкаталог failed, а ошибка записывается в лог.
 * Если превышен суточный лимит отправки запросов, то файл перемещается в подкаталог overlimit.
 */
public class RequestProcessor extends Thread {
    private static Logger LOG = LoggerFactory.getLogger(RequestProcessor.class.getName());
    private Properties props;
    private Boolean isRunnable;
    private long sleepTime;                 // Время задержки перед следующим опросом каталога, если он оказывается пуст
    public static Calendar currentDate;     // Вспомогательная переменная для определения начала нового дня
    public static PG dbConnection;          // Подключение к БД PostgreSQL со счетчиками, логами и прочим
    public static Path inputDir;            // Каталог, откуда забираются запросы ИС УВ на обработку
    public static Path processedDir;        // Каталог, куда складываются обработанные запросы ИС УВ
    public static Path failedDir;           // Каталог, куда складываются запросы ИС УВ, при обработке которых возникло исключение
    public static Path overlimitDir;        // Каталог, куда складываются запросы ИС УВ, чей суточный лимит на отправку исчерпан
    public static Path outputDir;           // Каталог, куда складываются запросы СМЭВ-адаптера (используется в Request, поэтому public
    public static Path outputDir13;         // Каталог, куда складываются запросы для instance СМЭВ-адаптера, работающего с версией схем 1.3 СМЭВ
    public static Path attachmentDir;       // Каталог, куда складываются файлы вложений к запросам СМЭВ-адаптера
    public static Path signDir;             // Каталог, в который временно помещается файл с подписью XMLDSig или файлы вложений для подписи PKCS7
    public static VSInfoArray vsInfoArray;  // Класс с описанием всех обрабатываемых видов сведений
    public static VSCounter vsCounter;      // Класс со счетчиком всех отправленных за сутки сообщений каждого ВС
    public static FNSSigner fnsSigner;      // Подписыватель XMLDSig ВС ФНС
    public static EGRNSigner egrnSigner;    // Подписыватель PKCS7 ВС ЕГРН
    public static boolean isFNSSignRegistered;    // Признак успешной регистрации подписи ФНС
    public static boolean isEGRNSignRegistered;   // Признак успешной регистрации подписи ЕГРН

    /**
     * Конструктор проверяет и создает при необходимости нужные каталоги
     *
     * @param props Параметры приложения, считанные из файла config.ini где-то за пределами RequestProcessor
     */
    public RequestProcessor(Properties props) {
        // Сохраняем properties в член класса - они понадобятся при восстановлении подписей в методе run
        this.props = props;
        // Устанавливаем имя потока
        setName("RequestProcessorThread");
        // Устанавливаем подключение к БД
        dbConnection = new PG(props);
        LOG.info("Создано подключение к PostgreSQL.");

        // Устанавливаем текущую дату
        currentDate = Calendar.getInstance();
        // Преобразуем дату в SQL-дату
        Date currentSQLDate = new Date(currentDate.getTimeInMillis());
        // Читаем значения счетчиков ВС на эту дату
        vsCounter = new VSCounter();
        try {
            vsCounter.loadVSCounter(currentSQLDate);
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
        LOG.info("Загружены счетчики видов сведений.");

        // Запуск процесса настраивается в конфигурации
        isRunnable = Boolean.parseBoolean(props.getProperty("REQUEST_PROCESSOR"));

        // Обрабатываем параметр каталога СМЭВ-адаптера и все связанные с ним каталоги.
        // Для СМЭВ-адаптера никакие каталоги автоматически не создаются - если чего-то не хватает,
        // то пусть разбираются вне приложения, а оно просто завершает работу
        if (!Files.exists(Paths.get(props.getProperty("ADAPTER_PATH")))) {
            // Если в параметре передан некорректный каталог, то прекращаем работу приложения
            LOG.error("В настройках указан некорректный каталог СМЭВ-адаптера. Работа завершается.");
            System.exit(1);
        }
        outputDir = Paths.get(props.getProperty("EXCHANGE_PATH"), "prepared");
        if (!Files.exists(outputDir)) {
            try {
                Files.createDirectory(outputDir);
            } catch (IOException e) {
                LOG.error("Не удалось создать каталог для обработанных запросов", e);
            }
            System.exit(1);
        }
        // Запросы персональных данных пользователей ЕСИА помещаются сразу в выходной каталог второго instance адаптера, минуя очереди по приоритетам
        outputDir13 = Paths.get(props.getProperty("ADAPTER_1_3_PATH"), "integration", "files", props.getProperty("MNEMONIC"), "out");

        attachmentDir = Paths.get(props.getProperty("ADAPTER_PATH"), "local-storage");
        if (!Files.exists(attachmentDir)) {
            try {
                Files.createDirectories(attachmentDir);
            } catch (IOException e) {
                LOG.error(String.format("Не удалось создать каталог %s. Приложение продолжит работу, но запросы с вложениями отправляться не будут.", attachmentDir.toString()));
            }
        }

        // Обрабатываем параметр каталога для файлового обмена с ИС участника взаимодействия
        // Вот тут уже можем что-то создать автоматически, если чего-то не хватает
        if (!Files.exists(Paths.get(props.getProperty("EXCHANGE_PATH")))) {
            // Если в параметре передан некорректный каталог, то прекращаем работу приложения
            LOG.error("В настройках указан некорректный каталог для файлового обмена. Работа завершается.");
            System.exit(1);
        }
        inputDir = Paths.get(props.getProperty("EXCHANGE_PATH"), "requests");
        if (!Files.exists(inputDir)) {
            // Если каталога с запросами нет, то создадим его
            try {
                Files.createDirectory(inputDir);
            } catch (IOException e) {
                LOG.error(e.getMessage());
            }
        }
        processedDir = inputDir.resolve("processed");
        failedDir = inputDir.resolve("failed");
        overlimitDir = inputDir.resolve("overlimit");
        signDir = inputDir.resolve("sign");
        if (!Files.exists(processedDir)) {
            // Создаем каталог для обработанных запросов, если его нет
            try {
                Files.createDirectory(processedDir);
            } catch (IOException e) {
                LOG.error(e.getMessage());
            }
        }
        if (!Files.exists(failedDir)) {
            // Создаем каталог для кривых запросов, если его нет
            try {
                Files.createDirectory(failedDir);
            } catch (IOException e) {
                LOG.error(e.getMessage());
            }
        }
        if (!Files.exists(overlimitDir)) {
            // Создаем каталог для сверхлимитных запросов, если его нет
            try {
                Files.createDirectory(overlimitDir);
            } catch (IOException e) {
                LOG.error(e.getMessage());
            }
        }
        if (!Files.exists(signDir)) {
            // Создаем каталог для подписей, если его нет
            try {
                Files.createDirectory(signDir);
            } catch (IOException e) {
                LOG.error(e.getMessage());
            }
        }
        LOG.info("Обработаны настройки каталогов.");

        // Формируем описание видов сведений
        try {
            vsInfoArray = new VSInfoArray();
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
        LOG.info("Загружен массив с описанием видов сведений");

        // Инициализируем фабрику
        DigitalSignatureFactory.init(props);
        // Создаем объекты "подписывателей" запросов в ФНС и в ЕГРН
        isFNSSignRegistered = DigitalSignatureFactory.getFNSProcessorState();
        if (isFNSSignRegistered) {
            try {
                fnsSigner = new FNSSigner(props.getProperty("FNS_SIGN_ALIAS"), props.getProperty("FNS_SIGN_PASSWORD"));
                isFNSSignRegistered = true;
                LOG.info("Зарегистрирована подпись ФНС.");
            } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | CertificateException e) {
                isFNSSignRegistered = false;
                LOG.error("Не удалось зарегистрировать подпись ФНС.");
                LOG.error(e.getMessage());
            }
        }
        isEGRNSignRegistered = DigitalSignatureFactory.getEGRNProcessorState();
        if (isEGRNSignRegistered) {
            try {
                egrnSigner = new EGRNSigner(props.getProperty("EGRN_SIGN_ALIAS"), props.getProperty("EGRN_SIGN_PASSWORD"));
                isEGRNSignRegistered = true;
                LOG.info("Зарегистрирована подпись ЕГРН.");
            } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | CertificateException e) {
                isEGRNSignRegistered = false;
                LOG.error("Не удалось зарегистрировать подпись ЕГРН.");
                LOG.error(e.getMessage());
            }
        }

        // Наконец читаем время задержки, на которое процесс засыпает, если при очередном проходе в каталоге ничего не оказывается
        sleepTime = Long.parseLong(props.getProperty("REQUEST_FREQ"));
        LOG.info("Установлено время задержки обработки запросов.");
        LOG.info("RequestProcessor инициализирован.");
    }

    @Override
    public void run() {
        //LOG.info(String.format("Параметр запуска потока isRunnable: %s.", Boolean.toString(isRunnable)));
        while (isRunnable) {
            // Проверяем переход суток, чтобы сбросить счетчики отправленных запросов,
            // очистить базу данных H2 и перенести overlimited запросы в каталог requests
            checkNewDay();
            // Читаем файлы в рабочем каталоге
            File[] files = inputDir.toFile().listFiles();
            if (files.length <= 5) {
                // Если каталог пуст (в рабочем каталоге есть еще подкаталоги processed, error, failed, overlimit и sign, поэтому такая проверка),
                // то засыпаем на какое-то время и начинаем цикл while заново.
                try {
                    LOG.info("Запросы отсутствуют.");
                    sleep(sleepTime);
                    continue;
                } catch (InterruptedException e) {
                    LOG.error(e.getMessage());
                }
            }
            int filesNum = 0; // Счетчик успешно обработанных запросов
            for (File file : files) {
                if (!file.isDirectory() && isFileAccessible(file)) {
                    try {
                        // Создаем объект Request
                        Request request = new Request(file);
                        String vsName = request.getVSName();
                        switch (vsName) {
                            case "Unknown":
                                throw new RequestException("Неизвестный вид сведений " + file.getName(), new Exception());
                            case "2-НДФЛ":
                            case "Доходы ФЛ НА":
                                if (isFNSSignRegistered) {
                                    request.signFNSRequest();
                                    request.process();
                                    request.log();
                                    clearSignDir();
                                    break;
                                } else {
                                    throw new SignException(String.format("Подпись ФНС не инициализирована. Файл %s не обработан.", file.getName()), new Exception());
                                }
                            case "Судимость":
                                if (isEGRNSignRegistered) {
                                    request.signMVDRequest();
                                    request.process();
                                    request.log();
                                    clearSignDir();
                                } else {
                                    throw new SignException(String.format("Подпись ЕГРН не инициализирована. Файл %s не обработан.", file.getName()), new Exception());
                                }
                                break;
                            case "ЕГРН":
                                if (isEGRNSignRegistered) {
                                    try {
                                        // Создаем техническое описание и подписываем его вместе с заявлением,
                                        // архивируем все с подписями в файл вложения, подписываем файл вложения,
                                        // создаем файл запроса.
                                        // При этом request меняет значение члена requestFile на файл с запросом в ЕГРН
                                        request.generateEGRNRequest();
                                        // Первоначальный file в generateEGRNRequest заменяется на сгенерированный файл.
                                        // Поэтому его нужно восстановить.
                                        file = request.getRequestFile();
                                    } catch (IOException e) {
                                        throw new RequestException(String.format("Не удалось переместить заявление ЕГРН %s из каталога requests.", file.getName()), new Exception());
                                    } catch (ParserConfigurationException | SAXException e) {
                                        throw new RequestException(String.format("Не удалось обработать заявление ЕГРН %s.", file.getName()), new Exception());
                                    }
                                    // Присваиваем текущему файлу вновь созданный файл запроса в ЕГРН
                                    request.process();
                                    request.log();
                                    clearSignDir();
                                    break;
                                } else {
                                    throw new SignException(String.format("Подпись ЕГРН не инициализирована. Файл %s не обработан.", file.getName()), new Exception());
                                }
                            case "Персональные данные пользователя ЕСИА":
                                request.generateESIARequest();
                                request.log();
                                break;
                            default:
                                // Преобразуем запрос в ClientMessage
                                request.process();
                                // Логируем результат обработки запроса в БД
                                request.log();
                        }
                        // Если не выкинуто исключение, то переносим файл в каталог processed
                        // Перезаписываем существующий файл, потому что ИС УВ может посылать одинаковые файлы в разные дни
                        Path target = processedDir.resolve(file.toPath().getFileName());
                        try {
                            Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                            filesNum++; // Увеличиваем счетчик успешно обработанных файлов
                        } catch (IOException e) {
                            LOG.error("Не получается переместить файл " + file.toString());
                        }
                    } catch (RequestException e) {
                        // Логируем исключение и переносим файл в каталог failed
                        LOG.error(e.getMessage());
                        Path target = failedDir.resolve(file.toPath().getFileName());
                        try {
                            Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException ex) {
                            LOG.error(String.format("Не удалось переместить файл %s в каталог failed", file.toString()));
                        }
                        // Очищаем каталог sign на всякий случай
                        clearSignDir();
                    } catch (OverlimitException e) {
                        // Переносим файл в каталог overlimit
                        Path target = overlimitDir.resolve(file.toPath().getFileName());
                        try {
                            Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException ex) {
                            LOG.error(String.format("Не удалось переместить файл %s в каталог overlimit", file.toString()));
                        }
                    } catch (SignException e) {
                        // Пытаемся заново инициализировать подписи
                        if (!isFNSSignRegistered) {
                            try {
                                String fnsSignAlias = props.getProperty("FNS_SIGN_ALIAS");
                                String fnsSignPassword = props.getProperty("FNS_SIGN_PASSWORD");
                                if (!fnsSignAlias.isEmpty() && !fnsSignPassword.isEmpty()) {
                                    fnsSigner = new FNSSigner(fnsSignAlias, fnsSignPassword);
                                    isFNSSignRegistered = true;
                                    LOG.info("Зарегистрирована подпись ФНС.");
                                } else {
                                    LOG.error("В файле конфигурации не заданы псевдоним или пароль подписи ФНС.");
                                }
                            } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | CertificateException ex) {
                                isFNSSignRegistered = false;
                                LOG.error("Не удалось зарегистрировать подпись ФНС.");
                                LOG.error(ex.getMessage());
                            }
                        }
                        if (!isEGRNSignRegistered) {
                            try {
                                String egrnSignAlias = props.getProperty("EGRN_SIGN_ALIAS");
                                String egrnSignPassword = props.getProperty("EGRN_SIGN_PASSWORD");
                                if (!egrnSignAlias.isEmpty() && !egrnSignPassword.isEmpty()) {
                                    egrnSigner = new EGRNSigner(egrnSignAlias, egrnSignPassword);
                                    isEGRNSignRegistered = true;
                                    LOG.info("Зарегистрирована подпись ЕГРН.");
                                } else {
                                    LOG.error("В файле конфигурации не заданы псевдоним или пароль подписи ЕГРН.");
                                }
                            } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | CertificateException ex) {
                                isEGRNSignRegistered = false;
                                LOG.error("Не удалось зарегистрировать подпись ЕГРН.");
                                LOG.error(ex.getMessage());
                            }
                        }

                        // Файл, на котором было выброшено исключение, оставляется в папке requests
                        // и будет обработан при восстановлении подписей
                    } catch (ParsingException e) {
                        // Эта ошибка возникает при попытке обработать файл, копирование которого не завершено.
                        // Поэтому здесь ничего не делаем, оставляя файл в requests для повторной обработки.
                    }
                }
            }
            LOG.info(String.format("Обработано %d запросов из %d.", filesNum, files.length - 5));
            // После обработки всех файлов сохраняем счетчики в базе данных
            try {
                vsCounter.saveVSCounter(new Date(currentDate.getTimeInMillis()));
            } catch (SQLException e) {
                LOG.error(e.getSQLState());
            }
        }
    }

    /**
     * Метод проверяет, не наступил ли новый день.
     * Если наступил, то счетчики запросов в базе данных на текущий день обнуляются,
     * а сверхлимитные запросы предыдущего дня переносятся из каталога overlimited в каталог requests.
     * Кроме того, запросы, для которых наступила timeout-дата переводятся в статус TIMEOUT
     */
    private void checkNewDay() {
        // Берем сию секунду
        Calendar justMomentDate = Calendar.getInstance();
        // И проверяем, что день изменился
        if (currentDate.get(Calendar.DAY_OF_YEAR) != justMomentDate.get(Calendar.DAY_OF_YEAR)) {
            LOG.info("Смена текущей даты.");
            // Переводим протухшие запросы в статус TIMEOUT
            java.sql.Date sqlDate = new java.sql.Date(justMomentDate.getTimeInMillis());
            try {
                dbConnection.fixTimeout(sqlDate);
            } catch (SQLException e) {
                LOG.error("Ошибка при обработке TIMEOUT запросов.");
                LOG.error(e.getMessage());
            }
            LOG.info("Обработаны timeout в логе.");
            // Записи log старше месяца переносятся в log_archive
            Calendar monthAgo = Calendar.getInstance();
            monthAgo.add(GregorianCalendar.MONTH, -1);
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
            String sqlMonthAgo = df.format(monthAgo.getTimeInMillis());
            try {
                dbConnection.archiveLog(sqlMonthAgo);
            } catch (SQLException e) {
                LOG.error("Ошибка при переносе записей из log в log_archive.");
                LOG.error(e.getMessage());
            }
            LOG.info("Записи log перенесены в log_archive.");
            try {
                // Если день поменялся, то:
                // текущие счетчики сохраняются в базе данных
                vsCounter.saveVSCounter(new Date(currentDate.getTimeInMillis()));
                // счетчики обнуляются
                vsCounter.clear();
            } catch (Exception e) {
                LOG.error("Ошибка при сохранении или очистке счетчиков запросов.");
                LOG.error(e.getMessage());
            }
            LOG.info("Счетчики ВС обнулены.");

            // Файлы с запросами переносятся из каталога overlimited в рабочий каталог
            File[] files = overlimitDir.toFile().listFiles();
            for (File file : files) {
                Path target = inputDir.resolve(file.toPath().getFileName());
                try {
                    Files.move(file.toPath(), target);
                } catch (IOException e) {
                    LOG.error("Ошибка при обработке OVERLIMITED запросов.");
                    LOG.error(e.getMessage());
                }
            }
            LOG.info("OVERLIMIT запросы перенесены в рабочий каталог.");

            // И меняем текущую дату на сию секунду
            currentDate = justMomentDate;
            LOG.info("Обработка смены даты закончена.");
        }
    }

    /**
     * Метод очищает каталог sign после обработки запросов с подписями или после исключения RequestException
     */
    public static void clearSignDir() {
        File[] files = signDir.toFile().listFiles();
        if (files.length == 0) {
            return;
        }
        // Хранилище для проблемных файлов
        List<File> deniedFiles = new LinkedList<>();
        for (File file : files) {
            // Пытаемся удалить текущий файл
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                // В случае неудачи (файл чем-то занят, например) помещаем его в отдельный список
                deniedFiles.add(file);
            }
        }
        if (!deniedFiles.isEmpty()) {
            // Еще раз пытаемся удалить файлы из проблемного списка
            for (File file : deniedFiles) {
                try {
                    Files.delete(file.toPath());
                } catch (IOException e) {
                    LOG.info(String.format("Не удалось удалить файл %s из каталога %s.", file.getName(), file.getParent()));
                    // Оставляем файл в каталоге до следующего раза, когда будет вызван этот метод
                }
            }
            deniedFiles.clear();
        }
    }

    /**
     * Метод пытается открыть файл на чтение, и если это не получается, то возвращается false.
     * Иначе возвращается true.
     *
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
