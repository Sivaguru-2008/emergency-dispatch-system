package emergency.model;

/**
 * Incident severity. Each level encodes how many ambulances should be sent and
 * whether an ICU-capable hospital must be reserved, which drives the
 * severity-based allocation policy in the dispatch system.
 */
public enum Severity {
    CRITICAL("Life-threatening", 2, true),
    HIGH("Serious",             1, true),
    MODERATE("Urgent",          1, false),
    LOW("Minor",                1, false);

    private final String label;
    private final int ambulances;
    private final boolean requiresIcu;

    Severity(String label, int ambulances, boolean requiresIcu) {
        this.label = label;
        this.ambulances = ambulances;
        this.requiresIcu = requiresIcu;
    }

    public String label() { return label; }
    public int ambulancesNeeded() { return ambulances; }
    public boolean requiresIcu() { return requiresIcu; }
}
