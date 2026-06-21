package emergency.geometry;

/**
 * An axis-aligned rectangle [minX, maxX] x [minY, maxY], inclusive on all
 * sides. Used both as an orthogonal-range query window and as a coverage zone
 * in the point-location structure.
 */
public final class Rectangle {

    private final double minX, maxX, minY, maxY;

    public Rectangle(double minX, double minY, double maxX, double maxY) {
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("min must not exceed max");
        }
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    /** Build the smallest rectangle (a square) of half-side r centred at (cx, cy). */
    public static Rectangle around(double cx, double cy, double r) {
        return new Rectangle(cx - r, cy - r, cx + r, cy + r);
    }

    public double minX() { return minX; }
    public double maxX() { return maxX; }
    public double minY() { return minY; }
    public double maxY() { return maxY; }

    public boolean containsX(double x) { return x >= minX && x <= maxX; }
    public boolean containsY(double y) { return y >= minY && y <= maxY; }

    public boolean contains(double x, double y) {
        return containsX(x) && containsY(y);
    }

    @Override
    public String toString() {
        return String.format("[%.2f..%.2f] x [%.2f..%.2f]", minX, maxX, minY, maxY);
    }
}
