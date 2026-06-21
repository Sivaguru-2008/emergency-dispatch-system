package emergency.structures;

import emergency.geometry.Point;
import emergency.geometry.Rectangle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * A two-dimensional <b>layered range tree with fractional cascading</b>.
 *
 * <p><b>Primary tree.</b> A balanced binary search tree is built on the
 * x-coordinate. Every input point lives in a leaf; an internal node stores the
 * largest x-coordinate found in its left subtree ({@code splitX}) and is used
 * for routing.
 *
 * <p><b>Associated structure.</b> Every node {@code v} keeps an array of the
 * points in its subtree <em>sorted by y</em>. A plain 2-D range tree binary
 * searches this array at each of the O(log n) canonical nodes, giving an
 * O(log&sup2; n + k) query.
 *
 * <p><b>Fractional cascading.</b> To remove one logarithmic factor, each entry
 * of a node's y-array stores a pointer to the position of the same y-threshold
 * in the y-arrays of its two children. The y-threshold is then located by a
 * <em>single</em> binary search at the root and simply followed down through the
 * pointers, yielding the optimal <b>O(log n + k)</b> reporting time.
 *
 * <p>Construction is O(n log n) time and O(n log n) space.
 *
 * @param <T> type of payload stored at each point
 */
public final class RangeTree2D<T> {

    /* ------------------------------------------------------------------ */
    /*  Node                                                              */
    /* ------------------------------------------------------------------ */

    private static final class Node<T> {
        Node<T> left, right;     // children (null for a leaf)
        double splitX;           // internal: max x-coordinate in the left subtree
        boolean isLeaf;
        Point<T> point;          // leaf: the single point stored here

        // Associated structure: points of this subtree sorted by ascending y.
        Point<T>[] ys;
        // Fractional-cascading pointers, length ys.length + 1 (last = sentinel).
        // leftPtr[i]  = first index j in left.ys  with left.ys[j].y  >= ys[i].y
        // rightPtr[i] = first index j in right.ys with right.ys[j].y >= ys[i].y
        int[] leftPtr;
        int[] rightPtr;
    }

    private final Node<T> root;
    private final int size;

    // Bounding box of all stored points (used to bound nearest-neighbour search).
    private final double minX, minY, maxX, maxY;
    private final List<Point<T>> all;   // kept for convenience / verification

    /* ------------------------------------------------------------------ */
    /*  Construction                                                      */
    /* ------------------------------------------------------------------ */

    public RangeTree2D(List<Point<T>> points) {
        this.all = new ArrayList<Point<T>>(points);
        this.size = points.size();
        if (points.isEmpty()) {
            root = null;
            minX = minY = maxX = maxY = 0;
            return;
        }

        Point<T>[] byX = newArray(points.size());
        points.toArray(byX);
        // Sort by x (stable). Duplicate x-values are handled by the query rules.
        Arrays.sort(byX, new Comparator<Point<T>>() {
            public int compare(Point<T> a, Point<T> b) {
                return Double.compare(a.x(), b.x());
            }
        });

        double loX = Double.POSITIVE_INFINITY, hiX = Double.NEGATIVE_INFINITY;
        double loY = Double.POSITIVE_INFINITY, hiY = Double.NEGATIVE_INFINITY;
        for (Point<T> p : byX) {
            loX = Math.min(loX, p.x()); hiX = Math.max(hiX, p.x());
            loY = Math.min(loY, p.y()); hiY = Math.max(hiY, p.y());
        }
        minX = loX; maxX = hiX; minY = loY; maxY = hiY;

        root = build(byX, 0, byX.length - 1);
    }

