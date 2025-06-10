package com.farpost;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzerTest {

    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;
    private int windowSize; // Размер окна в секундах

    @BeforeEach
    void setUp() throws Exception {
        // Получаем размер окна через рефлексию
        Field windowField = Analyzer.class.getDeclaredField("ANALYSIS_WINDOW_SECONDS");
        windowField.setAccessible(true);
        windowSize = windowField.getInt(null);

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
        StringBuilder logBuilder = new StringBuilder();

        // Создаем ситуацию где в окне будет 100% отказов
        // Сначала один хороший запрос
        logBuilder.append("192.168.32.181 - - [14/06/2017:16:46:00 +1000] \"GET /test HTTP/1.1\" 200 2 10.5 \"-\" \"user-agent\" prio:0\n");

        // Затем создаем сплошные отказы на протяжении времени больше размера окна
        // Это гарантирует, что в какой-то момент окно будет заполнено только отказами
        for (int i = 0; i < windowSize + 5; i++) {
            logBuilder.append(String.format(
                    "192.168.32.181 - - [14/06/2017:16:47:%02d +1000] \"GET /test HTTP/1.1\" 500 2 15.5 \"-\" \"user-agent\" prio:0\n",
                    i % 60
            ));
        }

        // Восстанавливаем сервис хорошими запросами
        for (int i = 0; i < windowSize + 2; i++) {
            logBuilder.append(String.format(
                    "192.168.32.181 - - [14/06/2017:16:48:%02d +1000] \"GET /test HTTP/1.1\" 200 2 25.5 \"-\" \"user-agent\" prio:0\n",
                    i % 60
            ));
        }

        InputStream inputStream = new ByteArrayInputStream(logBuilder.toString().getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        assertFalse(output.isEmpty(), "Должен быть выведен инцидент");
        assertTrue(output.contains("16:47:") || output.contains("16:48:"), "Время инцидента должно присутствовать в выводе");
    }

    @Test
    @DisplayName("Тест обнаружения инцидента по времени ответа")
    void testSlowResponseIncidentDetection() throws Exception {
        StringBuilder logBuilder = new StringBuilder();

        // Хороший запрос в начале
        logBuilder.append("192.168.32.181 - - [14/06/2017:16:46:00 +1000] \"GET /test HTTP/1.1\" 200 2 10.5 \"-\" \"user-agent\" prio:0\n");

        // Создаем сплошные медленные запросы (они считаются отказами)
        for (int i = 0; i < windowSize + 5; i++) {
            logBuilder.append(String.format(
                    "192.168.32.181 - - [14/06/2017:16:47:%02d +1000] \"GET /test HTTP/1.1\" 200 2 150.5 \"-\" \"user-agent\" prio:0\n",
                    i % 60
            ));
        }

        // Восстанавливаем быстрыми запросами
        for (int i = 0; i < windowSize + 2; i++) {
            logBuilder.append(String.format(
                    "192.168.32.181 - - [14/06/2017:16:48:%02d +1000] \"GET /test HTTP/1.1\" 200 2 25.5 \"-\" \"user-agent\" prio:0\n",
                    i % 60
            ));
        }

        InputStream inputStream = new ByteArrayInputStream(logBuilder.toString().getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        assertFalse(output.isEmpty(), "Должен быть выведен инцидент из-за медленных запросов");
    }

    @Test
    @DisplayName("Тест отсутствия инцидента при высокой доступности")
    void testNoIncidentWithHighAvailability() throws Exception {
        StringBuilder logBuilder = new StringBuilder();

        // Создаем только успешные запросы на протяжении длительного времени
        for (int i = 0; i < windowSize * 3 + 10; i++) {
            logBuilder.append(String.format(
                    "192.168.32.181 - - [14/06/2017:16:47:%02d +1000] \"GET /test HTTP/1.1\" 200 2 10.5 \"-\" \"user-agent\" prio:0\n",
                    i % 60
            ));
        }

        InputStream inputStream = new ByteArrayInputStream(logBuilder.toString().getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        assertTrue(output.isEmpty(), "Не должно быть инцидентов при высокой доступности");
    }

    @Test
    @DisplayName("Тест строгого порога доступности 99.9%")
    void testStrictAvailabilityThreshold() throws Exception {
        StringBuilder logBuilder = new StringBuilder();

        // При пороге 99.9% даже небольшое количество отказов должно вызвать инцидент
        // Создаем много хороших запросов
        for (int i = 0; i < 100; i++) {
            logBuilder.append(String.format(
                    "192.168.32.181 - - [14/06/2017:16:47:%02d +1000] \"GET /test HTTP/1.1\" 200 2 10.5 \"-\" \"user-agent\" prio:0\n",
                    i % 60
            ));
        }

        // Добавляем серию отказов которая точно превысит порог 99.9%
        for (int i = 0; i < Math.max(5, windowSize); i++) {
            logBuilder.append(String.format(
                    "192.168.32.181 - - [14/06/2017:16:48:%02d +1000] \"GET /test HTTP/1.1\" 500 2 15.5 \"-\" \"user-agent\" prio:0\n",
                    i % 60
            ));
        }

        // Восстановление
        for (int i = 0; i < windowSize + 2; i++) {
            logBuilder.append(String.format(
                    "192.168.32.181 - - [14/06/2017:16:49:%02d +1000] \"GET /test HTTP/1.1\" 200 2 20.5 \"-\" \"user-agent\" prio:0\n",
                    i % 60
            ));
        }

        InputStream inputStream = new ByteArrayInputStream(logBuilder.toString().getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.9, 100); // Очень высокий порог

        analyzer.run();

        String output = outputStream.toString().trim();
        assertFalse(output.isEmpty(), "При строгом пороге 99.9% отказы должны создать инцидент");
    }

    @Test
    @DisplayName("Тест комбинированных отказов (5xx + медленные запросы)")
    void testCombinedFailures() throws Exception {
        StringBuilder logBuilder = new StringBuilder();

        // Хороший запрос в начале
        logBuilder.append("192.168.32.181 - - [14/06/2017:16:46:00 +1000] \"GET /test HTTP/1.1\" 200 2 10.5 \"-\" \"user-agent\" prio:0\n");

        // Комбинированные отказы - чередуем 5xx и медленные запросы
        for (int i = 0; i < windowSize + 5; i++) {
            if (i % 2 == 0) {
                // 5xx ошибка
                logBuilder.append(String.format(
                        "192.168.32.181 - - [14/06/2017:16:47:%02d +1000] \"GET /test HTTP/1.1\" 500 2 15.5 \"-\" \"user-agent\" prio:0\n",
                        i % 60
                ));
            } else {
                // Медленный запрос
                logBuilder.append(String.format(
                        "192.168.32.181 - - [14/06/2017:16:47:%02d +1000] \"GET /test HTTP/1.1\" 200 2 150.5 \"-\" \"user-agent\" prio:0\n",
                        i % 60
                ));
            }
        }

        // Восстановление
        for (int i = 0; i < windowSize + 2; i++) {
            logBuilder.append(String.format(
                    "192.168.32.181 - - [14/06/2017:16:48:%02d +1000] \"GET /test HTTP/1.1\" 200 2 25.5 \"-\" \"user-agent\" prio:0\n",
                    i % 60
            ));
        }

        InputStream inputStream = new ByteArrayInputStream(logBuilder.toString().getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        assertFalse(output.isEmpty(), "Комбинированные отказы должны создать инцидент");
    }

    @Test
    @DisplayName("Тест различных HTTP 5xx кодов")
    void testDifferent5xxCodes() throws Exception {
        StringBuilder logBuilder = new StringBuilder();
        int[] errorCodes = {500, 501, 502, 503, 504};

        // Создаем инцидент с различными 5xx кодами
        for (int i = 0; i < windowSize + 5; i++) {
            int errorCode = errorCodes[i % errorCodes.length];
            logBuilder.append(String.format(
                    "192.168.32.181 - - [14/06/2017:16:47:%02d +1000] \"GET /test HTTP/1.1\" %d 2 %d.5 \"-\" \"user-agent\" prio:0\n",
                    i % 60, errorCode, 10 + i * 5
            ));
        }

        // Восстановление
        for (int i = 0; i < windowSize + 2; i++) {
            logBuilder.append(String.format(
                    "192.168.32.181 - - [14/06/2017:16:48:%02d +1000] \"GET /test HTTP/1.1\" 200 2 35.5 \"-\" \"user-agent\" prio:0\n",
                    i % 60
            ));
        }

        InputStream inputStream = new ByteArrayInputStream(logBuilder.toString().getBytes(StandardCharsets.UTF_8));
        Analyzer analyzer = new Analyzer(inputStream, 99.0, 100);

        analyzer.run();

        String output = outputStream.toString().trim();
        assertFalse(output.isEmpty(), "Все HTTP 5xx коды должны считаться отказами");
    }
}