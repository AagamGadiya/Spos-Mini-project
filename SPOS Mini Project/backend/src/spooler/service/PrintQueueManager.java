package spooler.service;

import spooler.model.PrintJob;
import spooler.model.PrintJobStatus;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * PrintQueueManager - Manages the FIFO print job queue.
 *
 * SPOS CONCEPT: QUEUE MANAGEMENT + CRITICAL SECTION
 *
 * This class implements a FIFO (First-Come, First-Served)
 * scheduling queue for print jobs. This is the same principle
 * used in OS process scheduling (FCFS scheduling algorithm).
 *
 * The queue is a SHARED DATA STRUCTURE accessed by:
 *   - Multiple producer threads (users submitting jobs)
 *   - The printer worker thread (consuming jobs)
 *
 * Thread Safety: A ReentrantLock protects all queue operations.
 * Every method that reads or modifies the queue must first
 * acquire the lock. This prevents RACE CONDITIONS such as:
 *   - Two threads adding a job at the same position
 *   - A thread reading a job while another removes it
 *   - Inconsistent queue size reporting
 */
public class PrintQueueManager {

    /**
     * The print queue - a LinkedList used as a FIFO queue.
     *
     * We use LinkedList (which implements Deque) rather than
     * java.util.concurrent.BlockingQueue because:
     * 1. We want explicit lock control for educational demonstration
     * 2. We need to iterate and cancel specific jobs
     * 3. We want to show the lock/unlock pattern clearly
     */
    private final LinkedList<PrintJob> queue;

    /**
     * Lock protecting all queue operations.
     * ReentrantLock is used instead of synchronized because:
     * 1. It provides tryLock() for non-blocking lock attempts
     * 2. Its lock state can be queried (isLocked(), getQueueLength())
     * 3. These features are useful for the Synchronization Monitor display
     */
    private final ReentrantLock queueLock;

    /** List of ALL jobs ever submitted (for history tracking) */
    private final List<PrintJob> allJobs;

    private final ActivityLogger logger;

    public PrintQueueManager(ActivityLogger logger) {
        this.queue = new LinkedList<>();
        this.queueLock = new ReentrantLock(true); // fair lock: FIFO thread ordering
        this.allJobs = new ArrayList<>();
        this.logger = logger;
    }

    /**
     * Add a job to the end of the queue (FIFO).
     *
     * --- CRITICAL SECTION: Queue modification ---
     * Must acquire queueLock before adding to queue.
     * This prevents race conditions when multiple
     * threads submit jobs simultaneously.
     */
    public void addJob(PrintJob job) {
        queueLock.lock(); // << ENTER CRITICAL SECTION
        try {
            queue.addLast(job);
            allJobs.add(job);
            logger.logSubmit(job.getJobId() + " added to queue (position " + queue.size() + ")");
            logger.logSync("Queue lock acquired by thread: " + Thread.currentThread().getName() + " for ADD operation");
        } finally {
            queueLock.unlock(); // << EXIT CRITICAL SECTION
            logger.logSync("Queue lock released by thread: " + Thread.currentThread().getName());
        }
    }

    /**
     * Remove and return the next job from the head of the queue (FIFO order).
     *
     * --- CRITICAL SECTION: Queue read + modification ---
     * The poll operation must be atomic to prevent
     * two printer workers from getting the same job.
     *
     * @return The next PrintJob, or null if the queue is empty.
     */
    public PrintJob getNextJob() {
        queueLock.lock(); // << ENTER CRITICAL SECTION
        try {
            PrintJob job = queue.pollFirst();
            if (job != null) {
                logger.logPrint(job.getJobId() + " removed from queue for printing");
                logger.logSync("Queue lock acquired by thread: " + Thread.currentThread().getName() + " for REMOVE operation");
            }
            return job;
        } finally {
            queueLock.unlock(); // << EXIT CRITICAL SECTION
        }
    }

    /**
     * Cancel a specific job in the queue.
     * Can only cancel jobs with QUEUED status (not yet printing).
     *
     * @return true if the job was found and cancelled, false otherwise.
     */
    public boolean cancelJob(String jobId) {
        queueLock.lock(); // << ENTER CRITICAL SECTION
        try {
            for (int i = 0; i < queue.size(); i++) {
                PrintJob job = queue.get(i);
                if (job.getJobId().equals(jobId)) {
                    if (job.getStatus() == PrintJobStatus.QUEUED) {
                        job.setStatus(PrintJobStatus.CANCELLED);
                        job.setCompletionTime(java.time.LocalDateTime.now());
                        queue.remove(i);
                        logger.logCancel(jobId + " cancelled and removed from queue");
                        return true;
                    } else {
                        logger.logError("Cannot cancel " + jobId + ": status is " + job.getStatus());
                        return false;
                    }
                }
            }
            logger.logError("Job " + jobId + " not found in queue");
            return false;
        } finally {
            queueLock.unlock(); // << EXIT CRITICAL SECTION
        }
    }

    /**
     * Get a snapshot of the current queue for display purposes.
     * Returns a copy to avoid concurrent modification issues.
     */
    public List<PrintJob> getQueueSnapshot() {
        queueLock.lock();
        try {
            return new ArrayList<>(queue);
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Get the current queue size.
     */
    public int getQueueSize() {
        queueLock.lock();
        try {
            return queue.size();
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Check if the queue is empty.
     */
    public boolean isEmpty() {
        queueLock.lock();
        try {
            return queue.isEmpty();
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Get a specific job by ID from the complete job history.
     */
    public PrintJob getJobById(String jobId) {
        synchronized (allJobs) {
            for (PrintJob job : allJobs) {
                if (job.getJobId().equals(jobId)) {
                    return job;
                }
            }
        }
        return null;
    }

    /**
     * Get all jobs ever submitted (for history display).
     */
    public List<PrintJob> getAllJobs() {
        synchronized (allJobs) {
            return new ArrayList<>(allJobs);
        }
    }

    /**
     * Get the queue lock for synchronization monitoring.
     * Used by the SynchronizationMonitor to report lock status.
     */
    public ReentrantLock getQueueLock() {
        return queueLock;
    }

    /**
     * Clear the queue and job history (for system reset).
     */
    public void reset() {
        queueLock.lock();
        try {
            for (PrintJob job : queue) {
                job.setStatus(PrintJobStatus.CANCELLED);
                job.setCompletionTime(java.time.LocalDateTime.now());
            }
            queue.clear();
        } finally {
            queueLock.unlock();
        }
        synchronized (allJobs) {
            allJobs.clear();
        }
        logger.logSystem("Print queue and job history cleared");
    }

    /**
     * Convert the current queue to a JSON array string.
     */
    public String queueToJsonArray() {
        queueLock.lock();
        try {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < queue.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(queue.get(i).toJson());
            }
            sb.append("]");
            return sb.toString();
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Convert all jobs to a JSON array string.
     */
    public String allJobsToJsonArray() {
        synchronized (allJobs) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < allJobs.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(allJobs.get(i).toJson());
            }
            sb.append("]");
            return sb.toString();
        }
    }
}
