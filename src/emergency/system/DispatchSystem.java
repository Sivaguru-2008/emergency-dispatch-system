package emergency.system;

import emergency.geometry.Point;
import emergency.geometry.Rectangle;
import emergency.model.DispatchPlan;
import emergency.model.EmergencyResource;
import emergency.model.Incident;
import emergency.model.ResourceType;
import emergency.model.Zone;
import emergency.structures.PointLocation;
import emergency.structures.RangeTree2D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * High-level façade for the emergency dispatch system. It owns the spatial
 * indexes and exposes the operational queries described in the project brief:
 *
 * <ul>
 *   <li>find available ambulances inside a region (orthogonal range search);</li>
 *   <li>find the nearest available ambulance (range-tree nearest neighbour);</li>
 *   <li>list ICU hospitals within a radius (range search + filter);</li>
 *   <li>allocate resources by incident severity;</li>
 *   <li>redistribute ambulances after dispatch;</li>
 *   <li>build a coverage map for planning.</li>
 * </ul>
 *
 * Spatial look-ups go through the {@link RangeTree2D}; incident-to-sector
 * routing goes through {@link PointLocation}.
 */
public final class DispatchSystem {

    private final List<EmergencyResource> resources;
    private final List<Zone> zones;

    private RangeTree2D<EmergencyResource> tree;     // rebuilt on relocation
    private final PointLocation<Zone> locator;       // static once zones are set

    public DispatchSystem(List<EmergencyResource> resources, List<Zone> zones) {
        this.resources = new ArrayList<EmergencyResource>(resources);
        this.zones = new ArrayList<Zone>(zones);

        List<Rectangle> rects = new ArrayList<Rectangle>();
        List<Zone> labels = new ArrayList<Zone>();
        for (Zone z : zones) { rects.add(z.area()); labels.add(z); }
        this.locator = new PointLocation<Zone>(rects, labels);

        rebuildIndex();
    }

    /** (Re)builds the range tree from the current resource positions. */
    private void rebuildIndex() {
        List<Point<EmergencyResource>> pts =
                new ArrayList<Point<EmergencyResource>>();
        for (EmergencyResource r : resources) {
            pts.add(new Point<EmergencyResource>(r.x(), r.y(), r));
        }
        this.tree = new RangeTree2D<EmergencyResource>(pts);
    }

    /* ================================================================== */
    /*  Reusable predicates                                               */
    /* ================================================================== */

    private static Predicate<Point<EmergencyResource>> availableType(ResourceType t) {
        return p -> p.payload().type() == t && p.payload().isAvailable();
    }

    private static final Predicate<Point<EmergencyResource>> ICU_HOSPITAL =
            p -> p.payload().type() == ResourceType.HOSPITAL && p.payload().hasIcu();

    /* ================================================================== */
    /*  Query 1 - available ambulances inside a region                    */
    /* ================================================================== */

    public List<EmergencyResource> availableAmbulancesInRegion(Rectangle region) {
        List<EmergencyResource> out = new ArrayList<EmergencyResource>();
        for (Point<EmergencyResource> p :
                tree.rangeQuery(region, availableType(ResourceType.AMBULANCE))) {
            out.add(p.payload());
        }
        return out;
    }

    /* ================================================================== */
    /*  Query 2 - nearest available ambulance                             */
    /* ================================================================== */

    public EmergencyResource nearestAvailableAmbulance(double x, double y) {
        Point<EmergencyResource> p =
                tree.nearest(x, y, availableType(ResourceType.AMBULANCE));
        return p == null ? null : p.payload();
    }

    private EmergencyResource nearestAvailable(double x, double y, ResourceType t) {
        Point<EmergencyResource> p = tree.nearest(x, y, availableType(t));
        return p == null ? null : p.payload();
    }

    /* ================================================================== */
    /*  Query 3 - ICU hospitals within a radius                           */
    /* ================================================================== */

    public List<EmergencyResource> hospitalsWithIcuWithinRadius(
            double x, double y, double radiusKm) {
        List<EmergencyResource> out = new ArrayList<EmergencyResource>();
        for (Point<EmergencyResource> p :
                tree.withinRadius(x, y, radiusKm, ICU_HOSPITAL)) {
            out.add(p.payload());
        }
        // closest first
        out.sort(Comparator.comparingDouble(h -> dist(h, x, y)));
        return out;
    }

    /* ================================================================== */
    /*  Incident-to-sector routing (point location)                       */
    /* ================================================================== */

    public Zone zoneOf(double x, double y) {
        return locator.locate(x, y);
    }

    /* ================================================================== */
    /*  Query 4 - severity-based allocation                               */
    /* ================================================================== */

    /**
     * Allocates resources to an incident according to its severity and type,
     * marking the chosen mobile units busy and reserving an ICU bed when needed.
     */
    public DispatchPlan allocate(Incident inc) {
        DispatchPlan plan = new DispatchPlan(inc);
        plan.setZone(zoneOf(inc.x(), inc.y()));

        int need = inc.severity().ambulancesNeeded();
        for (int i = 0; i < need; i++) {
            EmergencyResource amb = nearestAvailable(inc.x(), inc.y(),
                    ResourceType.AMBULANCE);
            if (amb == null) {
                plan.addUnmet("No ambulance available (" + (need - i) + " short)");
                break;
            }
            amb.setAvailable(false);            // taken off the board
            plan.addAmbulance(amb);
        }

        if (inc.severity().requiresIcu()) {
            EmergencyResource h = nearestIcuHospital(inc.x(), inc.y());
            if (h != null) { h.reserveIcuBed(); plan.setHospital(h); }
            else plan.addUnmet("No ICU hospital available");
        }

        if (inc.type().needsFireTruck()) {
            EmergencyResource f = nearestAvailable(inc.x(), inc.y(),
                    ResourceType.FIRE_TRUCK);
            if (f != null) { f.setAvailable(false); plan.setFireTruck(f); }
            else plan.addUnmet("No fire truck available");
        }

        if (inc.type().needsPolice()) {
            EmergencyResource pol = nearestAvailable(inc.x(), inc.y(),
                    ResourceType.POLICE_UNIT);
            if (pol != null) { pol.setAvailable(false); plan.setPolice(pol); }
            else plan.addUnmet("No police unit available");
        }

        return plan;
    }

