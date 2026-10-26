package spooler.service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * StatisticsManager - Tracks system-wide performance and usage statistics.
 * 
 * SPOS Concept: This is analogous to OS performance monitoring tools
 * (like top, htop, Task Manager) that track CPU utilization, process
 * counts, waiting times, etc.
 * 
 * Thread Safety: Uses AtomicInteger/AtomicLong for thread-safe counters
 * without explicit locking. These use CPU-level compare-and-swap (CAS)
 * operations for lock-free thread safety.
 */
public class StatisticsManager {

    private final AtomicInteger totalJobsSubmitted;
    private final AtomicInteger completedJobs;
    private final AtomicInteger cancelledJobs;
    private final AtomicInteger failedJobs;
    private final AtomicLong totalPagesCompleted;
    private final AtomicLong totalWaitingTimeMs;
    private final AtomicLong totalPrintingTimeMs;
    private final AtomicInteger waitingTimeSamples;
    private final AtomicInteger printingTimeSamples;

    /** Timestamp when the system started (for utilization calculation) */
    private volatile long systemStartTimeMs;

    /** Total time the printer has been actively printing */
    private final AtomicLong totalBusyTimeMs;

    public StatisticsManager() {
        this.totalJobsSubmitted = new AtomicInteger(0);
        this.completedJobs = new AtomicInteger(0);
        this.cancelledJobs = new AtomicInteger(0);
        this.failedJobs = new AtomicInteger(0);
        this.totalPagesCompleted = new AtomicLong(0);
        this.totalWaitingTimeMs = new AtomicLong(0);
        this.totalPrintingTimeMs = new AtomicLong(0);
        this.waitingTimeSamples = new AtomicInteger(0);
        this.printingTimeSamples = new AtomicInteger(0);
        this.systemStartTimeMs = System.currentTimeMillis();
        this.totalBusyTimeMs = new AtomicLong(0);
    }

    // --- Recording methods (called from other threads) ---

    public void recordJobSubmitted() {
        totalJobsSubmitted.incrementAndGet();
    }

    public void recordJobCompleted(int pages, long printingTimeMs) {
        completedJobs.incrementAndGet();
        totalPagesCompleted.addAndGet(pages);
        totalPrintingTimeMs.addAndGet(printingTimeMs);
        printingTimeSamples.incrementAndGet();
        totalBusyTimeMs.addAndGet(printingTimeMs);
    }

    public void recordJobCancelled() {
        cancelledJobs.incrementAndGet();
    }

    public void recordJobFailed() {
        failedJobs.incrementAndGet();
    }

    public void recordWaitingTime(long waitingTimeMs) {
        totalWaitingTimeMs.addAndGet(waitingTimeMs);
        waitingTimeSamples.incrementAndGet();
    }

    // --- Computed statistics ---

    public int getTotalJobsSubmitted() { return totalJobsSubmitted.get(); }
    public int getCompletedJobs() { return completedJobs.get(); }
    public int getCancelledJobs() { return cancelledJobs.get(); }
    public int getFailedJobs() { return failedJobs.get(); }
    public long getTotalPagesCompleted() { return totalPagesCompleted.get(); }

    public int getPendingJobs() {
        return totalJobsSubmitted.get() - completedJobs.get() - cancelledJobs.get() - failedJobs.get();
    }

    /**
     * Average waiting time in milliseconds.
     */
    public long getAverageWaitingTimeMs() {
        int samples = waitingTimeSamples.get();
        if (samples == 0) return 0;
        return totalWaitingTimeMs.get() / samples;
    }

    /**
     * Average printing time in milliseconds.
     */
    public long getAveragePrintingTimeMs() {
        int samples = printingTimeSamples.get();
        if (samples == 0) return 0;
        return totalPrintingTimeMs.get() / samples;
    }

    /**
     * Printer utilization percentage (0-100).
     * Calculated as: (total busy time / total elapsed time) * 100
     */
    public double getPrinterUtilization() {
        long elapsed = System.currentTimeMillis() - systemStartTimeMs;
        if (elapsed <= 0) return 0.0;
        double utilization = (totalBusyTimeMs.get() * 100.0) / elapsed;
        return Math.min(utilization, 100.0); // Cap at 100%
    }

    /**
     * Reset all statistics.
     */
    public void reset() {
        totalJobsSubmitted.set(0);
        completedJobs.set(0);
        cancelledJobs.set(0);
        failedJobs.set(0);
        totalPagesCompleted.set(0);
        totalWaitingTimeMs.set(0);
        totalPrintingTimeMs.set(0);
        waitingTimeSamples.set(0);
        printingTimeSamples.set(0);
        systemStartTimeMs = System.currentTimeMillis();
        totalBusyTimeMs.set(0);
    }

    /**
     * Convert statistics to JSON string.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"totalJobsSubmitted\":").append(getTotalJobsSubmitted()).append(",");
        sb.append("\"completedJobs\":").append(getCompletedJobs()).append(",");
        sb.append("\"cancelledJobs\":").append(getCancelledJobs()).append(",");
        sb.append("\"failedJobs\":").append(getFailedJobs()).append(",");
        sb.append("\"pendingJobs\":").append(getPendingJobs()).append(",");
        sb.append("\"totalPagesCompleted\":").append(getTotalPagesCompleted()).append(",");
        sb.append("\"averageWaitingTimeMs\":").append(getAverageWaitingTimeMs()).append(",");
        sb.append("\"averagePrintingTimeMs\":").append(getAveragePrintingTimeMs()).append(",");
        sb.append("\"printerUtilization\":").append(String.format("%.1f", getPrinterUtilization()));
        sb.append("}");
        return sb.toString();
    }
}