    /** Recursively builds the primary tree and every associated y-array. */
    private Node<T> build(Point<T>[] byX, int lo, int hi) {
        Node<T> node = new Node<T>();
        if (lo == hi) {                       // leaf
            node.isLeaf = true;
            node.point = byX[lo];
            node.ys = newArray(1);
            node.ys[0] = byX[lo];
            return node;
        }
        int mid = (lo + hi) >>> 1;
        node.splitX = byX[mid].x();           // max x in the left subtree
        node.left = build(byX, lo, mid);
        node.right = build(byX, mid + 1, hi);
        mergeByY(node);                       // build ys + cascade pointers
        return node;
    }

    /** Merges the children's y-arrays and computes fractional-cascading pointers. */
    private void mergeByY(Node<T> v) {
        Point<T>[] L = v.left.ys;
        Point<T>[] R = v.right.ys;
        int n = L.length + R.length;
        Point<T>[] merged = newArray(n);

        int i = 0, j = 0, k = 0;
        while (i < L.length && j < R.length) {
            merged[k++] = (L[i].y() <= R[j].y()) ? L[i++] : R[j++];
        }
        while (i < L.length) merged[k++] = L[i++];
        while (j < R.length) merged[k++] = R[j++];
        v.ys = merged;

        // Cascade pointers (sentinel slot at index n points past the children).
        int[] lp = new int[n + 1];
        int[] rp = new int[n + 1];
        lp[n] = L.length;
        rp[n] = R.length;
        int jl = L.length, jr = R.length;
        for (int t = n - 1; t >= 0; t--) {
            double yv = merged[t].y();
            while (jl - 1 >= 0 && L[jl - 1].y() >= yv) jl--;
            lp[t] = jl;
            while (jr - 1 >= 0 && R[jr - 1].y() >= yv) jr--;
            rp[t] = jr;
        }
        v.leftPtr = lp;
        v.rightPtr = rp;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Point<T>[] newArray(int n) {
        return (Point<T>[]) new Point[n];
    }

    /* ------------------------------------------------------------------ */
    /*  Orthogonal range query  (O(log n + k))                            */
    /* ------------------------------------------------------------------ */

    /** Reports every stored point whose coordinates fall inside {@code rect}. */
    public List<Point<T>> rangeQuery(Rectangle rect) {
        List<Point<T>> out = new ArrayList<Point<T>>();
        if (root == null) return out;

        final double x1 = rect.minX(), x2 = rect.maxX();
        final double y1 = rect.minY(), y2 = rect.maxY();

        // Descend to the split node while maintaining pos = first index >= y1.
        Node<T> v = root;
        int pos = lowerBoundY(root.ys, y1);
        while (!v.isLeaf) {
            if (x2 < v.splitX) {              // both bounds go left
                pos = v.leftPtr[pos];
                v = v.left;
            } else if (x1 > v.splitX) {       // both bounds go right
                pos = v.rightPtr[pos];
                v = v.right;
            } else {
                break;                         // split node found
            }
        }

        if (v.isLeaf) {                        // whole query collapsed to one leaf
            addLeafIfInside(v.point, x1, x2, y1, y2, out);
            return out;
        }

        final Node<T> split = v;
        final int posSplit = pos;

        // ---- left spine: follow x1, report right children as canonical ----
        Node<T> lv = split.left;
        int lpos = split.leftPtr[posSplit];
        while (!lv.isLeaf) {
            if (x1 <= lv.splitX) {
                report(lv.right, lv.rightPtr[lpos], y2, out);
                lpos = lv.leftPtr[lpos];
                lv = lv.left;
            } else {
                lpos = lv.rightPtr[lpos];
                lv = lv.right;
            }
        }
        addLeafIfInside(lv.point, x1, x2, y1, y2, out);

        // ---- right spine: follow x2, report left children as canonical ----
        Node<T> rv = split.right;
        int rpos = split.rightPtr[posSplit];
        while (!rv.isLeaf) {
            if (x2 >= rv.splitX) {
                report(rv.left, rv.leftPtr[rpos], y2, out);
                rpos = rv.rightPtr[rpos];
                rv = rv.right;
            } else {
                rpos = rv.leftPtr[rpos];
                rv = rv.left;
            }
        }
        addLeafIfInside(rv.point, x1, x2, y1, y2, out);

        return out;
    }

    /** Reports a canonical subtree: its y-array from {@code start} while y <= y2. */
    private void report(Node<T> w, int start, double y2, List<Point<T>> out) {
        Point<T>[] ys = w.ys;
        for (int p = start; p < ys.length && ys[p].y() <= y2; p++) {
            out.add(ys[p]);
        }
    }

    private void addLeafIfInside(Point<T> p, double x1, double x2,
                                 double y1, double y2, List<Point<T>> out) {
        if (p.x() >= x1 && p.x() <= x2 && p.y() >= y1 && p.y() <= y2) {
            out.add(p);
        }
    }

    /** First index i with {@code ys[i].y >= key}; returns ys.length if none. */
    private int lowerBoundY(Point<T>[] ys, double key) {
        int lo = 0, hi = ys.length;           // search in [lo, hi)
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (ys[mid].y() >= key) hi = mid;
            else lo = mid + 1;
        }
        return lo;
    }