    private EmergencyResource nearestIcuHospital(double x, double y) {
        Point<EmergencyResource> p = tree.nearest(x, y, ICU_HOSPITAL);
        return p == null ? null : p.payload();
    }

    /* ================================================================== */
    /*  Query 5 - dynamic redistribution                                  */
    /* ================================================================== */

    /** Returns a mobile unit to service (e.g. when it finishes a call). */
    public boolean returnToService(String resourceId) {
        for (EmergencyResource r : resources) {
            if (r.id().equals(resourceId)) { r.setAvailable(true); return true; }
        }
        return false;
    }

    /**
     * Rebalances ambulance coverage: any zone left with no available ambulance
     * pulls in the nearest idle ambulance from another sector, which is then
     * physically relocated to that zone's centre. Returns a log of moves.
     */
    public List<String> redistributeCoverage() {
        List<String> log = new ArrayList<String>();
        for (Zone z : zones) {
            if (!availableAmbulancesInRegion(z.area()).isEmpty()) continue;

            double cx = (z.area().minX() + z.area().maxX()) / 2.0;
            double cy = (z.area().minY() + z.area().maxY()) / 2.0;

            EmergencyResource donor = nearestSurplusAmbulance(z);
            if (donor == null) {
                log.add(z.name() + ": uncovered, no surplus ambulance to move");
                continue;
            }
            log.add(String.format("%s: relocating %s from (%.1f,%.1f) to (%.1f,%.1f)",
                    z.name(), donor.id(), donor.x(), donor.y(), cx, cy));
            relocate(donor.id(), cx, cy);
        }
        return log;
    }

    /** Nearest available ambulance that sits in a zone holding more than one. */
    private EmergencyResource nearestSurplusAmbulance(Zone needy) {
        double cx = (needy.area().minX() + needy.area().maxX()) / 2.0;
        double cy = (needy.area().minY() + needy.area().maxY()) / 2.0;
        EmergencyResource best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (EmergencyResource r : resources) {
            if (r.type() != ResourceType.AMBULANCE || !r.isAvailable()) continue;
            Zone home = zoneOf(r.x(), r.y());
            if (home == needy) continue;
            if (home != null && availableAmbulancesInRegion(home.area()).size() <= 1) {
                continue;   // don't strip a zone down to zero
            }
            double d = dist(r, cx, cy);
            if (d < bestD) { bestD = d; best = r; }
        }
        return best;
    }

    /** Moves a resource to a new position and rebuilds the spatial index. */
    public void relocate(String resourceId, double newX, double newY) {
        for (int i = 0; i < resources.size(); i++) {
            EmergencyResource r = resources.get(i);
            if (r.id().equals(resourceId)) {
                EmergencyResource moved = new EmergencyResource(
                        r.id(), r.name(), r.type(), newX, newY);
                moved.setAvailable(r.isAvailable());
                resources.set(i, moved);
                rebuildIndex();
                return;
            }
        }
    }

    /* ================================================================== */
    /*  Query 6 - coverage map for planning                               */
    /* ================================================================== */

    public String coverageReport(double icuRadiusKm) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-16s %-8s %-10s %-18s%n",
                "ZONE", "AMBUL.", "ICU<" + (int) icuRadiusKm + "km", "RESPONSE (centre)"));
        sb.append("   ------------------------------------------------------------\n");
        for (Zone z : zones) {
            double cx = (z.area().minX() + z.area().maxX()) / 2.0;
            double cy = (z.area().minY() + z.area().maxY()) / 2.0;
            int ambulances = availableAmbulancesInRegion(z.area()).size();
            boolean icu = !hospitalsWithIcuWithinRadius(cx, cy, icuRadiusKm).isEmpty();
            EmergencyResource nearest = nearestAvailableAmbulance(cx, cy);
            String resp = (nearest == null) ? "none"
                    : String.format("%s @ %.2f km", nearest.id(), dist(nearest, cx, cy));
            String flag = (ambulances == 0) ? "  <-- UNDER-COVERED" : "";
            sb.append(String.format("   %-13s %-8d %-10s %-18s%s%n",
                    z.name(), ambulances, icu ? "yes" : "NO", resp, flag));
        }
        return sb.toString();
    }

    /* ================================================================== */
    /*  Accessors                                                         */
    /* ================================================================== */

    public List<EmergencyResource> resources() {
        return new ArrayList<EmergencyResource>(resources);
    }

    public List<Zone> zones() {
        return new ArrayList<Zone>(zones);
    }

    public RangeTree2D<EmergencyResource> index() { return tree; }

    private static double dist(EmergencyResource r, double x, double y) {
        double dx = r.x() - x, dy = r.y() - y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
