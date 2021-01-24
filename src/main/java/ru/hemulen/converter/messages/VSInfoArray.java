package ru.hemulen.converter.messages;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Класс для хранения и поиска информации обо всех видах сведений, с которыми работает приложение
 */
public class VSInfoArray {
    private final String xmlFileName = "src/main/resources/VSInfoArray.xml";
    private Map<String, VSInfo> vsInfoSet;
    public static int maxPriority = 0;


    public VSInfoArray() throws ParserConfigurationException, IOException, SAXException {
        // Инициализируем HashMap
        vsInfoSet = new HashMap<>();
        // Читаем xml-файл с описанием видов сведений
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document xmlDOM = builder.parse(new File(xmlFileName));
        // Обходим все элементы vsInfoEntry и наполняем набор vsInfoSet
        NodeList vsInfoEntries = xmlDOM.getElementsByTagName("vsInfoEntry");
        for (int i=0; i<vsInfoEntries.getLength(); i++) {
            // Обрабатываем очередной элемент из множества найденных
            Element vsInfoEntry = (Element) vsInfoEntries.item(i);
            // Создаем объект VSInfo, который затем добавим в набор
            VSInfo vsInfo = new VSInfo();
            // Заполняем объект значениями из xml-файла
            vsInfo.namespace = vsInfoEntry.getElementsByTagName("namespace").item(0).getTextContent();
            vsInfo.name = vsInfoEntry.getElementsByTagName("name").item(0).getTextContent();
            vsInfo.timeout = Integer.parseInt(vsInfoEntry.getElementsByTagName("timeout").item(0).getTextContent());
            vsInfo.dailyLimit = Integer.parseInt(vsInfoEntry.getElementsByTagName("dailyLimit").item(0).getTextContent());
            vsInfo.priority = vsInfoEntry.getElementsByTagName("priority").item(0).getTextContent();
            int intPriority = Integer.parseInt(vsInfo.priority);
            if (intPriority > maxPriority) {
                maxPriority = intPriority;
            }
            // Обрабатываем массив keywords
            NodeList keywords = vsInfoEntry.getElementsByTagName("keywords");
            vsInfo.keywords = new String[keywords.getLength()];
            for (int j=0; j<keywords.getLength(); j++) {
                vsInfo.keywords[j] = keywords.item(j).getTextContent();
            }
            // Добавляем VSInfo в набор
            vsInfoSet.put(vsInfo.namespace, vsInfo);
        }
    }

    /**
     * Метод возвращает всю хэш-таблицу с информацией о видах сведений
     * @return Хэш-таблица с ключами Namespace и элементами VSInfo
     */
    public Map getVSInfoSet() {
        return vsInfoSet;
    }

    /**
     * Метод проверяет, есть ли заданное пространство имен среди ключей таблицы VSInfoSet
     * @param namespace Искомое пространство имен
     * @return true, если пространство имен есть в таблице, и false в противном случае
     */
    public Boolean isNamespaceRegistered(String namespace) {
            return vsInfoSet.containsKey(namespace);
    }

    /**
     * Метод возвращает информацию о виде сведений по его пространству имен
     * @param namespace Искомое пространство имен
     * @return Объект VSInfo с информацией о виде сведений
     */
    public VSInfo getVSInfo(String namespace) {
        return vsInfoSet.get(namespace);
    }

}
