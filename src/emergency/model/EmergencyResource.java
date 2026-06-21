package emergency.model;

/**
 * A single emergency resource on the city map: an ambulance, a fire truck, a
 * police unit or a hospital. Position is stored in the planar kilometre grid.
 *
 * <p>The {@code available} flag is mutable so the dispatch system can mark a
 * unit busy after it is sent out and free again once it returns, which is what
 * makes dynamic redistribution possible.
 */
public final class EmergencyResource {

    private final String id;
    private final String name;
    private final ResourceType type;
    private final double x;
    private final double y;

    private boolean available;     // mobile units: free to be dispatched
    private int icuBeds;           // hospitals: number of free ICU beds

    public EmergencyResource(String id, String name, ResourceType type,
                             double x, double y) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.x = x;
        this.y = y;
        this.available = true;
        this.icuBeds = 0;
    }

    /** Convenience factory for a hospital with a given number of free ICU beds. */
    public static EmergencyResource hospital(String id, String name,
                                             double x, double y, int icuBeds) {
        EmergencyResource h = new EmergencyResource(id, name,
                ResourceType.HOSPITAL, x, y);
        h.icuBeds = icuBeds;
        return h;
    }

    public String id() { return id; }
    public String name() { return name; }
    public ResourceType type() { return type; }
    public double x() { return x; }
    public double y() { return y; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean a) { this.available = a; }

    public int icuBeds() { return icuBeds; }
    public boolean hasIcu() { return icuBeds > 0; }
    public void reserveIcuBed() { if (icuBeds > 0) icuBeds--; }
    public void releaseIcuBed() { icuBeds++; }

    @Override
    public String toString() {
        String state = (type == ResourceType.HOSPITAL)
                ? (icuBeds + " ICU beds")
                : (available ? "available" : "BUSY");
        return String.format("%-5s %-18s %-11s @ (%.1f, %.1f) [%s]",
                id, name, type, x, y, state);
    }
}
