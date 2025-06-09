package com.farpost;

import com.farpost.model.LogEntry;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser {
    // регулярное выражение для захвата нужных полей
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^\\S+ \\S+ \\S+ \\[(\\d{2}/\\d{2}/\\d{4}:\\d{2}:\\d{2}:\\d{2} \\+\\d{4})] \".*?\" (\\d{3}) \\S+ (\\S+).*$"
    );

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy:HH:mm:ss Z", Locale.ENGLISH);


    /**
     * Разбирает строку лога и определяет, является ли она отказом.
     */
    public Optional<LogEntry> parse(String line, long responseTimeThresholdMs) {
        Matcher matcher = LOG_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {

            OffsetDateTime offsetDateTime = OffsetDateTime.parse(matcher.group(1), DATE_FORMATTER);

            LocalDateTime timestamp = offsetDateTime.toLocalDateTime();

            int statusCode = Integer.parseInt(matcher.group(2));
            double responseTime = Double.parseDouble(matcher.group(3));

            boolean isFailure = (statusCode >= 500) || (responseTime > responseTimeThresholdMs);
            return Optional.of(new LogEntry(timestamp, isFailure));
        } catch (DateTimeParseException | NumberFormatException e) {

            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}