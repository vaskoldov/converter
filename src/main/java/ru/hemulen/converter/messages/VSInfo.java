package ru.hemulen.converter.messages;

/**
 * Класс для хранения описания конкретного вида сведений.
 * Объекты этого класса объединяются в классе VSInfoArray.
 */
public class VSInfo {
    public String namespace;    // Пространство имен, определяющее вид сведений
    public String name;         // Наименование вида сведений для приложения визуализации
    public int timeout;         // Количество дней, через которое ожидание ответа на запрос теряет смысл
    public int dailyLimit;      // Максимальное количество запросов в сутки, которое допускае поставщик ВС
    public String priority;     // Приоритет вида сведений
    public String[] keywords;   // Массив XPath-выражений, которые идентифицируют запрос
}
