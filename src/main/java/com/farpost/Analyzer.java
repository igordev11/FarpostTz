package com.farpost;

import com.farpost.model.LogEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public class Analyzer {
    // Размер окна для анализа текущей доступности в секундах.
    private static final int ANALYSIS_WINDOW_SECONDS = 1;

    private static final DateTimeFormatter OUTPUT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final InputStream logStream;
    private final double availabilityThreshold;
    private final long responseTimeThresholdMs;
    private final LogParser parser;

    // Переменная для отслеживания времени окончания последнего выведенного инцидента.
    // Нужна только для гарантии, что в выводе не будет перекрывающихся интервалов.
    private LocalDateTime lastPrintedIncidentEndTime = null;

    public Analyzer(InputStream logStream, double availabilityThreshold, long responseTimeThresholdMs) {
        this.logStream = logStream;
        this.availabilityThreshold = availabilityThreshold;
        this.responseTimeThresholdMs = responseTimeThresholdMs;
        this.parser = new LogParser();
    }

    public void run() throws IOException {
        boolean isIncidentActive = false;
        LocalDateTime incidentStartTime = null; // Фактическое время начала текущего инцидента
        LocalDateTime lastProcessedEntryTime = null; // Время последней записи, обработанной из лога

        long totalRequestsInIncident = 0;
        long failedRequestsInIncident = 0;

        Deque<LogEntry> window = new ArrayDeque<>();
        long failuresInWindow = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(logStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Optional<LogEntry> entryOpt = parser.parse(line, responseTimeThresholdMs);
                if (entryOpt.isEmpty()) {
                    continue; // Пропускаем некорректные строки
                }
                LogEntry currentEntry = entryOpt.get();
                lastProcessedEntryTime = currentEntry.getTimestamp();

                // 1. Обновляем скользящее окно
                window.addLast(currentEntry);
                if (currentEntry.isFailure()) {
                    failuresInWindow++;
                }

                // 2. Удаляем старые записи из окна и обновляем счетчик отказов в окне
                LocalDateTime windowStartBoundary = currentEntry.getTimestamp().minusSeconds(ANALYSIS_WINDOW_SECONDS);
                while (!window.isEmpty() && window.getFirst().getTimestamp().isBefore(windowStartBoundary)) {
                    if (window.removeFirst().isFailure()) {
                        failuresInWindow--;
                    }
                }

                // 3. Вычисляем текущую доступность на основе данных в окне
                double currentAvailability = calculateAvailability(window.size(), failuresInWindow);

                // 4. Логика состояний инцидента
                if (isIncidentActive) {
                    // Мы находимся в активном инциденте
                    totalRequestsInIncident++;
                    if (currentEntry.isFailure()) {
                        failedRequestsInIncident++;
                    }

                    // Проверяем, не закончился ли инцидент (доступность в окне восстановилась)
                    if (currentAvailability >= availabilityThreshold) {
                        // Инцидент завершился. Выводим его.
                        printIncident(incidentStartTime, currentEntry.getTimestamp(), totalRequestsInIncident, failedRequestsInIncident);
                        isIncidentActive = false;
                        // Сбрасываем счетчики для следующего инцидента
                        totalRequestsInIncident = 0;
                        failedRequestsInIncident = 0;
                        incidentStartTime = null;
                    }
                } else {
                    // Мы не в инциденте, проверяем, не начался ли он
                    if (currentAvailability < availabilityThreshold && !window.isEmpty()) {
                        // Инцидент начался.
                        isIncidentActive = true;

                        // Начало инцидента - это самая ранняя запись в текущем окне,
                        // которая привела к падению доступности.
                        // Используем currentEntry.getTimestamp() для начала нового инцидента,
                        // если lastPrintedIncidentEndTime ещё не установлено или текущая запись
                        // идёт после него. Это предотвращает старт нового инцидента раньше, чем
                        // закончился предыдущий в выводе
                        incidentStartTime = currentEntry.getTimestamp().minusSeconds(ANALYSIS_WINDOW_SECONDS - 1); // Начало окна, которое вызвало падение
                        if (lastPrintedIncidentEndTime != null && incidentStartTime.isBefore(lastPrintedIncidentEndTime)) {
                            incidentStartTime = lastPrintedIncidentEndTime; // Принудительно начинаем после предыдущего
                        }

                        // Инициализируем счетчики инцидента на основе записей в окне,
                        // которые относятся к новому инциденту (начиная с incidentStartTime).
                        totalRequestsInIncident = 0;
                        failedRequestsInIncident = 0;
                        for (LogEntry entryInWindow : window) {
                            if (!entryInWindow.getTimestamp().isBefore(incidentStartTime)) {
                                totalRequestsInIncident++;
                                if (entryInWindow.isFailure()) {
                                    failedRequestsInIncident++;
                                }
                            }
                        }
                    }
                }
            }
        }
        // После обработки всех строк, если инцидент еще активен, закрываем его
        if (isIncidentActive && lastProcessedEntryTime != null) {
            printIncident(incidentStartTime, lastProcessedEntryTime, totalRequestsInIncident, failedRequestsInIncident);
        }
    }

    private double calculateAvailability(long total, long failures) {
        if (total == 0) return 100.0;
        return 100.0 * (total - failures) / total;
    }

    private void printIncident(LocalDateTime start, LocalDateTime end, long total, long failures) {
        // Проверяем, что инцидент имеет логичное начало и конец.
        if (start == null || end == null) {
            return;
        }

        // Если начало инцидента раньше или равно времени окончания предыдущего выведенного инцидента,
        // корректируем начало, чтобы избежать перекрытий в выводе.
        if (lastPrintedIncidentEndTime != null && start.isBefore(lastPrintedIncidentEndTime)) {
            start = lastPrintedIncidentEndTime;
        }

        // Дополнительная проверка, чтобы не выводить инциденты нулевой или отрицательной продолжительности
        if (!start.isBefore(end)) {
            return;
        }

        double finalAvailability = calculateAvailability(total, failures);

        System.out.printf("%s %s %.1f%n",
                start.format(OUTPUT_TIME_FORMATTER),
                end.format(OUTPUT_TIME_FORMATTER),
                finalAvailability
        );
        // Обновляем время окончания последнего успешно выведенного инцидента
        lastPrintedIncidentEndTime = end;
    }
}