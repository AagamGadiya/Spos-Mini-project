package spooler.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ActivityLogger - Records all system events for the activity log.
 * 
 * SPOS Concept: This is analogous to system logging in an OS kernel.
 * Every significant event (job submission, lock acquisition, printing
 * start/stop) is recorded with a timestamp, similar to how operating
 * systems maintain audit logs and event traces.
 * 
 * Thread Safety: Uses synchronized methods since multiple threads
 * (user threads, printer worker thread) may log events concurrently.
 */
public class ActivityLogger {

    /**
     * Represents a single log entry with a timestamp and message.
     */
    public static class LogEntry {
        private final String timestamp;
        private final String message;
        private final String type; // INFO, SUBMIT, PRINT, COMPLETE, CANCEL, SYNC, ERROR, SYSTEM

        public LogEntry(String timestamp, String message, String type) {
            this.timestamp = timestamp;
            this.message = message;
            this.type = type;
        }

        public String toJson() {
            return String.format("{\"timestamp\":\"%s\",\"message\":\"%s\",\"type\":\"%s\"}",
                    timestamp, escapeJson(message), type);
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }

    private final List<LogEntry> logs;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_LOG_SIZE = 500;

    public ActivityLogger() {
        this.logs = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Add a log entry. Thread-safe due to synchronized list.
     */
    public void log(String message, String type) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        LogEntry entry = new LogEntry(timestamp, message, type);
        synchronized (logs) {
            logs.add(entry);
            // Trim old entries to prevent unbounded growth
            if (logs.size() > MAX_LOG_SIZE) {
                logs.remove(0);
            }
        }
        // Also print to console for debugging
        System.out.println("[" + timestamp + "] [" + type + "] " + message);
    }

    // Convenience methods for different log types

    public void logInfo(String message) { log(message, "INFO"); }
    public void logSubmit(String message) { log(message, "SUBMIT"); }
    public void logPrint(String message) { log(message, "PRINT"); }
    public void logComplete(String message) { log(message, "COMPLETE"); }
    public void logCancel(String message) { log(message, "CANCEL"); }
    public void logSync(String message) { log(message, "SYNC"); }
    public void logError(String message) { log(message, "ERROR"); }
    public void logSystem(String message) { log(message, "SYSTEM"); }

    /**
     * Get all log entries as a JSON array string.
     * Returns most recent entries first.
     */
    public String toJsonArray() {
        synchronized (logs) {
            StringBuilder sb = new StringBuilder("[");
            // Return in reverse chronological order (newest first)
            for (int i = logs.size() - 1; i >= 0; i--) {
                if (i < logs.size() - 1) sb.append(",");
                sb.append(logs.get(i).toJson());
            }
            sb.append("]");
            return sb.toString();
        }
    }

    /**
     * Get log count.
     */
    public int getLogCount() {
        return logs.size();
    }

    /**
     * Clear all logs.
     */
    public void clear() {
        synchronized (logs) {
            logs.clear();
        }
    }
}
