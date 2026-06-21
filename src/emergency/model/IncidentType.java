package emergency.model;

/** Nature of the emergency, used to decide which support units are dispatched. */
public enum IncidentType {
    ROAD_ACCIDENT(true,  true),
    CARDIAC_ARREST(false, false),
    FIRE(true,  true),
    NATURAL_DISASTER(true, true),
    MEDICAL(false, false),
    CRIME(false, true);

    private final boolean needsFireTruck;
    private final boolean needsPolice;

    IncidentType(boolean needsFireTruck, boolean needsPolice) {
        this.needsFireTruck = needsFireTruck;
        this.needsPolice = needsPolice;
    }

    public boolean needsFireTruck() { return needsFireTruck; }
    public boolean needsPolice() { return needsPolice; }
}
