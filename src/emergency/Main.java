package emergency;

import emergency.geometry.Rectangle;
import emergency.model.EmergencyResource;
import emergency.model.Incident;
import emergency.model.IncidentType;
import emergency.model.Severity;
import emergency.system.CityLoader;
import emergency.system.DispatchSystem;
import emergency.system.SampleCity;
import emergency.test.Verification;

import java.util.List;
import java.util.Scanner;

/**
 * Driver for the Intelligent Emergency Resource Allocation &amp; Ambulance
 * Dispatch System.
 *
 * <pre>
 *   java emergency.Main          scripted demonstration of every query
 *   java emergency.Main test     randomised + behavioural correctness suite
 *   java emergency.Main menu     interactive console
 * </pre>
 *
 * The city is read from {@code data/resources.csv} and {@code data/zones.csv}
 * when those files are present, otherwise an identical built-in dataset is used.
 */
public final class Main {

    public static void main(String[] args) {
        String mode = (args.length > 0) ? args[0].toLowerCase() : "demo";
        if (mode.equals("test")) {
            new Verification().run();
        } else if (mode.equals("menu")) {
            interactive(SampleCity.load());
        } else {
            demo();
        }
    }

    /* ================================================================== */
    /*  Scripted demonstration                                            */
    /* ================================================================== */

    private static void demo() {
        DispatchSystem city = SampleCity.load();
        banner("INTELLIGENT EMERGENCY RESOURCE ALLOCATION & DISPATCH SYSTEM");
        System.out.println("Data source : "
                + (CityLoader.dataPresent("data") ? "data/*.csv" : "built-in dataset"));
        System.out.println("City model  : 30 km x 30 km, " + city.zones().size()
                + " sectors, " + city.resources().size()
                + " resources in a 2-D range tree with fractional cascading.");
        System.out.println("Distances are straight-line kilometres.\n");

        // ---- Query 1 : available ambulances in a region --------------------
        section("1) Available ambulances inside a region  (orthogonal range search)");
        Rectangle region = new Rectangle(0, 0, 15, 15);   // South-West sector
        System.out.println("   Region " + region + " (South-West):");
        for (EmergencyResource a : city.availableAmbulancesInRegion(region)) {
            System.out.println("      " + a);
        }

        // ---- Query 2 : nearest available ambulance -------------------------
        section("2) Nearest available ambulance to an accident  (nearest neighbour)");
        double ax = 18, ay = 23;
        System.out.printf("   Accident at (%.0f, %.0f):%n", ax, ay);
        EmergencyResource near = city.nearestAvailableAmbulance(ax, ay);
        System.out.println("      nearest -> " + near
                + String.format("   (%.2f km)", distance(near, ax, ay)));

        // ---- Query 3 : ICU hospitals within 5 km ---------------------------
        section("3) Hospitals with ICU within 5 km  (range search + filter)");
        double hx = 7, hy = 11;
        System.out.printf("   Searching around (%.0f, %.0f), radius 5 km:%n", hx, hy);
        List<EmergencyResource> icu = city.hospitalsWithIcuWithinRadius(hx, hy, 5);
        if (icu.isEmpty()) System.out.println("      (none)");
        for (EmergencyResource h : icu) {
            System.out.printf("      %s  (%.2f km, %d ICU beds)%n",
                    h.name(), distance(h, hx, hy), h.icuBeds());
        }

        // ---- Query 4 : severity-based allocation + point location ----------
        section("4) Severity-based allocation  (+ point location for the sector)");
        Incident[] calls = {
            new Incident("E1", 19, 24, IncidentType.ROAD_ACCIDENT, Severity.CRITICAL),
            new Incident("E2",  5,  6, IncidentType.FIRE,          Severity.HIGH),
            new Incident("E3", 24, 10, IncidentType.MEDICAL,       Severity.MODERATE),
        };
        for (Incident inc : calls) {
            System.out.print(city.allocate(inc));
        }

        // ---- Query 5 : dynamic redistribution ------------------------------
        // Fresh city so the imbalance is easy to follow: two back-to-back calls
        // in the South-East sector take both its ambulances off the board.
        section("5) Dynamic redistribution after dispatch");
        DispatchSystem city2 = SampleCity.load();
        city2.allocate(new Incident("E4", 20, 6, IncidentType.MEDICAL, Severity.MODERATE));
        city2.allocate(new Incident("E5", 26, 9, IncidentType.MEDICAL, Severity.MODERATE));
        System.out.println("   Two calls in South-East have just consumed A7 and A8.");
        System.out.println("   Coverage BEFORE redistribution:");
        System.out.print(city2.coverageReport(8));
        List<String> moves = city2.redistributeCoverage();
        System.out.println("   Redistribution actions:");
        if (moves.isEmpty()) System.out.println("      (no moves needed)");
        for (String m : moves) System.out.println("      " + m);
        System.out.println("   Coverage AFTER redistribution:");
        System.out.print(city2.coverageReport(8));

        // ---- Query 6 : coverage map ----------------------------------------
        section("6) Coverage map for planning");
        System.out.println("   The per-sector table above is the coverage map. Sectors");
        System.out.println("   flagged UNDER-COVERED have no free ambulance and are first");
        System.out.println("   in line for additional units or relocation.");

        System.out.println("\n(Run \"java emergency.Main test\" for the correctness suite.)");
    }

