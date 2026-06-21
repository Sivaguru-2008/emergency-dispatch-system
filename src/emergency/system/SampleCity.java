package emergency.system;

import emergency.geometry.Rectangle;
import emergency.model.EmergencyResource;
import emergency.model.ResourceType;
import emergency.model.Zone;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Single source of the demonstration city.
 *
 * <p>{@link #load()} prefers the CSV files under {@code data/} so the dataset
 * can be edited without recompiling, and transparently falls back to an
 * identical built-in dataset when the files are not reachable (e.g. the program
 * is launched from another directory). Either way the rest of the system sees
 * the same {@link DispatchSystem}.
 */
public final class SampleCity {

    private static final String DATA_DIR = "data";

    private SampleCity() { }

    /** Loads from {@code data/} if available, otherwise the built-in dataset. */
    public static DispatchSystem load() {
        if (CityLoader.dataPresent(DATA_DIR)) {
            try {
                return CityLoader.load(DATA_DIR);
            } catch (IOException e) {
                System.err.println("[warn] could not read data/ (" + e.getMessage()
                        + "); using built-in dataset");
            }
        }
        return builtIn();
    }

    /** The 30 km x 30 km reference city, hard-coded (mirrors data/*.csv). */
    public static DispatchSystem builtIn() {
        List<Zone> zones = new ArrayList<Zone>();
        zones.add(new Zone("North-West", new Rectangle(0, 15, 15, 30)));
        zones.add(new Zone("North-East", new Rectangle(15, 15, 30, 30)));
        zones.add(new Zone("South-West", new Rectangle(0, 0, 15, 15)));
        zones.add(new Zone("South-East", new Rectangle(15, 0, 30, 15)));

        List<EmergencyResource> r = new ArrayList<EmergencyResource>();
        r.add(new EmergencyResource("A1", "Ambulance-1", ResourceType.AMBULANCE,  3,  4));
        r.add(new EmergencyResource("A2", "Ambulance-2", ResourceType.AMBULANCE,  6, 12));
        r.add(new EmergencyResource("A3", "Ambulance-3", ResourceType.AMBULANCE, 10, 22));
        r.add(new EmergencyResource("A4", "Ambulance-4", ResourceType.AMBULANCE, 13, 27));
        r.add(new EmergencyResource("A5", "Ambulance-5", ResourceType.AMBULANCE, 22, 25));
        r.add(new EmergencyResource("A6", "Ambulance-6", ResourceType.AMBULANCE, 27, 20));
        r.add(new EmergencyResource("A7", "Ambulance-7", ResourceType.AMBULANCE, 20,  6));
        r.add(new EmergencyResource("A8", "Ambulance-8", ResourceType.AMBULANCE, 26,  9));
        r.add(new EmergencyResource("F1", "Fire-Engine-1", ResourceType.FIRE_TRUCK,  5,  8));
        r.add(new EmergencyResource("F2", "Fire-Engine-2", ResourceType.FIRE_TRUCK, 24, 22));
        r.add(new EmergencyResource("P1", "Patrol-1", ResourceType.POLICE_UNIT,  8, 18));
        r.add(new EmergencyResource("P2", "Patrol-2", ResourceType.POLICE_UNIT, 23, 11));
        r.add(EmergencyResource.hospital("H1", "City-General",   4, 10, 6));
        r.add(EmergencyResource.hospital("H2", "Northside-Med", 12, 25, 3));
        r.add(EmergencyResource.hospital("H3", "East-Care",     25, 18, 0));
        r.add(EmergencyResource.hospital("H4", "Metro-Trauma",  21,  7, 4));

        return new DispatchSystem(r, zones);
    }
}
