package ru.hemulen.converter.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;
import java.util.Properties;

/**
 * Класс PG предназначен для установки соединения с базой PostgreSQL, в которой ведутся логи конвертера и
 * хранится некая служебная информация.
 * Все запросы к PostgreSQL выполняются методами класса.
 */
public class ConverterDB implements AutoCloseable {
    private static Logger LOG = LoggerFactory.getLogger(ConverterDB.class.getName());
    Connection connection = null;
    String schema;
    PreparedStatement requestsPS = null;
    PreparedStatement responsesPS = null;

    /**
     * Конструктор устанавливает соединение с БД, параметры которой описаны в Properties.
     * Кроме того, конструктор создает два PreparedStatement для обновления времени отправки/получения
     * запросов и ответов в таблице log.
     * @param props Параметры подключения к базе PostgreSQL.
     */
    public ConverterDB(Properties props) {
        String pgURL = "jdbc:postgresql://" + props.getProperty("PG_HOST") + ":" + props.getProperty("PG_PORT") + "/" + props.getProperty("PG_DB");
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            LOG.error("Не найден драйвер PostgreSQL!");
            LOG.error(e.getMessage());
            System.exit(2);
        }
        schema = props.getProperty("MNEMONIC");
        String requestsSQL = "UPDATE \"" + schema + "\".log SET \n" +
                "(message_id, send_timestamp, status)=(?, ?, CASE WHEN status = 'PREPARED' THEN 'SENT' ELSE status END)\n" +
                "WHERE client_id = ?";
        String responsesSQL = "UPDATE \"" + schema + "\".log SET \n" +
                "(response_id, response_timestamp)=(?,?)\n" +
                "WHERE client_id = ?";
        try {
            connection = DriverManager.getConnection(pgURL, props.getProperty("PG_USER"), props.getProperty("PG_PASS"));
            requestsPS = connection.prepareStatement(requestsSQL);
            responsesPS = connection.prepareStatement(responsesSQL);
        } catch (SQLException e) {
            LOG.error("Не удалось установить соединение с базой PostgreSQL!");
            LOG.error(e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Метод закрывает оба PreparedStatement, а затем закрывает соединение с БД PostgreSQL.
     */
    public void close() {
        try {
            requestsPS.close();
            responsesPS.close();
            connection.close();
        } catch (SQLException e) {
            LOG.error(e.getMessage());
        }
    }

    /**
     * Метод получает на вход список ClientID запросов, которые созданы в базе H2 с определенного момента.
     * Для каждой записи из resultSet метод обновляет message_id, send_timestamp и status записи запроса в таблице log.
     * Метод возвращает максимальное значение send_timestamp из resultSet.
     * @param resultSet Список запросов, созданных в базе H2, начиная с определенного момента времени.
     * @return Максимальное значение send_timestamp из resultSet.
     */
    public Timestamp updateRequests(ResultSet resultSet) { // resultSet закрывается в вызывающем методе
        Timestamp latestTimestamp = null;
        Timestamp currentTimestamp;
        if (resultSet == null) return latestTimestamp;
        try {
            while (resultSet.next()) {
                // 1 - clientID запроса
                // 2 - messageID запроса
                // 3 - время отправки запроса SENDING_DATE
                requestsPS.setString(1, resultSet.getString(2));
                requestsPS.setTimestamp(2, resultSet.getTimestamp(3));
                requestsPS.setString(3, resultSet.getString(1));
                requestsPS.executeUpdate();
                currentTimestamp= resultSet.getTimestamp(3);
                // Сохраняем самое позднее время отправки запроса из resultSet
                if (latestTimestamp == null) {
                    latestTimestamp = currentTimestamp;
                } else if (currentTimestamp.after(latestTimestamp)) {
                    latestTimestamp = currentTimestamp;
                }
            }

        } catch (SQLException | NullPointerException e) {
            LOG.error(e.getMessage());
        }
        return latestTimestamp;
    }

    /**
     * Метод получает на вход список ClientID ответов, которые созданы в базе адаптера с определенного момента.
     * Для каждой записи из resultSet метод обновляет response_id и response_timestamp записи соответствующего запроса в таблице log.
     * Метод возвращает максимальное значение response_timestamp из resultSet.
     * @param resultSet Список запросов, созданных в базе адаптера, начиная с определенного момента времени.
     * @return Максимальное значение send_timestamp из resultSet.
     */
    public Timestamp updateResponses(ResultSet resultSet) {
        Timestamp latestTimestamp = null;
        try {
            while (resultSet.next()) { // resultSet закрывается в вызывающем методе
                // 1 - clientID запроса
                // 2 - messageID ответа
                // 3 - время получения ответа
                responsesPS.setString(1, resultSet.getString(2));
                responsesPS.setTimestamp(2, resultSet.getTimestamp(3));
                responsesPS.setString(3, resultSet.getString(1));
                responsesPS.executeUpdate();
                latestTimestamp = resultSet.getTimestamp(3);
            }
        } catch (SQLException | NullPointerException e) {
            LOG.error(e.getMessage());
        }
        return latestTimestamp;
    }

    public Timestamp getLastRequestTimestamp() throws SQLException {
        String sql = String.format("SELECT  request_timestamp FROM \"%s\".timestamps;", schema);
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        resultSet.next();
        Timestamp result = resultSet.getTimestamp(1);
        resultSet.close();
        statement.close();
        return result;
    }

    public Timestamp getLastResponseTimestamp() throws SQLException {
        String sql = String.format("SELECT response_timestamp FROM \"%s\".timestamps;", schema);
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        resultSet.next();
        Timestamp result = resultSet.getTimestamp(1);
        resultSet.close();
        statement.close();
        return result;
    }

    public void setLastRequestTimestamp(Timestamp timestamp) throws SQLException {
        String sql = String.format("UPDATE \"%s\".timestamps SET request_timestamp = '%s';", schema, timestamp);
        Statement statement = connection.createStatement();
        statement.executeUpdate(sql);
        statement.close();
    }

    public void setLastResponseTimestamp(Timestamp timestamp) throws SQLException {
        String sql = String.format("UPDATE \"%s\".timestamps SET response_timestamp = '%s';", schema, timestamp);
        Statement statement = connection.createStatement();
        statement.executeUpdate(sql);
        statement.close();
    }

    public void logOverlimitRequest(String file_name, String vs_name, Integer msg_index, String keywords) throws SQLException {
        String sql = String.format("INSERT INTO \"%s\".log (log_id, file_name, receipt_timestamp, status, msg_index, vs_name, keywords) " +
                "VALUES (DEFAULT, '%s', DEFAULT, 'OVERLIMIT', '%d', '%s', '%s')", schema, file_name, msg_index, vs_name, keywords);
        Statement statement = connection.createStatement();
        statement.executeUpdate(sql);
        statement.close();
    }

    public void logRequest(String file_name,
                           String client_id,
                           String vs_name,
                           Date timeout,
                           Integer msg_index,
                           String keywords,
                           String documentKey) throws SQLException {
        String sql = String.format("INSERT INTO \"%s\".log (log_id, file_name, receipt_timestamp, client_id, status, timeout, msg_index, vs_name, keywords, document_key) " +
                "VALUES (DEFAULT, '%s', DEFAULT, '%s', 'PREPARED', '%s', %d, '%s', '%s', '%s')", schema, file_name, client_id, timeout, msg_index, vs_name, keywords, documentKey);
        Statement statement = connection.createStatement();
        statement.executeUpdate(sql);
        statement.close();
    }

    public ResultSet getCounter(Date date) throws SQLException {
        String sql = String.format("SELECT vs_namespace, msg_count FROM \"%s\".msg_counter WHERE session_date = '%s'", schema, date.toString());
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        return resultSet;
    }

    public void saveCounter(Map<String, Integer> counter, Date date) throws SQLException {
        String sql = String.format("INSERT INTO \"%s\".msg_counter (session_date, vs_namespace, msg_count) VALUES (?, ?, ?) ", schema);
        sql += "ON CONFLICT (session_date, vs_namespace) DO UPDATE SET msg_count = ?";
        PreparedStatement statement = connection.prepareStatement(sql);
        for (Map.Entry<String, Integer> entry : counter.entrySet()) {
            statement.setDate(1, date);
            statement.setString(2, entry.getKey());
            statement.setInt(3, entry.getValue());
            statement.setInt(4, entry.getValue());
            statement.executeUpdate();
        }
        statement.close();
    }

    public ResultSet getRequest(String clientID) throws SQLException {
        String sql = String.format("SELECT log_id, file_name, vs_name, status FROM \"%s\".log WHERE client_id = '%s'", schema, clientID);
        Statement statement = connection.createStatement();
        return statement.executeQuery(sql);
    }

    public ResultSet getRequestByMessageID(String messageID) throws SQLException {
        String sql = String.format("SELECT client_id FROM \"%s\".log WHERE message_id = '%s'", schema, messageID);
        Statement statement = connection.createStatement();
        return statement.executeQuery(sql);
    }

    public void logStatus(Long log_id, String status) throws SQLException {
        if (log_id == null) {
            return;
        }
        // Обновляем статус существующей записи запроса
        String sql = String.format("UPDATE \"%s\".log SET status = '%s', processing_timestamp = NOW() WHERE log_id = %d", schema, status, log_id);
        Statement statement = connection.createStatement();
        statement.executeUpdate(sql);
        statement.close();
    }

    public void logError(Long log_id, String status, String err_source, String err_code, String err_description) throws SQLException {
        // Для ответов ЕГРН необходимо сохранять предыдущее описание ошибки, потому что там может быть внутренний номер
        // документа в ЕГРН. Поэтому сначала читаем запись log_id, определяем вид сведений и если это ЕГРН, то конкатенируем
        // существующее описание ошибки с новым.
        String getLogSQL = String.format("SELECT vs_name, err_description FROM \"%s\".log WHERE log_id = %d", schema, log_id);
        Statement getLog = connection.createStatement();
        ResultSet rs = getLog.executeQuery(getLogSQL);
        String vsName = null;
        String oldErrorDescription = null;
        while (rs.next()) {
            vsName = rs.getString(1);
            oldErrorDescription = rs.getString(2);
        }
        if (vsName.equals("ЕГРН") && oldErrorDescription != null) {
            err_description = oldErrorDescription + "; " + err_description;
        }

        String sql = String.format("UPDATE \"%s\".log SET status = ?, err_source = ?, err_code = ?, " +
                "err_description = ?, processing_timestamp = NOW() WHERE log_id = ?", schema);
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, status);
        ps.setString(2, err_source);
        ps.setString(3, err_code);
        ps.setString(4, err_description);
        ps.setLong(5, log_id);
        ps.executeUpdate();
        ps.close();
    }

    public void fixTimeout(Date currentDate) throws SQLException {
        String sql = String.format("UPDATE \"%s\".log SET status = 'TIMEOUT' WHERE date(timeout) <= '%s' AND status IN ('PREPARED', 'QUEUE', 'SENT')", schema, currentDate.toString());
        Statement statement = connection.createStatement();
        statement.executeUpdate(sql);
        statement.close();
    }

    public void archiveLog(String sqlDate) throws SQLException {
        String sql = String.format("INSERT INTO \"%s\".log_archive\n" +
                "SELECT * FROM \"%s\".log WHERE receipt_timestamp < '%s';", schema, schema, sqlDate);
        Statement statement = connection.createStatement();
        statement.executeUpdate(sql);
        sql = String.format("DELETE FROM \"%s\".log WHERE receipt_timestamp < '%s';", schema, sqlDate);
        statement.executeUpdate(sql);
        sql = String.format("REFRESH MATERIALIZED VIEW \"%s\".full_log;", schema);
        statement.executeUpdate(sql);
        statement.close();
    }

    public String getFSSPRequestFileName(String docKey) {
        String sql = String.format("SELECT file_name FROM \"%s\".log WHERE document_key = '%s';", schema, docKey);
        try {
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(sql);
            rs.next();
            return rs.getString("file_name");
        } catch (SQLException e) {
            LOG.error(String.format("Не удалось найти входящий запрос ФССП для идентификатора документа %s", docKey));
            LOG.error(e.getMessage());
            return null;
        }
    }

    public Long getFSSPRequestLogId(String docKey) {
        String sql = String.format("SELECT log_id FROM \"%s\".log WHERE document_key = '%s';", schema, docKey);
        try {
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(sql);
            rs.next();
            return rs.getLong("log_id");
        } catch (SQLException e) {
            LOG.error(String.format("Не удалось найти входящий запрос ФССП для идентификатора документа %s", docKey));
            LOG.error(e.getMessage());
            return null;
        }
    }
}
