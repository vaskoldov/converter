package ru.hemulen.converter.messages;

import ru.hemulen.converter.thread.RequestProcessor;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Класс предназначен для подсчета отправленных за сутки запросов и запрета отправки запроса при превышении лимита.
 * Счетчики запросов сохраняются в базу данных и считываются из базы данных
 */
public class VSCounter {
    // Единственный экземпляр набора счетчиков видов сведений
    public static Map<String, Integer> counter = new HashMap<>();

    /** Читаем счетчики из базы данных
     * @param actualDate Дата, на которую актуальны счетчики
     */
    public void loadVSCounter(Date actualDate) throws SQLException {
        // Очищаем текущие значения
        counter.clear();
        // Читаем ResultSet из базы данных
        ResultSet resultSet = RequestProcessor.dbConnection.getCounter(actualDate);
        // Обрабатываем ResultSet
        while (resultSet.next()) {
            counter.put(resultSet.getString(1), resultSet.getInt(2));
        }
        resultSet.close();
    }

    /**
     * Сохраняем счетчики в базу данных
     * @param actualDate Дата, на которую актуальны счетчики
     */
    public void saveVSCounter(Date actualDate) throws SQLException {
        RequestProcessor.dbConnection.saveCounter(counter, actualDate);
    }

    /**
     * Метод увеличивает текущее значение счетчика для вида сведений на единицу и возвращает новое значение
     * Если счетчика для вида сведений нет, то он создается.
     * @param namespace Пространство имен вида сведений, счетчик которого надо получить
     * @return Счетчик запросов вида сведений, включающий текущий запрос
     */
    public Integer getIndex(String namespace) {
        if (!counter.containsKey(namespace)) {
            // Если этот ВС сегодня еще не отправлялся, то добавляем элемент в таблицу счетчиков
            counter.put(namespace, 0);
        }
        // Увеличиваем счетчик на 1
        Integer newIndex = counter.get(namespace) + 1;
        counter.put(namespace, newIndex);
        return counter.get(namespace);
    }

    public void clear() {
        counter.clear();
    }
}
