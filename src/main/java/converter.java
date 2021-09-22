//import ru.hemulen.h2pgsql.signer.Signer;
import ru.hemulen.converter.thread.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class converter {

    public static void main(String[] args) {
        Properties props = new Properties();
        try {
            props.load(new FileInputStream("./config/config.ini"));
        } catch (IOException e) {
            System.out.println("Не удалось загрузить файл конфигурации");
            e.printStackTrace();
            System.exit(1);
        }

        // RequestUpdater считывает message_id и send_timestamp запросов из базы H2 (или PostgreSQL) и обновляет log в psql
        RequestUpdater requestUpdater = new RequestUpdater(props);
        // ResponseUpdater считывает response_id и delivery_timestamp из базы H2 (или PostgreSQL) и обновляет log в psql
        ResponseUpdater responseUpdater = new ResponseUpdater(props);
        // RequestProcessor обрабатывает запросы из requests и помещает результаты в папку prepared по приоритетам
        RequestProcessor requestProcessor = new RequestProcessor(props);
        // RequestSender переносит файлы из prepared в порядке возрастания приоритета в папку OUT адаптера
        RequestSender requestSender = new RequestSender(props);
        // ResponseProcessor обрабатывает ответы из папки IN адаптера и помещает результаты в папку responses
        ResponseProcessor responseProcessor = new ResponseProcessor(props);
        // Response13Processor обрабатывает ответы из папки IN второго instance адаптера, который работает с версией 1.3 схем СМЭВ
        Response13Processor response13Processor = new Response13Processor(props);

        requestProcessor.start();
        requestSender.start();
        responseProcessor.start();
        response13Processor.start();
        requestUpdater.start();
        responseUpdater.start();
    }
}
