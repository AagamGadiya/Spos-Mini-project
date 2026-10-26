package spooler.service;

import spooler.model.PrintJob;
import spooler.model.PrintJobStatus;
import spooler.model.Printer;
import spooler.model.PrinterStatus;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * PrinterSpooler - THE CORE SPOS CLASS implementing printer spooler logic.
 *
 * ===== PRINTER SPOOLER - SPOS CORE =====
 *
 * This class is the HEART of the Printer Spooler System.
 * It implements the following OS concepts:
 *
 * 1. MUTUAL EXCLUSION:
 *    Only one print job can access the printer at any time.
 *    Enforced via ReentrantLock (printerLock).
 *
 * 2. CRITICAL SECTION:
 *    The code between printerLock.lock() and printerLock.unlock()
 *    is the critical section where the shared printer resource
 *    is accessed.
 *
 * 3. PRODUCER-CONSUMER PATTERN:
 *    - Producers: User threads that submit print jobs
 *    - Consumer: The printer worker thread that processes jobs
 *    - Buffer: The print queue (FIFO)
 *    - Signaling: Condition variable (jobAvailable)
 *
 * 4. THREAD SYNCHRONIZATION:
 *    The Condition variable (jobAvailable) is used to coordinate:
 *    - Producer signals consumer when a new job arrives
 *    - Consumer waits when the queue is empty
 *    This avoids busy-waiting (polling), which wastes CPU.
 *
 * 5. SHARED RESOURCE MANAGEMENT:
 *    The Printer object is the shared resource.
 *    The printerLock ensures safe, exclusive access.
 *
 * Synchronization Mechanism:
 *    ReentrantLock + Condition (preferred over synchronized
 *    because lock state can be inspected for monitoring).
 */
public class PrinterSpooler {

    private final Printer printer;
    private final PrintQueueManager queueManager;
    private final StatisticsManager statsManager;
    private final ActivityLogger logger;

    /**
     * Lock for the printer resource - implements MUTUAL EXCLUSION.
     * Fair ordering (true) ensures threads acquire the lock in FIFO order,
     * preventing starvation.
     */
    private final ReentrantLock printerLock;

    /**
     * Condition variable for producer-consumer signaling.
     * - Producer (submitJob) calls jobAvailable.signal() after adding a job.
     * - Consumer (printer worker) calls jobAvailable.await() when queue is empty.
     */
    private final Condition jobAvailable;

    /** Thread pool for the printer worker and simulation threads */
    private final ExecutorService printerWorkerPool;

    /** Atomic counter for generating unique job IDs (thread-safe) */
    private final AtomicInteger jobCounter;

    /** Tracks the number of concurrently active submission threads */
    private final AtomicInteger activeThreadCount;

    /** Tracks total concurrent submissions for statistics */
    private final AtomicLong totalConcurrentSubmissions;

    /** Flag indicating if the printer worker is running */
    private volatile boolean workerRunning;

    /** Flag indicating if the system is shutting down */
    private volatile boolean shuttingDown;

    /** Configurable milliseconds per page for print simulation */
    private volatile long msPerPage;

    /** Flag indicating if the printer is paused */
    private volatile boolean paused;

    /** Lock for pause/resume signaling */
    private final ReentrantLock pauseLock;
    private final Condition pauseCondition;

    // Speed presets
    public static final long SPEED_FAST = 200;     // 200ms per page
    public static final long SPEED_NORMAL = 500;    // 500ms per page
    public static final long SPEED_SLOW = 1000;     // 1000ms per page

