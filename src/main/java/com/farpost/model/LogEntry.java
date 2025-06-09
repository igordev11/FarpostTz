package com.farpost.model;


import java.time.LocalDateTime;
import java.util.Objects;

public final class LogEntry {
    private final LocalDateTime timestamp;
    private final boolean isFailure;

    public LogEntry(LocalDateTime timestamp, boolean isFailure) {
        this.timestamp = timestamp;
        this.isFailure = isFailure;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public boolean isFailure() {
        return isFailure;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogEntry logEntry = (LogEntry) o;
        return isFailure == logEntry.isFailure && timestamp.equals(logEntry.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, isFailure);
    }
}
//cat access.log | java -jar target/log-analyzer-1.0-SNAPSHOT.jar -u 99.9 -t 45