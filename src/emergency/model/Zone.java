package emergency.model;

import emergency.geometry.Rectangle;

/**
 * A rectangular response zone (city sector) owned by one control room. Used by
 * the point-location structure to route an incident to the responsible sector.
 */
public final class Zone {

    private final String name;
    private final Rectangle area;

    public Zone(String name, Rectangle area) {
        this.name = name;
        this.area = area;
    }

    public String name() { return name; }
    public Rectangle area() { return area; }

    @Override
    public String toString() {
        return name + " " + area;
    }
}
