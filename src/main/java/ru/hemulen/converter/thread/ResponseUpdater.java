package ru.hemulen.converter.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hemulen.converter.db.Adapter13DB;
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
    private Adapter13DB adapter13DB;
    private PG pgDb;
    private long sleepTime;
    private Timestamp lastUpdateTime;

    public ResponseUpdater(Properties props) {
        // Устанавливаем имя потока
        setName("ResponseUpdaterThread");
        isRunnable = Boolean.parseBoolean(props.getProperty("RESPONSE_UPDATER"));
        h2Db = new H2(props);
        LOG.info("Создано подключение к БД адаптера.");
        adapter13DB = new Adapter13DB(props);
        LOG.info("Создано подключение к БД второго instance адаптера.");
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
                // Получаем из базы первого instance адаптера все полученные за время последнего обновления ответы
                ResultSet result = h2Db.getResponses(lastUpdateTime);
                // Получаем из базы второго instance адаптера все полученные за время последнего обновления ответы
                ResultSet result13 = adapter13DB.getResponses(lastUpdateTime);
                Timestamp latestDeliveryDate = null;
                if (result != null) {
                    latestDeliveryDate = pgDb.updateResponses(result);
                    result.close();
                }
                Timestamp latestDeliveryDate13 = null;
                if (result13 != null) {
                    latestDeliveryDate13 = pgDb.updateResponses(result13);
                    result13.close();
                }

                if (latestDeliveryDate != null && latestDeliveryDate13 != null) {
                    // Сохраняем самое раннее из двух RESPONSE_TIMESTAMP в базе конвертера
                    if (latestDeliveryDate.before(latestDeliveryDate13)) {
                        lastUpdateTime = latestDeliveryDate;
                    } else {
                        lastUpdateTime = latestDeliveryDate13;
                    }
                } else if (latestDeliveryDate != null) {
                    lastUpdateTime = latestDeliveryDate;
                } else if (latestDeliveryDate13 != null) {
                    lastUpdateTime = latestDeliveryDate13;
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
