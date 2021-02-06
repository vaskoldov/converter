package ru.hemulen.converter.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hemulen.converter.db.H2;
import ru.hemulen.converter.db.PG;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;

public class ResponseUpdater extends Thread {
    private static Logger LOG = LoggerFactory.getLogger(ResponseUpdater.class.getName());
    private Boolean isRunnable;
    private H2 h2Db;
    private PG pgDb;
    private long sleepTime;
    private Timestamp lastUpdateTime;

    public ResponseUpdater(Properties props) {
        // Устанавливаем имя потока
        setName("ResponseUpdaterThread");
        isRunnable = Boolean.parseBoolean(props.getProperty("RESPONSE_UPDATER"));
        h2Db = new H2(props);
        LOG.info("Создано подключение к БД адаптера.");
        pgDb = new PG(props);
        LOG.info("Создано подключение к PostgreSQL.");
        sleepTime = Long.parseLong(props.getProperty("RESPONSE_FREQ"));
        // Извлекаем из БД postgres время последнего обновления ответов
        try {
            lastUpdateTime = pgDb.getLastResponseTimestamp();
        } catch (SQLException e) {
            // Если по какой-то причине не удалось извлечь штамп времени из БД, то присваиваем хоть что-нибудь.
            lastUpdateTime = Timestamp.valueOf("2019-08-01 00:00:00.000000");
        }
        LOG.info("ResponseUpdater инициализирован.");
    }

    @Override
    public void run() {
        while (isRunnable) {
            try {
                // Получаем из СМЭВ-адаптера все полученные за время последнего обновления ответы
                ResultSet result = h2Db.getResponses(lastUpdateTime);
                if (result != null) {
                    // Обновляем записи запросов в PHP-адаптере
                    Timestamp latestDeliveryDate = pgDb.updateResponses(result);
                    if (latestDeliveryDate != null) {
                        lastUpdateTime = latestDeliveryDate;
                    }
                }
                if (result != null) {
                    result.close();
                }
                // Сохраняем на всякий случай время последнего обновления ответов в БД postgres
                pgDb.setLastResponseTimestamp(lastUpdateTime);
                // И засыпаем на определенное в параметре время
                sleep(sleepTime);

            } catch (Exception e) {
                LOG.error(e.getMessage());
            }
        }
    }
}
