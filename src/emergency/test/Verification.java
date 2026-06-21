package emergency.test;

import emergency.geometry.Point;
import emergency.geometry.Rectangle;
import emergency.model.EmergencyResource;
import emergency.model.Incident;
import emergency.model.IncidentType;
import emergency.model.ResourceType;
import emergency.model.Severity;
import emergency.model.Zone;
import emergency.structures.RangeTree2D;
import emergency.system.DispatchSystem;
import emergency.system.SampleCity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Self-contained correctness suite (no external test framework). Every
 * spatial-structure result is checked against an independent brute-force
 * O(n) computation, and the system-level behaviours are asserted on the sample
 * city. Run with {@code java emergency.Main test} or {@code emergency.test.Verification}.
 */
public final class Verification {

    private int passed = 0;
    private int failed = 0;

    public static void main(String[] args) {
        System.exit(new Verification().run());
    }

    /** Runs the whole suite; returns the number of failed checks. */
    public int run() {
        line();
        System.out.println(" SELF-TEST SUITE");
        line();

        rangeQueriesMatchBruteForce();
        nearestMatchesBruteForce();
        radiusMatchesBruteForce();
        emptyTree();
        singlePoint();
        duplicateCoordinates();
        pointLocation();
        allocationMarksUnitsBusy();
        redistributionCoversEmptyZone();

        System.out.println();
        System.out.printf("   RESULT: %d passed, %d failed%n", passed, failed);
        System.out.println(failed == 0 ? "   ALL CHECKS PASSED" : "   *** FAILURES PRESENT ***");
        return failed;
    }

    /* ------------------------------------------------------------------ */
    /*  Spatial structure tests                                           */
    /* ------------------------------------------------------------------ */

    private void rangeQueriesMatchBruteForce() {
        Random rng = new Random(7);
        boolean ok = true;
        for (int t = 0; t < 300 && ok; t++) {
            List<Point<Integer>> pts = randomPoints(rng, 1 + rng.nextInt(120), 40);
            RangeTree2D<Integer> tree = new RangeTree2D<Integer>(pts);
            for (int q = 0; q < 20; q++) {
                Rectangle rect = randomRect(rng, 40);
                List<String> got = keys(tree.rangeQuery(rect));
                List<String> exp = bruteRange(pts, rect);
                Collections.sort(got);
                Collections.sort(exp);
                if (!got.equals(exp)) { ok = false; break; }
            }
        }
        check("range query == brute force (6000 queries, with duplicates)", ok);
    }

    private void nearestMatchesBruteForce() {
        Random rng = new Random(11);
        boolean ok = true;
        for (int t = 0; t < 300 && ok; t++) {
            List<Point<Integer>> pts = randomPoints(rng, 1 + rng.nextInt(120), 40);
            RangeTree2D<Integer> tree = new RangeTree2D<Integer>(pts);
            for (int q = 0; q < 10; q++) {
                double qx = rng.nextInt(40), qy = rng.nextInt(40);
                Point<Integer> got = tree.nearest(qx, qy, p -> true);
                double g = (got == null) ? Double.POSITIVE_INFINITY : got.distanceTo(qx, qy);
                double e = bruteNearest(pts, qx, qy);
                if (Math.abs(g - e) > 1e-9) { ok = false; break; }
            }
        }
        check("nearest neighbour == brute force (3000 queries)", ok);
    }

    private void radiusMatchesBruteForce() {
        Random rng = new Random(13);
        boolean ok = true;
        for (int t = 0; t < 200 && ok; t++) {
            List<Point<Integer>> pts = randomPoints(rng, 1 + rng.nextInt(80), 40);
            RangeTree2D<Integer> tree = new RangeTree2D<Integer>(pts);
            for (int q = 0; q < 10; q++) {
                double qx = rng.nextInt(40), qy = rng.nextInt(40), r = 1 + rng.nextInt(15);
                List<String> got = keys(tree.withinRadius(qx, qy, r, p -> true));
                List<String> exp = new ArrayList<String>();
                for (Point<Integer> p : pts) {
                    if (p.distanceTo(qx, qy) <= r) exp.add(key(p));
                }
                Collections.sort(got);
                Collections.sort(exp);
                if (!got.equals(exp)) { ok = false; break; }
            }
        }
        check("radius query == brute force circle (2000 queries)", ok);
    }

    private void emptyTree() {
        RangeTree2D<Integer> tree =
                new RangeTree2D<Integer>(new ArrayList<Point<Integer>>());
        boolean ok = tree.size() == 0
                && tree.rangeQuery(new Rectangle(0, 0, 10, 10)).isEmpty()
                && tree.nearest(5, 5, p -> true) == null;
        check("empty tree behaves (no points, null nearest)", ok);
    }

