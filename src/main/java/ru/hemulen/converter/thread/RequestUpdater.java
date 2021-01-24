package ru.hemulen.converter.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hemulen.converter.db.H2;
import ru.hemulen.converter.db.PG;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;

public class RequestUpdater extends Thread {
    private static Logger LOG = LoggerFactory.getLogger(RequestUpdater.class.getName());
    private boolean isRunnable;
    private H2 h2Db;
    private PG pgDb;
    private long sleepTime;
    private Timestamp lastUpdateTime;

    public RequestUpdater(Properties props) {
        // Устанавливаем имя потока
        setName("RequestUpdaterThread");
        isRunnable = Boolean.parseBoolean(props.getProperty("REQUEST_UPDATER"));
        h2Db = new H2(props);
        LOG.info("Создано подключение к H2.");
        pgDb = new PG(props);
        LOG.info("Создано подключение к PostgreSQL.");
        sleepTime = Long.parseLong(props.getProperty("REQUEST_FREQ"));
        // Извлекаем из БД postgres время последнего обновления запросов
        try {
            lastUpdateTime = pgDb.getLastRequestTimestamp();
        } catch (SQLException e) {
            // Если по какой-то причине не удалось извлечь штамп времени из БД, то присваиваем хоть что-нибудь.
            lastUpdateTime = Timestamp.valueOf("2019-08-01 00:00:00.000000");
        }
        LOG.info("RequestUpdater инициализирован.");
    }

    @Override
    public void run() {
        while (isRunnable) {
            try {
                // Получаем из базы H2 все запросы, отправленные с момента последнего обновления
                ResultSet result = h2Db.getRequests(lastUpdateTime);
                if (result != null) {
                    // Обновляем в PHP-адаптере message_id, send_timestamp и status всех обработанных запросов
                    // updateRequests возвращает самое позднее время SEND_DATE в result
                    Timestamp lastCreateDate = pgDb.updateRequests(result);
                    if (lastCreateDate != null) {
                        lastUpdateTime = lastCreateDate;
                    }
                }
                if (result != null) {
                    result.close();
                }
                // Сохраняем на всякий случай время последнего обновления запросов в БД postgres
                pgDb.setLastRequestTimestamp(lastUpdateTime);
                // И засыпаем на определенное в параметре время
                sleep(sleepTime);
            } catch (Exception e) {
                LOG.error(e.getMessage());
            }
        }
    }
}
