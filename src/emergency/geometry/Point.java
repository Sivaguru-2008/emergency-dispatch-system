package emergency.geometry;

/**
 * An immutable 2-D point with a generic payload.
 *
 * <p>Coordinates are expressed in a planar kilometre grid (a local projection
 * of latitude / longitude), so the Euclidean distance between two points is an
 * approximate ground distance in kilometres. This keeps the geometric data
 * structures simple while remaining realistic for a city-scale dispatch map.
 *
 * @param <T> type of the object stored at this location (e.g. an ambulance,
 *            a hospital or a fire station)
 */
public final class Point<T> {

    private final double x;          // east-west position, km
    private final double y;          // north-south position, km
    private final T payload;         // the real-world object at this location

    public Point(double x, double y, T payload) {
        this.x = x;
        this.y = y;
        this.payload = payload;
    }

    public double x() { return x; }

    public double y() { return y; }

    public T payload() { return payload; }

    /** Straight-line (Euclidean) distance to another point, in km. */
    public double distanceTo(double qx, double qy) {
        double dx = x - qx;
        double dy = y - qy;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double distanceTo(Point<?> other) {
        return distanceTo(other.x, other.y);
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f)->%s", x, y, payload);
    }
}
