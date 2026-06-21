package emergency.system;

import emergency.geometry.Rectangle;
import emergency.model.EmergencyResource;
import emergency.model.ResourceType;
import emergency.model.Zone;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the city from plain CSV files ({@code resources.csv}, {@code zones.csv}).
 *
 * <p>Lines that are blank or start with {@code #} are ignored, so the data files
 * may be commented. Fields are comma-separated and trimmed.
 *
 * <pre>
 *   resources.csv : id,name,type,x,y,available,icuBeds
 *   zones.csv     : name,minX,minY,maxX,maxY
 * </pre>
 */
public final class CityLoader {

    private CityLoader() { }

    /** True when both data files exist in {@code dir}. */
    public static boolean dataPresent(String dir) {
        return Files.isReadable(Paths.get(dir, "resources.csv"))
            && Files.isReadable(Paths.get(dir, "zones.csv"));
    }

    /** Builds a dispatch system from the CSV files in {@code dir}. */
    public static DispatchSystem load(String dir) throws IOException {
        List<EmergencyResource> resources =
                loadResources(Paths.get(dir, "resources.csv"));
        List<Zone> zones = loadZones(Paths.get(dir, "zones.csv"));
        return new DispatchSystem(resources, zones);
    }

    private static List<EmergencyResource> loadResources(Path file) throws IOException {
        List<EmergencyResource> out = new ArrayList<EmergencyResource>();
        int lineNo = 0;
        for (String raw : dataLines(file)) {
            lineNo++;
            String[] f = split(raw, 7, file, lineNo);
            String id = f[0];
            String name = f[1];
            ResourceType type = ResourceType.valueOf(f[2].toUpperCase());
            double x = Double.parseDouble(f[3]);
            double y = Double.parseDouble(f[4]);
            boolean available = Boolean.parseBoolean(f[5]);
            int icuBeds = Integer.parseInt(f[6]);

            EmergencyResource r;
            if (type == ResourceType.HOSPITAL) {
                r = EmergencyResource.hospital(id, name, x, y, icuBeds);
            } else {
                r = new EmergencyResource(id, name, type, x, y);
                r.setAvailable(available);
            }
            out.add(r);
        }
        if (out.isEmpty()) throw new IOException("no resources found in " + file);
        return out;
    }

    private static List<Zone> loadZones(Path file) throws IOException {
        List<Zone> out = new ArrayList<Zone>();
        int lineNo = 0;
        for (String raw : dataLines(file)) {
            lineNo++;
            String[] f = split(raw, 5, file, lineNo);
            String name = f[0];
            double minX = Double.parseDouble(f[1]);
            double minY = Double.parseDouble(f[2]);
            double maxX = Double.parseDouble(f[3]);
            double maxY = Double.parseDouble(f[4]);
            out.add(new Zone(name, new Rectangle(minX, minY, maxX, maxY)));
        }
        if (out.isEmpty()) throw new IOException("no zones found in " + file);
        return out;
    }

    /** Reads a file, dropping blank lines and #-comments, trimming the rest. */
    private static List<String> dataLines(Path file) throws IOException {
        List<String> out = new ArrayList<String>();
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                out.add(t);
            }
        }
        return out;
    }

    private static String[] split(String row, int expected, Path file, int lineNo)
            throws IOException {
        String[] f = row.split(",");
        if (f.length < expected) {
            throw new IOException(String.format(
                    "%s line %d: expected %d fields but found %d -> \"%s\"",
                    file.getFileName(), lineNo, expected, f.length, row));
        }
        for (int i = 0; i < f.length; i++) f[i] = f[i].trim();
        return f;
    }
}
