package emergency.model;

/** An emergency call: where it is, what kind it is and how severe. */
public final class Incident {

    private final String id;
    private final double x;
    private final double y;
    private final IncidentType type;
    private final Severity severity;

    public Incident(String id, double x, double y,
                    IncidentType type, Severity severity) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.type = type;
        this.severity = severity;
    }

    public String id() { return id; }
    public double x() { return x; }
    public double y() { return y; }
    public IncidentType type() { return type; }
    public Severity severity() { return severity; }

    @Override
    public String toString() {
        return String.format("Incident %s: %s (%s) at (%.1f, %.1f)",
                id, type, severity.label(), x, y);
    }
}
