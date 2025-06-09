package com.farpost;

import com.farpost.model.LogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Тесты для класса LogEntry")
class LogEntryTest {

    @Test
    @DisplayName("Должен корректно инициализироваться и возвращать значения через геттеры")
    void shouldInitializeCorrectlyAndReturnValues() {
        LocalDateTime timestamp = LocalDateTime.of(2023, 1, 15, 10, 30, 0);
        boolean isFailure = true;
        LogEntry entry = new LogEntry(timestamp, isFailure);


        assertEquals(timestamp, entry.getTimestamp(), "Временная метка должна совпадать");
        assertEquals(isFailure, entry.isFailure(), "Флаг отказа должен совпадать");
    }

    @Test
    @DisplayName("Метод equals должен корректно сравнивать идентичные объекты")
    void equalsShouldReturnTrueForIdenticalObjects() {
        LocalDateTime timestamp = LocalDateTime.of(2023, 1, 15, 10, 30, 0);
        LogEntry entry1 = new LogEntry(timestamp, true);
        LogEntry entry2 = new LogEntry(timestamp, true);

        assertTrue(entry1.equals(entry2), "Идентичные объекты должны быть равны");
    }

    @Test
    @DisplayName("Метод equals должен возвращать false для разных объектов")
    void equalsShouldReturnFalseForDifferentObjects() {
        LocalDateTime timestamp1 = LocalDateTime.of(2023, 1, 15, 10, 30, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2023, 1, 15, 10, 30, 1);
        LogEntry entry1 = new LogEntry(timestamp1, true);
        LogEntry entry2 = new LogEntry(timestamp2, true); // Разные временные метки
        LogEntry entry3 = new LogEntry(timestamp1, false); // Разные флаги отказа

        assertFalse(entry1.equals(entry2), "Объекты с разными временными метками не должны быть равны");
        assertFalse(entry1.equals(entry3), "Объекты с разными флагами отказа не должны быть равны");
    }

    @Test
    @DisplayName("Метод hashCode должен быть одинаковым для равных объектов")
    void hashCodeShouldBeSameForEqualObjects() {
        LocalDateTime timestamp = LocalDateTime.of(2023, 1, 15, 10, 30, 0);
        LogEntry entry1 = new LogEntry(timestamp, true);
        LogEntry entry2 = new LogEntry(timestamp, true);

        assertEquals(entry1.hashCode(), entry2.hashCode(), "Хэш-коды равных объектов должны совпадать");
    }

    @Test
    @DisplayName("Метод hashCode должен быть разным для разных объектов")
    void hashCodeShouldBeDifferentForDifferentObjects() {
        LocalDateTime timestamp1 = LocalDateTime.of(2023, 1, 15, 10, 30, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2023, 1, 15, 10, 30, 1);
        LogEntry entry1 = new LogEntry(timestamp1, true);
        LogEntry entry2 = new LogEntry(timestamp2, true);

        assertNotEquals(entry1.hashCode(), entry2.hashCode(), "Хэш-коды разных объектов не должны совпадать");
    }

    @Test
    @DisplayName("Метод equals должен возвращать false при сравнении с null")
    void equalsShouldReturnFalseWhenComparingWithNull() {
        LocalDateTime timestamp = LocalDateTime.of(2023, 1, 15, 10, 30, 0);
        LogEntry entry = new LogEntry(timestamp, true);
        assertFalse(entry.equals(null), "Сравнение с null должно возвращать false");
    }

    @Test
    @DisplayName("Метод equals должен возвращать false при сравнении с объектом другого типа")
    void equalsShouldReturnFalseWhenComparingWithDifferentType() {
        LocalDateTime timestamp = LocalDateTime.of(2023, 1, 15, 10, 30, 0);
        LogEntry entry = new LogEntry(timestamp, true);
        Object other = new Object();
        assertFalse(entry.equals(other), "Сравнение с объектом другого типа должно возвращать false");
    }
}