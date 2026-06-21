package emergency.model;

import java.util.ArrayList;
import java.util.List;

/** The set of resources allocated to a single incident plus its owning zone. */
public final class DispatchPlan {

    private final Incident incident;
    private Zone zone;
    private final List<EmergencyResource> ambulances = new ArrayList<EmergencyResource>();
    private EmergencyResource hospital;
    private EmergencyResource fireTruck;
    private EmergencyResource police;
    private final List<String> unmet = new ArrayList<String>();

    public DispatchPlan(Incident incident) {
        this.incident = incident;
    }

    public void setZone(Zone z) { this.zone = z; }
    public void addAmbulance(EmergencyResource a) { ambulances.add(a); }
    public void setHospital(EmergencyResource h) { this.hospital = h; }
    public void setFireTruck(EmergencyResource f) { this.fireTruck = f; }
    public void setPolice(EmergencyResource p) { this.police = p; }
    public void addUnmet(String reason) { unmet.add(reason); }

    public Incident incident() { return incident; }
    public Zone zone() { return zone; }
    public List<EmergencyResource> ambulances() { return ambulances; }
    public EmergencyResource hospital() { return hospital; }
    public EmergencyResource fireTruck() { return fireTruck; }
    public EmergencyResource police() { return police; }
    public List<String> unmet() { return unmet; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(incident).append('\n');
        sb.append("   Zone: ").append(zone == null ? "outside mapped sectors" : zone.name()).append('\n');
        sb.append("   Dispatched:\n");
        for (EmergencyResource a : ambulances) {
            sb.append(distLine("Ambulance", a)).append('\n');
        }
        if (hospital != null) {
            sb.append(distLine("Hospital (ICU)", hospital)).append('\n');
        }
        if (fireTruck != null) {
            sb.append(distLine("Fire truck", fireTruck)).append('\n');
        }
        if (police != null) {
            sb.append(distLine("Police unit", police)).append('\n');
        }
        for (String u : unmet) {
            sb.append("      ! ").append(u).append('\n');
        }
        return sb.toString();
    }

    private String distLine(String role, EmergencyResource r) {
        double dx = r.x() - incident.x();
        double dy = r.y() - incident.y();
        double d = Math.sqrt(dx * dx + dy * dy);
        return String.format("      - %-15s %-5s %-18s (%.2f km)",
                role, r.id(), r.name(), d);
    }
}
