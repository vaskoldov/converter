package ru.hemulen.converter.utils;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Класс дублирует функциональность класса AbstractTools, но не является абстрактным, а его методы
 * не являются статическими.
 * Каждый поток приложения будет создавать свои собственные экземпляры класса FileUtils.
 */
public class FileUtils {
    private DocumentBuilderFactory documentBuilderFactory;
    private Transformer transformer;

    public FileUtils() throws TransformerConfigurationException {
        documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        documentBuilderFactory.setCoalescing(true);
        documentBuilderFactory.setIgnoringElementContentWhitespace(true);
        transformer = TransformerFactory.newInstance().newTransformer();
    }
    /**
     * Парсит XML файл и возвращает корневой элемент
     *
     * @param file файл с xml документом
     * @return корневой элемент документа
     * @throws IOException                  в случае ошибки чтения входного файла
     * @throws ParserConfigurationException в случае ошибки создания xml парсера
     * @throws SAXException                 в случае невалидного xml кода
     */
    public synchronized Element fileToElement(File file) throws IOException, ParserConfigurationException, SAXException {
        if (file == null) {
            return null;
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            return documentBuilderFactory.newDocumentBuilder().parse(inputStream).getDocumentElement();
        }
    }

    /**
     * Записывает указанные данные в выходной файл
     *
     * @param element   данные для записи
     * @param file      файл с XML-сообщением
     * @throws IOException в случае ошибки ввода-вывода
     */
    public synchronized void writeOutput(Element element, File file) throws IOException, TransformerException {
        if (element == null) {
            return;
        }
        try (OutputStream outputStream = new FileOutputStream(file)) {
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(element);
            transformer.transform(source, result);
            IOUtils.write(result.getWriter().toString(), outputStream);
        }
    }

    /**
     * Архивирует указанные файлы в архив с указанным именем
     * @param files   массив исходных файлов
     * @param zipName имя файла с архивом
     * @throws IOException ошибка файловой операции
     */
    public synchronized void zipElements(File[] files, String zipName) throws IOException {
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zipName));
        for (File file : files) {
            FileInputStream fis = new FileInputStream(file.getAbsolutePath());
            ZipEntry zipEntry = new ZipEntry(file.getName());
            zout.putNextEntry(zipEntry);
            byte[] buffer = new byte[fis.available()];
            fis.read(buffer);
            zout.write(buffer);
            zout.closeEntry();
            fis.close();
        }
        zout.close();
    }
}