    public PrinterSpooler(ActivityLogger logger) {
        this.printer = new Printer("PRN-001", "SPOS Virtual Printer");
        this.queueManager = new PrintQueueManager(logger);
        this.statsManager = new StatisticsManager();
        this.logger = logger;

        // Initialize synchronization primitives
        this.printerLock = new ReentrantLock(true); // fair lock
        this.jobAvailable = printerLock.newCondition();

        this.pauseLock = new ReentrantLock();
        this.pauseCondition = pauseLock.newCondition();

        // Thread pool: 1 thread for the printer worker
        this.printerWorkerPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PrinterWorker");
            t.setDaemon(true);
            return t;
        });

        this.jobCounter = new AtomicInteger(0);
        this.activeThreadCount = new AtomicInteger(0);
        this.totalConcurrentSubmissions = new AtomicLong(0);
        this.workerRunning = false;
        this.shuttingDown = false;
        this.msPerPage = SPEED_NORMAL; // Default: 500ms per page
        this.paused = false;

        logger.logSystem("PrinterSpooler initialized with ReentrantLock (fair=true) + Condition variable");
        logger.logSystem("Printer: " + printer.getPrinterName() + " (" + printer.getPrinterId() + ")");
    }

    // ===================================================================
    //  JOB SUBMISSION (Producer side of producer-consumer)
    // ===================================================================

    /**
     * Submit a new print job to the spooler.
     *
     * This method is called by producer threads (user threads).
     * It is thread-safe: multiple threads can call this concurrently.
     *
     * ALGORITHM (corresponds to Pseudocode A and B):
     *   1. Validate input parameters
     *   2. Generate unique job ID (atomic operation)
     *   3. Create PrintJob object
     *   4. Add job to queue (queue lock acquired internally)
     *   5. Signal the printer worker that a new job is available
     *   6. Update statistics
     *   7. Return the created job
     *
     * @param userId User's ID
     * @param userName User's name
     * @param documentName Name of the document to print
     * @param pages Number of pages
     * @return The created PrintJob
     * @throws IllegalArgumentException if input is invalid
     */
    public PrintJob submitJob(String userId, String userName, String documentName, int pages) {
        // Track active threads for synchronization monitoring
        activeThreadCount.incrementAndGet();
        totalConcurrentSubmissions.incrementAndGet();

        try {
            // Step 1: Validate input
            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalArgumentException("User ID is required");
            }
            if (userName == null || userName.trim().isEmpty()) {
                throw new IllegalArgumentException("User name is required");
            }
            if (documentName == null || documentName.trim().isEmpty()) {
                throw new IllegalArgumentException("Document name is required");
            }
            if (pages <= 0) {
                throw new IllegalArgumentException("Pages must be a positive integer");
            }
            if (pages > 1000) {
                throw new IllegalArgumentException("Maximum 1000 pages per job");
            }

            // Step 2: Generate unique job ID (AtomicInteger ensures thread safety)
            String jobId = String.format("JOB-%03d", jobCounter.incrementAndGet());

            // Step 3: Create PrintJob
            PrintJob job = new PrintJob(jobId, userId.trim(), userName.trim(),
                    documentName.trim(), pages, msPerPage);

            logger.logSubmit("User " + userId + " (" + userName + ") submitted " + jobId
                    + " [" + documentName + ", " + pages + " pages]"
                    + " (Thread: " + Thread.currentThread().getName() + ")");

            // Step 4: Add job to queue
            // (PrintQueueManager.addJob() acquires its own lock internally)
            queueManager.addJob(job);

            // Step 5: Signal the printer worker that a new job is available
            //
            // PRODUCER-CONSUMER SIGNALING:
            // The producer acquires the printerLock and signals
            // the jobAvailable condition. The printer worker
            // thread, if waiting, will wake up and process the job.
            //
            printerLock.lock();
            try {
                jobAvailable.signal(); // Wake up the printer worker
                logger.logSync("jobAvailable.signal() called by " + Thread.currentThread().getName());
            } finally {
                printerLock.unlock();
            }

            // Step 6: Update statistics
            statsManager.recordJobSubmitted();

            return job;

        } finally {
            activeThreadCount.decrementAndGet();
        }
    }

    // ===================================================================
    //  PRINTER WORKER (Consumer side of producer-consumer)
    // ===================================================================

    /**
     * Start the printer worker thread.
     *
     * This is the CONSUMER in the producer-consumer model.
     * It runs in a loop, waiting for jobs and processing them one at a time.
     *
     * ALGORITHM (corresponds to Pseudocode C and D):
     *   1. Wait for a job to be available (Condition.await())
     *   2. Remove the next job from the queue (FIFO order)
     *   3. Acquire the printer resource (printerLock)
     *   4. Set printer status to PRINTING
     *   5. Simulate printing page by page
     *   6. Set job status to COMPLETED
     *   7. Release the printer resource
     *   8. Update statistics
     *   9. Repeat
     */
    public void startPrinterWorker() {
        if (workerRunning) {
            logger.logSystem("Printer worker is already running");
            return;
        }

        workerRunning = true;
        printer.setStatus(PrinterStatus.IDLE);
        logger.logSystem("Printer worker started -- waiting for jobs...");

        printerWorkerPool.submit(() -> {
            while (workerRunning && !shuttingDown) {
                try {
                    PrintJob job = null;

                    // CONSUMER WAITING (Condition Variable)
                    //
                    // The printer worker acquires the printerLock and
                    // checks if the queue is empty. If empty, it calls
                    // jobAvailable.await(), which:
                    //   1. Atomically releases the lock
                    //   2. Puts the thread to sleep
                    //   3. Waits for signal() from a producer
                    //   4. Re-acquires the lock upon waking up
                    //
                    // This is more efficient than busy-waiting (polling),
                    // which would waste CPU cycles.
                    //
                    printerLock.lock();
                    try {
                        while (queueManager.isEmpty() && workerRunning && !shuttingDown) {
                            printer.setStatus(PrinterStatus.IDLE);
                            printer.setCurrentJob(null);
                            logger.logSync("Printer worker calling jobAvailable.await() -- waiting for jobs");
                            jobAvailable.await(); // << WAIT (releases lock, sleeps)
                            logger.logSync("Printer worker woke up from await()");
                        }

                        if (!workerRunning || shuttingDown) break;

                        // Remove next job from queue (FIFO)
                        job = queueManager.getNextJob();
                    } finally {
                        printerLock.unlock();
                    }

                    if (job == null) continue;

                    // CRITICAL SECTION: Printer Resource Access
                    //
                    // Only ONE job can use the printer at a time.
                    // The printerLock ensures MUTUAL EXCLUSION.
                    //
                    // While this job is being printed, no other thread
                    // can start printing another job.
                    //
                    processJob(job);

                } catch (InterruptedException e) {
                    if (!shuttingDown) {
                        logger.logError("Printer worker interrupted: " + e.getMessage());
                    }
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.logError("Printer worker error: " + e.getMessage());
                }
            }
            logger.logSystem("Printer worker stopped");
            printer.setStatus(PrinterStatus.OFFLINE);
        });
    }

    /**
     * Process a single print job -- simulates actual printing.
     *
     * This method represents the CRITICAL SECTION where the shared
     * printer resource is exclusively used by one job.
     *
     * ALGORITHM:
     *   1. Set printer status = PRINTING, assign current job
     *   2. Set job status = PRINTING, record start time
     *   3. Simulate printing page by page (sleep per page)
     *   4. Update progress after each page
     *   5. On completion: set job status = COMPLETED
     *   6. Record statistics
     *   7. Release printer (set IDLE, clear current job)
     */
    private void processJob(PrintJob job) {
        logger.logPrint("Printer acquired for " + job.getJobId()
                + " (Thread: " + Thread.currentThread().getName() + ")");
        logger.logSync("CRITICAL SECTION ENTERED -- Printer resource locked for " + job.getJobId());

        try {
            // Assign the job to the printer
            printer.setStatus(PrinterStatus.PRINTING);
            printer.setCurrentJob(job);

            // Update job status
            job.setStatus(PrintJobStatus.PRINTING);
            job.setStartTime(LocalDateTime.now());

            long waitingTime = job.getWaitingTimeMs();
            statsManager.recordWaitingTime(waitingTime);

            logger.logPrint(job.getJobId() + " started printing -- "
                    + job.getDocumentName() + " (" + job.getPages() + " pages)"
                    + " -- Waited: " + waitingTime + "ms");

            // PRINT SIMULATION
            // Simulate printing page by page.
            // Each page takes msPerPage milliseconds.
            // Progress is updated after each page for the UI.
            for (int page = 1; page <= job.getPages(); page++) {
                // Check if job should stop
                if (!workerRunning || shuttingDown) {
                    job.setStatus(PrintJobStatus.FAILED);
                    job.setCompletionTime(LocalDateTime.now());
                    logger.logError(job.getJobId() + " interrupted during printing");
                    break;
                }

                // Handle pause
                checkPause();

                // Simulate time to print one page
                Thread.sleep(msPerPage);

                // Update printed page count (progress)
                job.setPrintedPages(page);
            }

            // Job completed successfully
            if (job.getStatus() == PrintJobStatus.PRINTING) {
                job.setStatus(PrintJobStatus.COMPLETED);
                job.setCompletionTime(LocalDateTime.now());

                long printingTime = job.getPrintingTimeMs();
                statsManager.recordJobCompleted(job.getPages(), printingTime);
                printer.recordJobCompletion(job.getPages());

                logger.logComplete(job.getJobId() + " completed -- "
                        + job.getPages() + " pages in " + printingTime + "ms");
            }

        } catch (InterruptedException e) {
            job.setStatus(PrintJobStatus.FAILED);
            job.setCompletionTime(LocalDateTime.now());
            statsManager.recordJobFailed();
            logger.logError(job.getJobId() + " failed: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            // Release the printer resource
            printer.setCurrentJob(null);
            printer.setStatus(PrinterStatus.IDLE);
            logger.logSync("CRITICAL SECTION EXITED -- Printer resource released after " + job.getJobId());
            logger.logPrint("Printer released after " + job.getJobId());
        }
    }

    /**
     * Check if the printer is paused and wait if so.
     */
    private void checkPause() throws InterruptedException {
        pauseLock.lock();
        try {
            while (paused) {
                printer.setStatus(PrinterStatus.PAUSED);
                logger.logSystem("Printer paused -- waiting for resume");
                pauseCondition.await();
            }
        } finally {
            pauseLock.unlock();
        }
    }

    // ===================================================================
    //  PRINTER CONTROL METHODS
    // ===================================================================

    /** Pause the printer. Current page will finish but next page will wait. */
    public void pausePrinter() {
        pauseLock.lock();
        try {
            paused = true;
            logger.logSystem("Printer pause requested");
        } finally {
            pauseLock.unlock();
        }
    }

    /** Resume the printer after being paused. */
    public void resumePrinter() {
        pauseLock.lock();
        try {
            paused = false;
            printer.setStatus(PrinterStatus.PRINTING);
            pauseCondition.signalAll();
            logger.logSystem("Printer resumed");
        } finally {
            pauseLock.unlock();
        }
    }

    /**
     * Cancel a queued job.
     * Jobs that are already PRINTING cannot be cancelled.
     */
    public boolean cancelJob(String jobId) {
        PrintJob currentJob = printer.getCurrentJob();
        if (currentJob != null && currentJob.getJobId().equals(jobId)) {
            logger.logError("Cannot cancel " + jobId + ": job is currently being printed");
            return false;
        }

        boolean cancelled = queueManager.cancelJob(jobId);
        if (cancelled) {
            statsManager.recordJobCancelled();
        }
        return cancelled;
    }

    // ===================================================================
    //  CONCURRENT SIMULATION
    // ===================================================================

    /**
     * Run a concurrent simulation with multiple user threads.
     *
     * This method demonstrates CONCURRENCY by creating multiple threads
     * that submit jobs simultaneously to the shared spooler.
     *
     * SPOS Concept: Multiple processes (users) competing for a shared
     * resource (printer) with proper synchronization.
     *
     * @param numUsers Number of simulated users
     * @param jobsPerUser Number of jobs each user will submit
     * @return Total number of jobs submitted
     */
    public int runConcurrentSimulation(int numUsers, int jobsPerUser) {
        logger.logSystem("===================================================");
        logger.logSystem("CONCURRENT SIMULATION STARTED");
        logger.logSystem("Users: " + numUsers + ", Jobs/User: " + jobsPerUser);
        logger.logSystem("Total expected jobs: " + (numUsers * jobsPerUser));
        logger.logSystem("===================================================");

        // Sample document names for simulation
        String[] documents = {
            "Operating Systems Report.pdf", "SPOS Assignment.docx",
            "CN Lab Report.pdf", "DBMS Notes.pdf", "DSA Homework.pdf",
            "AI Project.pdf", "TOC Notes.docx", "SE Report.pdf",
            "Web Tech Lab.pdf", "Compiler Design.pdf"
        };

        // Sample user names for simulation
        String[] userNames = {
            "Aagam", "Yash", "Mudra", "Arjun", "Priya",
            "Rahul", "Sneha", "Vikram", "Ananya", "Karan"
        };

        // Create a thread pool for user simulation
        ExecutorService simulationPool = Executors.newFixedThreadPool(numUsers, r -> {
            Thread t = new Thread(r, "SimUser-" + System.nanoTime() % 1000);
            t.setDaemon(true);
            return t;
        });

        AtomicInteger totalSubmitted = new AtomicInteger(0);

        // Create user threads (Producers)
        for (int u = 0; u < numUsers; u++) {
            final int userIndex = u;
            final String userId = String.format("U%03d", u + 1);
            final String userName = userNames[u % userNames.length];

            simulationPool.submit(() -> {
                logger.logSync("Simulation thread started: " + Thread.currentThread().getName()
                        + " (User: " + userId + " - " + userName + ")");

                for (int j = 0; j < jobsPerUser; j++) {
                    try {
                        String doc = documents[(userIndex * jobsPerUser + j) % documents.length];
                        int pages = (int) (Math.random() * 20) + 1; // 1-20 pages

                        PrintJob job = submitJob(userId, userName, doc, pages);
                        totalSubmitted.incrementAndGet();

                        // Small random delay between submissions (simulates real user behavior)
                        Thread.sleep((long) (Math.random() * 300) + 50);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logger.logError("Simulation error for user " + userId + ": " + e.getMessage());
                    }
                }

                logger.logSync("Simulation thread finished: " + Thread.currentThread().getName());
            });
        }

        // Shutdown the simulation pool (don't wait for completion)
        simulationPool.shutdown();

        return numUsers * jobsPerUser;
    }

    // ===================================================================
    //  CONFIGURATION
    // ===================================================================

    /**
     * Set simulation speed.
     * @param speed One of SPEED_FAST, SPEED_NORMAL, SPEED_SLOW
     */
    public void setSimulationSpeed(long speed) {
        this.msPerPage = speed;
        logger.logSystem("Simulation speed set to " + speed + "ms per page");
    }

    public long getMsPerPage() {
        return msPerPage;
    }

    // ===================================================================
    //  GETTERS
    // ===================================================================

    public Printer getPrinter() { return printer; }
    public PrintQueueManager getQueueManager() { return queueManager; }
    public StatisticsManager getStatsManager() { return statsManager; }
    public ActivityLogger getLogger() { return logger; }
    public ReentrantLock getPrinterLock() { return printerLock; }
    public int getActiveThreadCount() { return activeThreadCount.get(); }
    public long getTotalConcurrentSubmissions() { return totalConcurrentSubmissions.get(); }
    public boolean isWorkerRunning() { return workerRunning; }
    public boolean isPaused() { return paused; }

    // ===================================================================
    //  SYNCHRONIZATION MONITORING
    // ===================================================================

    /**
     * Get synchronization status as a JSON string.
     * Used by the Synchronization Monitor on the frontend.
     */
    public String getSynchronizationStatus() {
        ReentrantLock queueLock = queueManager.getQueueLock();

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"activeThreads\":").append(activeThreadCount.get()).append(",");
        sb.append("\"totalConcurrentSubmissions\":").append(totalConcurrentSubmissions.get()).append(",");
        sb.append("\"queueLockLocked\":").append(queueLock.isLocked()).append(",");
        sb.append("\"queueLockQueueLength\":").append(queueLock.getQueueLength()).append(",");
        sb.append("\"printerLockLocked\":").append(printerLock.isLocked()).append(",");
        sb.append("\"printerLockQueueLength\":").append(printerLock.getQueueLength()).append(",");
        sb.append("\"workerRunning\":").append(workerRunning).append(",");
        sb.append("\"printerPaused\":").append(paused).append(",");
        sb.append("\"printerStatus\":\"").append(printer.getStatus().name()).append("\",");
        sb.append("\"currentJobId\":").append(
            printer.getCurrentJob() != null ? "\"" + printer.getCurrentJob().getJobId() + "\"" : "null"
        ).append(",");
        sb.append("\"queueSize\":").append(queueManager.getQueueSize()).append(",");
        sb.append("\"jobsProcessedSafely\":").append(printer.getTotalJobsPrinted()).append(",");
        sb.append("\"raceConditionsPrevented\":").append(totalConcurrentSubmissions.get());
        sb.append("}");
        return sb.toString();
    }

    // ===================================================================
    //  SYSTEM RESET
    // ===================================================================

    /**
     * Reset the entire system to initial state.
     */
    public void resetSystem() {
        logger.logSystem("System reset initiated");

        workerRunning = false;

        // Signal the worker to wake up if it's waiting
        printerLock.lock();
        try {
            jobAvailable.signalAll();
        } finally {
            printerLock.unlock();
        }

        // Resume if paused
        if (paused) {
            resumePrinter();
        }

        // Wait a moment for the worker to stop
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Reset all components
        queueManager.reset();
        printer.reset();
        statsManager.reset();
        jobCounter.set(0);
        activeThreadCount.set(0);
        totalConcurrentSubmissions.set(0);
        paused = false;

        logger.logSystem("System reset complete");

        // Restart the worker
        startPrinterWorker();
    }

    /**
     * Shutdown the spooler (for application exit).
     */
    public void shutdown() {
        shuttingDown = true;
        workerRunning = false;

        printerLock.lock();
        try {
            jobAvailable.signalAll();
        } finally {
            printerLock.unlock();
        }

        if (paused) {
            resumePrinter();
        }

        printerWorkerPool.shutdownNow();
        logger.logSystem("PrinterSpooler shut down");
    }
}
