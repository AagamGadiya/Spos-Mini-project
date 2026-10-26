package spooler.model;

/**
 * Printer - Represents the shared printer resource in the system.
 * 
 * SPOS Concept: The printer is a SHARED RESOURCE that can only be used
 * by one process (print job) at a time. This is the central resource
 * that requires MUTUAL EXCLUSION.
 * 
 * Analogy: Just as a CPU can only execute one process at a time
 * (in a single-core system), the printer can only print one document
 * at a time. The OS must schedule and synchronize access to this resource.
 * 
 * This class is NOT thread-safe by itself. Thread safety is enforced
 * by the PrinterSpooler class using ReentrantLock (external synchronization).
 */
public class Printer {
    private final String printerId;
    private final String printerName;
    private volatile PrinterStatus status;
    private volatile PrintJob currentJob;
    private volatile int totalJobsPrinted;
    private volatile long totalPagesPrinted;

    public Printer(String printerId, String printerName) {
        this.printerId = printerId;
        this.printerName = printerName;
        this.status = PrinterStatus.IDLE;
        this.currentJob = null;
        this.totalJobsPrinted = 0;
        this.totalPagesPrinted = 0;
    }

    // --- Getters ---

    public String getPrinterId() { return printerId; }
    public String getPrinterName() { return printerName; }
    public PrinterStatus getStatus() { return status; }
    public PrintJob getCurrentJob() { return currentJob; }
    public int getTotalJobsPrinted() { return totalJobsPrinted; }
    public long getTotalPagesPrinted() { return totalPagesPrinted; }

    // --- Setters ---

    public void setStatus(PrinterStatus status) { this.status = status; }
    public void setCurrentJob(PrintJob job) { this.currentJob = job; }

    /**
     * Record that a job has been completed.
     * Called after a job finishes printing successfully.
     */
    public void recordJobCompletion(int pagesPrinted) {
        this.totalJobsPrinted++;
        this.totalPagesPrinted += pagesPrinted;
    }

    /**
     * Reset printer statistics.
     */
    public void reset() {
        this.status = PrinterStatus.IDLE;
        this.currentJob = null;
        this.totalJobsPrinted = 0;
        this.totalPagesPrinted = 0;
    }

    /**
     * Converts this Printer to a JSON string.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"printerId\":\"").append(escapeJson(printerId)).append("\",");
        sb.append("\"printerName\":\"").append(escapeJson(printerName)).append("\",");
        sb.append("\"status\":\"").append(status.name()).append("\",");
        sb.append("\"statusDisplay\":\"").append(status.getDisplayName()).append("\",");
        sb.append("\"currentJob\":").append(currentJob != null ? currentJob.toJson() : "null").append(",");
        sb.append("\"totalJobsPrinted\":").append(totalJobsPrinted).append(",");
        sb.append("\"totalPagesPrinted\":").append(totalPagesPrinted);
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
}
