package com.farpost;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzerTest {

    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        // Перехватываем System.out для проверки вывода
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        // Восстанавливаем System.out
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("Тест базовой функциональности - обнаружение инцидента по 5xx кодам")
    void testBasicIncidentDetection() throws Exception {
        String logData =
                "192.168.32.181 - - [14/06/2017:16:47:00 +1000] \"GET /test HTTP/1.1\" 200 2 10.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:01 +1000] \"GET /test HTTP/1.1\" 500 2 15.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /test HTTP/1.1\" 502 2 20.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:03 +1000] \"GET /test HTTP/1.1\" 200 2 25.5 \"-\" \"user-agent\" prio:0\n";

        InputStream inputStream = new ByteArrayInputStream(logData.getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        assertFalse(output.isEmpty(), "Должен быть выведен инцидент");
        assertTrue(output.contains("16:47:"), "Время инцидента должно присутствовать в выводе");
    }

    @Test
    @DisplayName("Тест обнаружения инцидента по времени ответа")
    void testSlowResponseIncidentDetection() throws Exception {
        String logData =
                "192.168.32.181 - - [14/06/2017:16:47:00 +1000] \"GET /test HTTP/1.1\" 200 2 10.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:01 +1000] \"GET /test HTTP/1.1\" 200 2 150.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /test HTTP/1.1\" 200 2 200.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:03 +1000] \"GET /test HTTP/1.1\" 200 2 25.5 \"-\" \"user-agent\" prio:0\n";

        InputStream inputStream = new ByteArrayInputStream(logData.getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        assertFalse(output.isEmpty(), "Должен быть выведен инцидент из-за медленных запросов");
    }

    @Test
    @DisplayName("Тест отсутствия инцидента при высокой доступности")
    void testNoIncidentWithHighAvailability() throws Exception {
        String logData =
                "192.168.32.181 - - [14/06/2017:16:47:00 +1000] \"GET /test HTTP/1.1\" 200 2 10.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:01 +1000] \"GET /test HTTP/1.1\" 200 2 15.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /test HTTP/1.1\" 200 2 20.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:03 +1000] \"GET /test HTTP/1.1\" 200 2 25.5 \"-\" \"user-agent\" prio:0\n";

        InputStream inputStream = new ByteArrayInputStream(logData.getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        assertTrue(output.isEmpty(), "Не должно быть инцидентов при высокой доступности");
    }

    @Test
    @DisplayName("Тест множественных инцидентов")
    void testMultipleIncidents() throws Exception {
        String logData =
                // Первый инцидент
                "192.168.32.181 - - [14/06/2017:16:47:00 +1000] \"GET /test HTTP/1.1\" 500 2 10.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:01 +1000] \"GET /test HTTP/1.1\" 200 2 15.5 \"-\" \"user-agent\" prio:0\n" +
                        // Пауза
                        "192.168.32.181 - - [14/06/2017:16:47:05 +1000] \"GET /test HTTP/1.1\" 200 2 20.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:06 +1000] \"GET /test HTTP/1.1\" 200 2 25.5 \"-\" \"user-agent\" prio:0\n" +
                        // Второй инцидент
                        "192.168.32.181 - - [14/06/2017:16:47:10 +1000] \"GET /test HTTP/1.1\" 503 2 30.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:11 +1000] \"GET /test HTTP/1.1\" 200 2 35.5 \"-\" \"user-agent\" prio:0\n";

        InputStream inputStream = new ByteArrayInputStream(logData.getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        String[] lines = output.split("\n");
        assertEquals(2, lines.length, "Должно быть два инцидента");
    }

    @Test
    @DisplayName("Тест граничного случая - точно на пороге доступности")
    void testAvailabilityThresholdBoundary() throws Exception {
        // 99 успешных + 1 неуспешный = 99% доступности
        StringBuilder logBuilder = new StringBuilder();

        // 99 успешных запросов
        for (int i = 0; i < 99; i++) {
            logBuilder.append(String.format(
                    "192.168.32.181 - - [14/06/2017:16:47:%02d +1000] \"GET /test HTTP/1.1\" 200 2 10.5 \"-\" \"user-agent\" prio:0\n",
                    i % 60
            ));
        }

        // 1 неуспешный запрос
        logBuilder.append("192.168.32.181 - - [14/06/2017:16:48:39 +1000] \"GET /test HTTP/1.1\" 500 2 15.5 \"-\" \"user-agent\" prio:0\n");

        InputStream inputStream = new ByteArrayInputStream(logBuilder.toString().getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        assertTrue(output.isEmpty(), "При доступности ровно 99% и пороге 99% инцидента быть не должно");
    }



    @Test
    @DisplayName("Тест строгого порога доступности 99.9%")
    void testStrictAvailabilityThreshold() throws Exception {
        String logData =
                "192.168.32.181 - - [14/06/2017:16:47:00 +1000] \"GET /test HTTP/1.1\" 200 2 10.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:01 +1000] \"GET /test HTTP/1.1\" 500 2 15.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /test HTTP/1.1\" 200 2 20.5 \"-\" \"user-agent\" prio:0\n";

        InputStream inputStream = new ByteArrayInputStream(logData.getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.9, 100); // Очень высокий порог

        analyzer.run();

        String output = outputStream.toString().trim();
        assertFalse(output.isEmpty(), "При строгом пороге 99.9% даже один отказ должен создать инцидент");
    }

    @Test
    @DisplayName("Тест комбинированных отказов (5xx + медленные запросы)")
    void testCombinedFailures() throws Exception {
        String logData =
                "192.168.32.181 - - [14/06/2017:16:47:00 +1000] \"GET /test HTTP/1.1\" 200 2 10.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:01 +1000] \"GET /test HTTP/1.1\" 500 2 15.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /test HTTP/1.1\" 200 2 150.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:03 +1000] \"GET /test HTTP/1.1\" 200 2 25.5 \"-\" \"user-agent\" prio:0\n";

        InputStream inputStream = new ByteArrayInputStream(logData.getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        assertFalse(output.isEmpty(), "Комбинированные отказы должны создать инцидент");
    }

    @Test
    @DisplayName("Тест различных HTTP 5xx кодов")
    void testDifferent5xxCodes() throws Exception {
        String logData =
                "192.168.32.181 - - [14/06/2017:16:47:00 +1000] \"GET /test HTTP/1.1\" 500 2 10.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:01 +1000] \"GET /test HTTP/1.1\" 501 2 15.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /test HTTP/1.1\" 502 2 20.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:03 +1000] \"GET /test HTTP/1.1\" 503 2 25.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:04 +1000] \"GET /test HTTP/1.1\" 504 2 30.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:05 +1000] \"GET /test HTTP/1.1\" 200 2 35.5 \"-\" \"user-agent\" prio:0\n";

        InputStream inputStream = new ByteArrayInputStream(logData.getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        assertFalse(output.isEmpty(), "Все HTTP 5xx коды должны считаться отказами");
    }

    @Test
    @DisplayName("Тест что 4xx коды не считаются отказами")
    void test4xxCodesNotFailures() throws Exception {
        String logData =
                "192.168.32.181 - - [14/06/2017:16:47:00 +1000] \"GET /test HTTP/1.1\" 400 2 10.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:01 +1000] \"GET /test HTTP/1.1\" 404 2 15.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /test HTTP/1.1\" 403 2 20.5 \"-\" \"user-agent\" prio:0\n" +
                        "192.168.32.181 - - [14/06/2017:16:47:03 +1000] \"GET /test HTTP/1.1\" 401 2 25.5 \"-\" \"user-agent\" prio:0\n";

        InputStream inputStream = new ByteArrayInputStream(logData.getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        assertTrue(output.isEmpty(), "HTTP 4xx коды не должны считаться отказами");
    }

}