    private void singlePoint() {
        List<Point<Integer>> pts = new ArrayList<Point<Integer>>();
        pts.add(new Point<Integer>(5, 5, 1));
        RangeTree2D<Integer> tree = new RangeTree2D<Integer>(pts);
        boolean inside = tree.rangeQuery(new Rectangle(0, 0, 10, 10)).size() == 1;
        boolean outside = tree.rangeQuery(new Rectangle(6, 6, 10, 10)).isEmpty();
        boolean nn = tree.nearest(0, 0, p -> true) != null;
        check("single-point tree (inside / outside / nearest)", inside && outside && nn);
    }

    private void duplicateCoordinates() {
        // Four points stacked on the same coordinate must all be reported.
        List<Point<Integer>> pts = new ArrayList<Point<Integer>>();
        for (int i = 0; i < 4; i++) pts.add(new Point<Integer>(5, 5, i));
        pts.add(new Point<Integer>(9, 9, 99));
        RangeTree2D<Integer> tree = new RangeTree2D<Integer>(pts);
        int hits = tree.rangeQuery(new Rectangle(5, 5, 5, 5)).size();
        check("duplicate coordinates all reported (expected 4)", hits == 4);
    }

    /* ------------------------------------------------------------------ */
    /*  System-level tests                                                */
    /* ------------------------------------------------------------------ */

    private void pointLocation() {
        DispatchSystem city = SampleCity.builtIn();
        Zone sw = city.zoneOf(5, 5);
        Zone ne = city.zoneOf(20, 20);
        Zone outside = city.zoneOf(100, 100);
        boolean ok = sw != null && sw.name().equals("South-West")
                  && ne != null && ne.name().equals("North-East")
                  && outside == null;
        check("point location routes incidents to the right sector", ok);
    }

    private void allocationMarksUnitsBusy() {
        DispatchSystem city = SampleCity.builtIn();
        Incident inc = new Incident("T", 19, 24, IncidentType.ROAD_ACCIDENT, Severity.CRITICAL);
        int before = countAvailableAmbulances(city);
        city.allocate(inc);
        int after = countAvailableAmbulances(city);
        // CRITICAL needs 2 ambulances; both must now be unavailable.
        boolean ok = (before - after) == Severity.CRITICAL.ambulancesNeeded();
        check("severity allocation removes the right number of ambulances", ok);
    }

    private void redistributionCoversEmptyZone() {
        DispatchSystem city = SampleCity.builtIn();
        // Empty the South-East sector.
        city.allocate(new Incident("a", 20, 6, IncidentType.MEDICAL, Severity.MODERATE));
        city.allocate(new Incident("b", 26, 9, IncidentType.MEDICAL, Severity.MODERATE));
        Rectangle se = new Rectangle(15, 0, 30, 15);
        boolean emptied = city.availableAmbulancesInRegion(se).isEmpty();
        city.redistributeCoverage();
        boolean covered = !city.availableAmbulancesInRegion(se).isEmpty();
        check("redistribution restores coverage to an emptied sector", emptied && covered);
    }

    private int countAvailableAmbulances(DispatchSystem city) {
        int n = 0;
        for (EmergencyResource r : city.resources()) {
            if (r.type() == ResourceType.AMBULANCE && r.isAvailable()) n++;
        }
        return n;
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    private void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("   [PASS] " + name); }
        else      { failed++; System.out.println("   [FAIL] " + name); }
    }

    private List<Point<Integer>> randomPoints(Random rng, int n, int span) {
        List<Point<Integer>> pts = new ArrayList<Point<Integer>>();
        for (int i = 0; i < n; i++) {
            pts.add(new Point<Integer>(rng.nextInt(span), rng.nextInt(span), i));
        }
        return pts;
    }

    private Rectangle randomRect(Random rng, int span) {
        double x1 = rng.nextInt(span), x2 = rng.nextInt(span);
        double y1 = rng.nextInt(span), y2 = rng.nextInt(span);
        if (x1 > x2) { double s = x1; x1 = x2; x2 = s; }
        if (y1 > y2) { double s = y1; y1 = y2; y2 = s; }
        return new Rectangle(x1, y1, x2, y2);
    }

    private List<String> keys(List<Point<Integer>> pts) {
        List<String> k = new ArrayList<String>();
        for (Point<Integer> p : pts) k.add(key(p));
        return k;
    }

    private String key(Point<Integer> p) {
        return p.payload() + "@" + p.x() + "," + p.y();
    }

    private List<String> bruteRange(List<Point<Integer>> pts, Rectangle r) {
        List<String> k = new ArrayList<String>();
        for (Point<Integer> p : pts) if (r.contains(p.x(), p.y())) k.add(key(p));
        return k;
    }

    private double bruteNearest(List<Point<Integer>> pts, double qx, double qy) {
        double best = Double.POSITIVE_INFINITY;
        for (Point<Integer> p : pts) best = Math.min(best, p.distanceTo(qx, qy));
        return best;
    }

    private void line() {
        System.out.println("============================================================");
    }
}