    /* ================================================================== */
    /*  Interactive console                                               */
    /* ================================================================== */

    private static void interactive(DispatchSystem city) {
        Scanner in = new Scanner(System.in);
        banner("INTERACTIVE DISPATCH CONSOLE");
        while (true) {
            System.out.println("\n  1) ambulances in region    2) nearest ambulance");
            System.out.println("  3) ICU hospitals in radius  4) dispatch incident");
            System.out.println("  5) coverage map             6) redistribute     0) quit");
            System.out.print("  choice> ");
            String c = in.hasNextLine() ? in.nextLine().trim() : "0";
            try {
                if (c.equals("0")) { System.out.println("  bye"); return; }
                else if (c.equals("1")) {
                    System.out.print("  minX minY maxX maxY> ");
                    double[] v = reals(in, 4);
                    Rectangle rect = new Rectangle(v[0], v[1], v[2], v[3]);
                    for (EmergencyResource a : city.availableAmbulancesInRegion(rect))
                        System.out.println("    " + a);
                } else if (c.equals("2")) {
                    System.out.print("  x y> ");
                    double[] v = reals(in, 2);
                    System.out.println("    " + city.nearestAvailableAmbulance(v[0], v[1]));
                } else if (c.equals("3")) {
                    System.out.print("  x y radiusKm> ");
                    double[] v = reals(in, 3);
                    for (EmergencyResource h :
                            city.hospitalsWithIcuWithinRadius(v[0], v[1], v[2]))
                        System.out.println("    " + h);
                } else if (c.equals("4")) {
                    System.out.print("  x y severity(C/H/M/L) type(ACC/FIRE/CARD/MED/DIS/CRIME)> ");
                    String[] p = in.nextLine().trim().split("\\s+");
                    double x = Double.parseDouble(p[0]);
                    double y = Double.parseDouble(p[1]);
                    Severity sev = parseSeverity(p.length > 2 ? p[2] : "M");
                    IncidentType ty = parseType(p.length > 3 ? p[3] : "MED");
                    System.out.print(city.allocate(new Incident("UI", x, y, ty, sev)));
                } else if (c.equals("5")) {
                    System.out.print(city.coverageReport(8));
                } else if (c.equals("6")) {
                    List<String> moves = city.redistributeCoverage();
                    if (moves.isEmpty()) System.out.println("    (no moves needed)");
                    for (String m : moves) System.out.println("    " + m);
                } else {
                    System.out.println("  ? unknown choice");
                }
            } catch (Exception e) {
                System.out.println("  input error: " + e.getMessage());
            }
        }
    }

    private static double[] reals(Scanner in, int n) {
        String[] p = in.nextLine().trim().split("\\s+");
        double[] v = new double[n];
        for (int i = 0; i < n; i++) v[i] = Double.parseDouble(p[i]);
        return v;
    }

    private static Severity parseSeverity(String s) {
        switch (s.toUpperCase()) {
            case "C": return Severity.CRITICAL;
            case "H": return Severity.HIGH;
            case "L": return Severity.LOW;
            default:  return Severity.MODERATE;
        }
    }

    private static IncidentType parseType(String s) {
        switch (s.toUpperCase()) {
            case "ACC":   return IncidentType.ROAD_ACCIDENT;
            case "FIRE":  return IncidentType.FIRE;
            case "CARD":  return IncidentType.CARDIAC_ARREST;
            case "DIS":   return IncidentType.NATURAL_DISASTER;
            case "CRIME": return IncidentType.CRIME;
            default:      return IncidentType.MEDICAL;
        }
    }

    /* ================================================================== */

    private static double distance(EmergencyResource r, double x, double y) {
        if (r == null) return Double.NaN;
        double dx = r.x() - x, dy = r.y() - y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static void banner(String t) {
        System.out.println("============================================================");
        System.out.println(" " + t);
        System.out.println("============================================================");
    }

    private static void section(String t) {
        System.out.println("\n------------------------------------------------------------");
        System.out.println(t);
        System.out.println("------------------------------------------------------------");
    }
}
