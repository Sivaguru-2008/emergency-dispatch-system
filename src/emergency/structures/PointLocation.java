package emergency.structures;

import emergency.geometry.Rectangle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Planar <b>point location</b> over a set of non-overlapping, axis-aligned
 * rectangular regions, using the classic <b>slab decomposition</b>.
 *
 * <p>In the dispatch system the regions are <em>response zones</em> (sectors of
 * the city). Given an incident's coordinates, {@link #locate} reports in
 * O(log n) time which zone the incident belongs to, so the right control room
 * / sector can own the call.
 *
 * <p><b>Method.</b> Every vertical edge of every rectangle induces a vertical
 * line; consecutive lines bound vertical <em>slabs</em>. Inside a single slab
 * the rectangles appear as a set of disjoint y-intervals, kept sorted by their
 * lower edge. A query first binary-searches the x-coordinate to pick the slab,
 * then binary-searches the y-coordinate inside that slab. Preprocessing is
 * O(n&sup2;) space in the worst case; each query is O(log n).
 *
 * @param <Z> type describing a region (its label / metadata)
 */
public final class PointLocation<Z> {

    private final double[] slabX;        // sorted distinct vertical boundaries
    private final double[][] loY;        // per slab: interval lower edges (sorted)
    private final double[][] hiY;        // per slab: interval upper edges
    private final Object[][] label;      // per slab: the Z owning each interval

    public PointLocation(List<Rectangle> rects, List<Z> labels) {
        if (rects.size() != labels.size()) {
            throw new IllegalArgumentException("rects and labels differ in size");
        }

        // 1. Distinct, sorted x-boundaries -> slabs.
        TreeSet<Double> xs = new TreeSet<Double>();
        for (Rectangle r : rects) { xs.add(r.minX()); xs.add(r.maxX()); }
        slabX = new double[xs.size()];
        int idx = 0;
        for (Double d : xs) slabX[idx++] = d;

        int slabs = Math.max(slabX.length - 1, 0);
        loY = new double[slabs][];
        hiY = new double[slabs][];
        label = new Object[slabs][];

        // 2. For each slab, gather the rectangles covering it, sorted by lower y.
        for (int s = 0; s < slabs; s++) {
            double mid = (slabX[s] + slabX[s + 1]) / 2.0;
            List<Integer> cover = new ArrayList<Integer>();
            for (int i = 0; i < rects.size(); i++) {
                Rectangle r = rects.get(i);
                if (r.minX() <= mid && mid <= r.maxX()) cover.add(i);
            }
            cover.sort((a, b) -> Double.compare(rects.get(a).minY(),
                                                rects.get(b).minY()));
            int m = cover.size();
            loY[s] = new double[m];
            hiY[s] = new double[m];
            label[s] = new Object[m];
            for (int k = 0; k < m; k++) {
                Rectangle r = rects.get(cover.get(k));
                loY[s][k] = r.minY();
                hiY[s][k] = r.maxY();
                label[s][k] = labels.get(cover.get(k));
            }
        }
    }

    /** Returns the region containing (px, py), or {@code null} if outside all. */
    @SuppressWarnings("unchecked")
    public Z locate(double px, double py) {
        if (slabX.length < 2) return null;
        if (px < slabX[0] || px > slabX[slabX.length - 1]) return null;

        // Slab index: largest s with slabX[s] <= px (clamped to last slab).
        int s = upperBound(slabX, px) - 1;
        if (s < 0) s = 0;
        if (s >= loY.length) s = loY.length - 1;

        // Within the slab, find the disjoint y-interval containing py.
        double[] lo = loY[s];
        double[] hi = hiY[s];
        int k = upperBound(lo, py) - 1;        // last interval with lo <= py
        if (k >= 0 && py <= hi[k]) {
            return (Z) label[s][k];
        }
        return null;
    }

    /** First index i with {@code a[i] > key}. */
    private static int upperBound(double[] a, double key) {
        int lo = 0, hi = a.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (a[mid] > key) hi = mid;
            else lo = mid + 1;
        }
        return lo;
    }

    public int slabCount() { return Math.max(slabX.length - 1, 0); }
}