    /* ------------------------------------------------------------------ */
    /*  Derived queries                                                   */
    /* ------------------------------------------------------------------ */

    /** Range query followed by a payload predicate filter. */
    public List<Point<T>> rangeQuery(Rectangle rect, Predicate<Point<T>> keep) {
        List<Point<T>> filtered = new ArrayList<Point<T>>();
        for (Point<T> p : rangeQuery(rect)) {
            if (keep.test(p)) filtered.add(p);
        }
        return filtered;
    }

    /** Every matching point within Euclidean distance {@code r} of (qx, qy). */
    public List<Point<T>> withinRadius(double qx, double qy, double r,
                                       Predicate<Point<T>> keep) {
        List<Point<T>> out = new ArrayList<Point<T>>();
        for (Point<T> p : rangeQuery(Rectangle.around(qx, qy, r))) {
            if (p.distanceTo(qx, qy) <= r && keep.test(p)) out.add(p);
        }
        return out;
    }

    /**
     * Nearest matching point to (qx, qy), or {@code null} if none matches.
     *
     * <p>Uses an expanding square window over the range tree. Once a first
     * candidate is found at Euclidean distance {@code d}, a final exact query
     * over the box of half-side {@code d} is performed: any point closer than
     * {@code d} must lie inside that box, so the result is provably correct.
     */
    public Point<T> nearest(double qx, double qy, Predicate<Point<T>> keep) {
        if (root == null) return null;

        // Largest half-side ever needed to cover the whole data set.
        double cover = Math.max(Math.max(qx - minX, maxX - qx),
                                Math.max(qy - minY, maxY - qy));
        if (cover < 0) cover = 0;

        double r = 1.0;                        // start with a 1 km window
        List<Point<T>> hits = null;
        while (true) {
            hits = rangeQuery(Rectangle.around(qx, qy, r), keep);
            if (!hits.isEmpty() || r >= cover) break;
            r = Math.min(r * 2.0, cover);
        }
        if (hits.isEmpty()) return null;       // nothing matches anywhere

        double best = Double.POSITIVE_INFINITY;
        for (Point<T> p : hits) best = Math.min(best, p.distanceTo(qx, qy));

        // Exact pass: the true nearest lies within distance 'best'.
        Point<T> winner = null;
        double winnerDist = Double.POSITIVE_INFINITY;
        for (Point<T> p : rangeQuery(Rectangle.around(qx, qy, best), keep)) {
            double d = p.distanceTo(qx, qy);
            if (d < winnerDist) { winnerDist = d; winner = p; }
        }
        return winner;
    }

    /* ------------------------------------------------------------------ */
    /*  Misc                                                              */
    /* ------------------------------------------------------------------ */

    public int size() { return size; }

    public List<Point<T>> allPoints() { return new ArrayList<Point<T>>(all); }
}
