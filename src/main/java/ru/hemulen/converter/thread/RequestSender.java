package ru.hemulen.converter.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hemulen.converter.messages.VSInfoArray;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;

public class RequestSender extends Thread {
    private static Logger LOG = LoggerFactory.getLogger(RequestSender.class.getName());
    private Boolean isRunnable;     // Признак включения потока
    private Path preparedDir;       // Каталог, в котором лежат подкаталоги по приоритетам отправляемых запросов
    private Path outputDir;         // Каталог OUT адаптера

    public RequestSender(Properties props) {
        // Устанавливаем имя потока
        setName("RequestSenderThread");
        // Запуск процесса настраивается в конфигурации
        isRunnable = Boolean.parseBoolean(props.getProperty("REQUEST_SENDER"));
        preparedDir = Paths.get(props.getProperty("EXCHANGE_PATH")).resolve("prepared");
        outputDir = Paths.get(props.getProperty("INTEGRATION_OUT"));
        LOG.info("Инициализированы каталог подготовленных запросов и каталог отправки адаптера.");
        LOG.info("RequestSender инициализирован.");
    }

    public void run() {
        while (isRunnable) {
            // Общее количество приоритетов определяется при загрузке файла VSInfoArray.xml в классе RequestProcessor.
            // Это глобальная статическая переменная, которая при старте RequestSender может быть еще равна нулю.
            // В этом случае следующий цикл не выполнится, но будет продолжаться внешний, пока maxPriority не станет больше единицы.
            for (int i = 1; i <= VSInfoArray.maxPriority; i++) {
                // Обработка очередного приоритета начинается только тогда, когда каталог out пуст
                // Поэтому ждем, когда он освободится, а потом отправляем очередной приоритет
                while (!isOutputEmpty()) {
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        LOG.error(e.getMessage());
                    }
                }
                sendPriority(i);
            }
        }
    }

    private void sendPriority(int priority) {
        Path priorityDir = preparedDir.resolve(Integer.toString(priority));
        if (!priorityDir.toFile().exists()) {
            return; // Нет каталога - нечего обрабатывать
        }
        File[] files = priorityDir.toFile().listFiles();
        if (files.length == 0) {
            return; // Каталог есть, но он пустой
        }
        // Создаем хранилище для проблемных файлов
        List<File> deniedFiles = new LinkedList<>();
        // Обрабатываем текущий список файлов
        for (File file : files) {
            Path source = file.toPath();
            Path target = outputDir.resolve(file.toPath().getFileName());
            try {
                Files.move(source, target);
                SetPermissions(target); // Явно прописываем права доступа, чтобы адаптер смог прочитать наш запрос
            } catch (IOException e) {
                // Собираем в кучку файлы, которые не удалось переместить с первого раза
                deniedFiles.add(file);
            }
        }
        // Теперь обрабатываем проблемные файлы
        if (!deniedFiles.isEmpty()) {
            for (File file : deniedFiles) {
                Path source = file.toPath();
                Path target = outputDir.resolve(file.toPath().getFileName());
                try {
                    Files.move(source, target);
                } catch (IOException e) {
                    LOG.info(String.format("Не удалось переместить файл %s из %s в %s.", file.getName(), file.getParent(), outputDir.toString()));
                    // Файл остается в текущем каталоге до следующего прохода цикла в RequestSender.run()
                }
            }
            deniedFiles.clear();
        }
        LOG.info(String.format("Отправлены запросы %d приоритета.", priority));
    }

    private boolean isOutputEmpty() {
        File[] files = outputDir.toFile().listFiles();
        // В каталоге out еще два подкаталога - sent и error
        return files.length <= 2;
    }

    /**
     * Метод изменяет права доступа к файлу запроса, помещаемому в каталог адаптера
     */
    private Path SetPermissions(Path target) throws IOException {
        Set<PosixFilePermission> perms =
                EnumSet.of(OWNER_READ,
                        OWNER_WRITE,
                        OWNER_EXECUTE,
                        GROUP_READ,
                        GROUP_WRITE,
                        GROUP_EXECUTE,
                        OTHERS_READ,
                        OTHERS_WRITE,
                        OTHERS_EXECUTE);
        return Files.setPosixFilePermissions(target, perms);
    }
}
