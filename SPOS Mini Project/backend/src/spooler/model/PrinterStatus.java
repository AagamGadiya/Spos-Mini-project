package spooler.model;

/**
 * PrinterStatus - Enum representing the states of the shared printer resource.
 * 
 * SPOS Concept: The printer is a shared resource (like a CPU or I/O device).
 * Its status determines whether new jobs can be dispatched to it.
 * Only when the printer is IDLE can a new job be assigned (resource allocation).
 */
public enum PrinterStatus {
    IDLE("Idle", "Printer is available and waiting for jobs"),
    PRINTING("Printing", "Printer is actively processing a job"),
    PAUSED("Paused", "Printer is paused by the operator"),
    OFFLINE("Offline", "Printer is not available");

    private final String displayName;
    private final String description;

    PrinterStatus(String displayName, String description) {
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
