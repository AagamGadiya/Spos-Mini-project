package spooler.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PrintJob - Represents a single print job in the spooler system.
 * 
 * SPOS Concept: A print job is analogous to a process/task in an OS.
 * Like OS processes, print jobs have:
 *   - A unique identifier (jobId ~ PID)
 *   - A state (status ~ process state: READY, RUNNING, TERMINATED)
 *   - Timestamps for lifecycle tracking
 *   - Resource requirements (pages to print)
 * 
 * The job transitions through states similar to a process lifecycle:
 *   QUEUED (Ready) → PRINTING (Running) → COMPLETED (Terminated)
 */
public class PrintJob {
    private final String jobId;
    private final String userId;
    private final String userName;
    private final String documentName;
    private final int pages;
    private final LocalDateTime submissionTime;
    private volatile LocalDateTime startTime;
    private volatile LocalDateTime completionTime;
    private volatile PrintJobStatus status;
    private volatile int printedPages; // Track progress during printing
    private final long estimatedPrintTimeMs; // Estimated total print time in milliseconds

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PrintJob(String jobId, String userId, String userName, String documentName, int pages, long msPerPage) {
        this.jobId = jobId;
        this.userId = userId;
        this.userName = userName;
        this.documentName = documentName;
        this.pages = pages;
        this.submissionTime = LocalDateTime.now();
        this.status = PrintJobStatus.QUEUED;
        this.printedPages = 0;
        this.estimatedPrintTimeMs = (long) pages * msPerPage;
    }

    // --- Getters ---

    public String getJobId() { return jobId; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getDocumentName() { return documentName; }
    public int getPages() { return pages; }
    public LocalDateTime getSubmissionTime() { return submissionTime; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getCompletionTime() { return completionTime; }
    public PrintJobStatus getStatus() { return status; }
    public int getPrintedPages() { return printedPages; }
    public long getEstimatedPrintTimeMs() { return estimatedPrintTimeMs; }

    // --- Setters for mutable fields ---

    public void setStatus(PrintJobStatus status) { this.status = status; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public void setCompletionTime(LocalDateTime completionTime) { this.completionTime = completionTime; }
    public void setPrintedPages(int printedPages) { this.printedPages = printedPages; }

    /**
     * Calculate progress percentage (0-100).
     */
    public int getProgressPercent() {
        if (pages == 0) return 100;
        return (int) ((printedPages * 100.0) / pages);
    }

    /**
     * Calculate waiting time in milliseconds (time between submission and start).
     */
    public long getWaitingTimeMs() {
        if (startTime == null) return 0;
        return java.time.Duration.between(submissionTime, startTime).toMillis();
    }

    /**
     * Calculate actual printing time in milliseconds.
     */
    public long getPrintingTimeMs() {
        if (startTime == null) return 0;
        LocalDateTime end = (completionTime != null) ? completionTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end).toMillis();
    }

    /**
     * Converts this PrintJob to a JSON string for API responses.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"jobId\":\"").append(escapeJson(jobId)).append("\",");
        sb.append("\"userId\":\"").append(escapeJson(userId)).append("\",");
        sb.append("\"userName\":\"").append(escapeJson(userName)).append("\",");
        sb.append("\"documentName\":\"").append(escapeJson(documentName)).append("\",");
        sb.append("\"pages\":").append(pages).append(",");
        sb.append("\"submissionTime\":\"").append(submissionTime.format(FORMATTER)).append("\",");
        sb.append("\"startTime\":").append(startTime != null ? "\"" + startTime.format(FORMATTER) + "\"" : "null").append(",");
        sb.append("\"completionTime\":").append(completionTime != null ? "\"" + completionTime.format(FORMATTER) + "\"" : "null").append(",");
        sb.append("\"status\":\"").append(status.name()).append("\",");
        sb.append("\"statusDisplay\":\"").append(status.getDisplayName()).append("\",");
        sb.append("\"printedPages\":").append(printedPages).append(",");
        sb.append("\"progressPercent\":").append(getProgressPercent()).append(",");
        sb.append("\"estimatedPrintTimeMs\":").append(estimatedPrintTimeMs).append(",");
        sb.append("\"waitingTimeMs\":").append(getWaitingTimeMs()).append(",");
        sb.append("\"printingTimeMs\":").append(getPrintingTimeMs());
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return "PrintJob{jobId='" + jobId + "', doc='" + documentName + "', status=" + status + "}";
    }
}
