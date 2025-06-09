package com.farpost;

import com.farpost.model.LogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Тесты для класса LogParser")
class LogParserTest {

    private final LogParser parser = new LogParser();
    private final long responseTimeThresholdMs = 50; // Порог для времени ответа в тестах

    @Test
    @DisplayName("Должен успешно парсить корректную строку лога с успехом")
    void shouldParseValidLogLineSuccessfully() {
        String logLine = "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"PUT /rest/v1.4/documents?zone=default&_rid=6076537c HTTP/1.1\" 200 2 44.510983 \"-\" \"@list-item-updater\" prio:0";
        Optional<LogEntry> entryOpt = parser.parse(logLine, responseTimeThresholdMs);

        // Проверяем, что Optional содержит значение
        assertTrue(entryOpt.isPresent(), "Парсер должен вернуть LogEntry для корректной строки");
        LogEntry entry = entryOpt.get();

        // Проверяем корректность парсинга временной метки
        assertEquals(LocalDateTime.of(2017, Month.JUNE, 14, 16, 47, 2), entry.getTimestamp(), "Время должно быть корректно распарсено");
        // Проверяем, что не является отказом (200 OK, время 44.5 < 50)
        assertFalse(entry.isFailure(), "Запрос не должен быть отказом (200 OK, время в пределах)");
    }

    @Test
    @DisplayName("Должен корректно определять отказ по HTTP 5xx коду")
    void shouldIdentifyFailureBy5xxStatusCode() {
        String logLine = "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /api HTTP/1.1\" 500 10 10.000 \"-\" \"user\" prio:0";
        Optional<LogEntry> entryOpt = parser.parse(logLine, responseTimeThresholdMs);

        assertTrue(entryOpt.isPresent(), "Парсер должен вернуть LogEntry для 5xx кода");
        assertTrue(entryOpt.get().isFailure(), "Запрос с 500 кодом должен быть отказом");
    }

    @Test
    @DisplayName("Должен корректно определять отказ по времени ответа")
    void shouldIdentifyFailureByResponseTime() {
        String logLine = "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /api HTTP/1.1\" 200 10 50.001 \"-\" \"user\" prio:0";
        Optional<LogEntry> entryOpt = parser.parse(logLine, responseTimeThresholdMs);

        assertTrue(entryOpt.isPresent(), "Парсер должен вернуть LogEntry для превышения времени ответа");
        assertTrue(entryOpt.get().isFailure(), "Запрос с превышенным временем ответа должен быть отказом");
    }

    @Test
    @DisplayName("Должен корректно определять отказ по HTTP 5xx коду и времени ответа")
    void shouldIdentifyFailureBy5xxStatusCodeAndResponseTime() {
        String logLine = "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /api HTTP/1.1\" 503 10 100.000 \"-\" \"user\" prio:0";
        Optional<LogEntry> entryOpt = parser.parse(logLine, responseTimeThresholdMs);

        assertTrue(entryOpt.isPresent(), "Парсер должен вернуть LogEntry для 5xx кода и превышения времени ответа");
        assertTrue(entryOpt.get().isFailure(), "Запрос должен быть отказом");
    }

    @Test
    @DisplayName("Должен возвращать Optional.empty() для неполной строки лога")
    void shouldReturnEmptyForIncompleteLogLine() {
        String logLine = "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /api HTTP/1.1\" 200"; // Неполная строка
        Optional<LogEntry> entryOpt = parser.parse(logLine, responseTimeThresholdMs);

        assertTrue(entryOpt.isEmpty(), "Парсер должен вернуть Optional.empty() для неполной строки");
    }

    @Test
    @DisplayName("Должен возвращать Optional.empty() для строки с неверным форматом даты")
    void shouldReturnEmptyForInvalidDateFormat() {
        String logLine = "192.168.32.181 - - [14-06-2017:16:47:02 +1000] \"GET /api HTTP/1.1\" 200 2 44.510983 \"-\" \"@list-item-updater\" prio:0";
        Optional<LogEntry> entryOpt = parser.parse(logLine, responseTimeThresholdMs);

        assertTrue(entryOpt.isEmpty(), "Парсер должен вернуть Optional.empty() для неверного формата даты");
    }

    @Test
    @DisplayName("Должен возвращать Optional.empty() для строки с неверным форматом HTTP статуса")
    void shouldReturnEmptyForInvalidStatusCodeFormat() {
        String logLine = "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /api HTTP/1.1\" ABC 2 44.510983 \"-\" \"@list-item-updater\" prio:0";
        Optional<LogEntry> entryOpt = parser.parse(logLine, responseTimeThresholdMs);

        assertTrue(entryOpt.isEmpty(), "Парсер должен вернуть Optional.empty() для неверного формата статуса");
    }

    @Test
    @DisplayName("Должен возвращать Optional.empty() для строки с неверным форматом времени ответа")
    void shouldReturnEmptyForInvalidResponseTimeFormat() {
        String logLine = "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /api HTTP/1.1\" 200 2 ABC \"-\" \"@list-item-updater\" prio:0";
        Optional<LogEntry> entryOpt = parser.parse(logLine, responseTimeThresholdMs);

        assertTrue(entryOpt.isEmpty(), "Парсер должен вернуть Optional.empty() для неверного формата времени ответа");
    }

    @Test
    @DisplayName("Должен корректно обрабатывать нулевое время ответа")
    void shouldHandleZeroResponseTime() {
        String logLine = "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /api HTTP/1.1\" 200 2 0.0 \"-\" \"@list-item-updater\" prio:0";
        Optional<LogEntry> entryOpt = parser.parse(logLine, responseTimeThresholdMs);

        assertTrue(entryOpt.isPresent(), "Парсер должен вернуть LogEntry для нулевого времени ответа");
        assertFalse(entryOpt.get().isFailure(), "Запрос с нулевым временем ответа не должен быть отказом (если в пределах порога)");
    }

    @Test
    @DisplayName("Должен корректно обрабатывать отрицательное время ответа (не должно быть отказом, если < порога)")
    void shouldHandleNegativeResponseTime() {
        String logLine = "192.168.32.181 - - [14/06/2017:16:47:02 +1000] \"GET /api HTTP/1.1\" 200 2 -10.5 \"-\" \"@list-item-updater\" prio:0";
        Optional<LogEntry> entryOpt = parser.parse(logLine, responseTimeThresholdMs);

        assertTrue(entryOpt.isPresent(), "Парсер должен вернуть LogEntry для отрицательного времени ответа");
        assertFalse(entryOpt.get().isFailure(), "Запрос с отрицательным временем ответа не должен быть отказом (если в пределах порога)");
    }
}