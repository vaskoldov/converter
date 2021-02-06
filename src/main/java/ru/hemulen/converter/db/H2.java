package ru.hemulen.converter.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.Properties;

public class H2 implements AutoCloseable {
    private static Logger LOG = LoggerFactory.getLogger(H2.class.getName());
    Connection connection = null;

    public H2(Properties props) {
        String dbType = props.getProperty("DB_TYPE");
        switch (dbType) {
            case "H2":
                // Подключаемся к файлу БД H2
                String h2Path = props.getProperty("ADAPTER_PATH") + File.separator +
                        "data" + File.separator +
                        props.getProperty("VERSION") + File.separator
                        + "db" + File.separator + "adapter";
                String h2URL = "jdbc:h2:file:" + h2Path + ";IFEXISTS=TRUE;AUTO_SERVER=TRUE";
                try {
                    Class.forName("org.h2.Driver");
                } catch (ClassNotFoundException e) {
                    LOG.error("Не удалось найти JDBC-драйвер ru.hemulen.h2pgsql.db.H2");
                    LOG.error(e.getMessage());
                    System.exit(2);
                }
                try {
                    connection = DriverManager.getConnection(h2URL, "adapter", "p@$$w0rd");
                } catch (SQLException e) {
                    LOG.error("Не удалось установить соединение с базой ru.hemulen.h2pgsql.db.H2!");
                    LOG.error(e.getMessage());
                    System.exit(2);
                }
                break;
            case "PG":
                // Подключаемся к БД PostgreSQL
                String pgURL = "jdbc:postgresql://" + props.getProperty("DB_HOST") + ":" + props.getProperty("DB_PORT") + "/" + props.getProperty("DB_DB");
                try {
                    Class.forName("org.postgresql.Driver");
                    connection = DriverManager.getConnection(pgURL, props.getProperty("DB_USER"), props.getProperty("DB_PASS"));
                } catch (ClassNotFoundException e) {
                    LOG.error("Не найден драйвер PostgreSQL!");
                    LOG.error(e.getMessage());
                    System.exit(2);
                } catch (SQLException e) {
                    LOG.error("Не удалось установить соединение с базой PostgreSQL адаптера!");
                    LOG.error(e.getMessage());
                    System.exit(2);
                }
                break;
            default:
                LOG.error("Некорректный тип СУБД адаптера. Допустимые значения: H2 и PG.");
                System.exit(2);
        }
    }

    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.error(e.getMessage());
        }
    }

    public ResultSet getRequests(Timestamp timestamp) {
        try {
            String sql = "SELECT\n" +
                    "md.ID ,\n" +
                    "md.MESSAGE_ID ,\n" +
                    "md.SENDING_DATE \n" +
                    "FROM CORE.MESSAGE_METADATA md\n" +
                    "LEFT JOIN CORE.MESSAGE_STATE st ON md.ID = st.ID\n" +
                    "WHERE (1=1)\n" +
                    "AND md.MESSAGE_TYPE = 'REQUEST'\n" +
                    "AND md.CREATION_DATE >= '" + timestamp.toString() + "'\n" +
                    "ORDER BY md.CREATION_DATE";
            Statement stmt = connection.createStatement();
            return stmt.executeQuery(sql);
        } catch (SQLException e) {
            LOG.error(e.getMessage());
            return null;
        }
    }

    public ResultSet getResponses(Timestamp timestamp) {
        try {
            String sql = "SELECT \n" +
                    "md.REFERENCE_ID ,\n" +
                    "md.MESSAGE_ID ,\n" +
                    "md.DELIVERY_DATE \n" +
                    "FROM CORE.MESSAGE_METADATA md\n" +
                    "LEFT JOIN CORE.MESSAGE_CONTENT mc ON md.ID=mc.ID \n" +
                    "WHERE md.MESSAGE_TYPE = 'RESPONSE'\n" +
                    "AND mc.MODE <> 'STATUS' \n" +
                    "AND md.DELIVERY_DATE >= '" + timestamp + "'\n" +
                    "ORDER BY md.DELIVERY_DATE";
            Statement stmt = connection.createStatement();
            ResultSet result = stmt.executeQuery(sql);
            return result;
        } catch (SQLException e) {
            LOG.error(e.getMessage());
            return null;
        }
    }

    /**
     * Метод удаляет из базы данных все ответы, на которые получены финальные ответы ('REJECT', 'ERROR' или 'MESSAGE'),
     * а также все ответы, связанные с этими запросами (включая STATUS).
     */
    public void clearDB() {
        LOG.info("Начало очистки базы данных H2:");
        // Выбираем идентификаторы запросов, на которые ссылаются финальные ответы (MESSAGE, REJECT, ERROR) в REQ_TMP
        getWasteRequests();
        // Выбираем идентификаторы всех ответов на запросы в финальном статусе в RESP_TMP
        getWasteResponses();
        // Удаляем ответы и запросы из четырех таблиц схемы CORE
        deleteMessages();
        // Удаляем временные таблицы
        dropTemporaryTables();
        LOG.info("База данных H2 очищена.");
    }

    /**
     * Метод записывает идентификаторы запросов,
     * на которые получены финальные ответы ('REJECT', 'ERROR' или 'MESSAGE'),
     * во временную таблицу REQ_TMP
     */
    private void getWasteRequests() {
        try {
            String sql = "CREATE TABLE REQ_TMP AS \n" +
                    "SELECT MM.REFERENCE_ID\n" +
                    "FROM CORE.MESSAGE_METADATA MM\n" +
                    "LEFT JOIN CORE.MESSAGE_CONTENT MC ON MC.ID = MM.ID\n" +
                    "WHERE MC.MODE IN ('MESSAGE', 'REJECT', 'ERROR')\n" +
                    "AND MM.MESSAGE_TYPE = 'RESPONSE';";
            Statement stmt = connection.createStatement();
            stmt.executeUpdate(sql);
            LOG.info("Идентификаторы запросов в финальных статусах выбраны в таблицу REQ_TMP.");
        } catch (SQLException e) {
            LOG.error(e.getMessage());
        }
    }

    /**
     * Метод записывает идентификаторы всех ответов на запросы, находящихся в финальном статусе,
     * включая ответы типа STATUS, во временную таблицу RESP_TMP.
     * <p>
     * Должен вызываться после getWasteRequests, где создается таблица REQ_TMP, используемая в запросе.
     */
    private void getWasteResponses() {
        try {
            String sql = "CREATE TABLE RESP_TMP AS \n" +
                    "SELECT ID \n" +
                    "FROM CORE.MESSAGE_METADATA\n" +
                    "WHERE REFERENCE_ID IN \n" +
                    "(SELECT REFERENCE_ID FROM REQ_TMP);";
            Statement stmt = connection.createStatement();
            stmt.executeUpdate(sql);
            LOG.info("Идентификаторы ответов на запросы в финальных статусах выбраны в таблицу RESP_TMP.");
        } catch (SQLException e) {
            LOG.error(e.getMessage());
        }
    }

    /**
     * Метод удаляет все записи в базе данных, связанные с овтетами на запросы в финальных статусах
     * и с самими запросами в финальных статусах.
     */
    private void deleteMessages() {
        try {
            // Подготавливаем запросы:
            // - для удаления ответов
            String respAttachments = "DELETE FROM CORE.ATTACHMENT_METADATA WHERE MESSAGE_METADATA_ID IN (SELECT ID FROM RESP_TMP);";
            String respMessageContent = "DELETE FROM CORE.MESSAGE_CONTENT WHERE ID IN (SELECT ID FROM RESP_TMP);";
            String respMessageMetadata = "DELETE FROM CORE.MESSAGE_METADATA WHERE ID IN (SELECT ID FROM RESP_TMP);";
            String respMessageState = "DELETE FROM CORE.MESSAGE_STATE WHERE ID IN (SELECT ID FROM RESP_TMP);";
            // - для удаления запросов
            String reqAttachments = "DELETE FROM CORE.ATTACHMENT_METADATA WHERE MESSAGE_METADATA_ID IN (SELECT REFERENCE_ID FROM REQ_TMP);";
            String reqMessageContent = "DELETE FROM CORE.MESSAGE_CONTENT WHERE ID IN (SELECT REFERENCE_ID FROM REQ_TMP);";
            String reqMessageMetadata = "DELETE FROM CORE.MESSAGE_METADATA WHERE ID IN (SELECT REFERENCE_ID FROM REQ_TMP);";
            String reqMessageState = "DELETE FROM CORE.MESSAGE_STATE WHERE ID IN (SELECT REFERENCE_ID FROM REQ_TMP);";
            // Создаем запрос
            Statement statement = connection.createStatement();
            // Удаляем ответы из четырех таблиц
            statement.executeUpdate(respAttachments);
            LOG.info("Удалены ответы из ATTACHMENT_METADATA.");
            statement.executeUpdate(respMessageContent);
            LOG.info("Удалены ответы из MESSAGE_CONTENT.");
            statement.executeUpdate(respMessageMetadata);
            LOG.info("Удалены ответы из MESSAGE_METADATA.");
            statement.executeUpdate(respMessageState);
            LOG.info("Удалены ответы из MESSAGE_STATE.");
            // Удаляем запросы из четырех таблиц
            statement.executeUpdate(reqAttachments);
            LOG.info("Удалены запросы из ATTACHMENT_METADATA.");
            statement.executeUpdate(reqMessageContent);
            LOG.info("Удалены запросы из MESSAGE_CONTENT.");
            statement.executeUpdate(reqMessageMetadata);
            LOG.info("Удалены запросы из MESSAGE_METADATA.");
            statement.executeUpdate(reqMessageState);
            LOG.info("Удалены запросы из MESSAGE_STATE.");
        } catch (SQLException e) {
            LOG.error("Ошибка при удалении записей ответов и запросов.");
            LOG.error(e.getMessage());
        }
    }

    private void dropTemporaryTables() {
        try {
            String sql = "DROP TABLE REQ_TMP; DROP TABLE RESP_TMP;";
            Statement statement = connection.createStatement();
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            LOG.error("Ошибка при удалении временных таблиц.");
            LOG.error(e.getMessage());
        }
    }
}