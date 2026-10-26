package spooler.model;

/**
 * PrintJobStatus - Enum representing the lifecycle states of a print job.
 * 
 * SPOS Concept: This enum models the state transitions of a process/job
 * in an operating system. Similar to how OS processes move through states
 * (NEW -> READY -> RUNNING -> TERMINATED), print jobs move through:
 * QUEUED -> PRINTING -> COMPLETED
 * 
 * State Transition Diagram:
 * QUEUED ──→ PRINTING ──→ COMPLETED
 * │
 * └──→ CANCELLED
 * PRINTING ──→ FAILED (on error)
 */
public enum PrintJobStatus {
    QUEUED("Queued", "Job is waiting in the print queue"),
    PRINTING("Printing", "Job is currently being printed"),
    COMPLETED("Completed", "Job has been successfully printed"),
    CANCELLED("Cancelled", "Job was cancelled before printing"),
    FAILED("Failed", "Job failed during printing");

    private final String displayName;
    private final String description;

    PrintJobStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
