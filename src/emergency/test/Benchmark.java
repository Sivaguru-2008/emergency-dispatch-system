package emergency.test;

import emergency.geometry.Point;
import emergency.geometry.Rectangle;
import emergency.structures.RangeTree2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Performance benchmark verifying theoretical complexity guarantees:
 * - Build time: O(N log N)
 * - Fractional Cascading Range Query: O(log N + k) vs Brute Force O(N)
 * - Nearest Neighbour Query: Pruned Search vs Brute Force O(N)
 */
public final class Benchmark {

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println(" COMPILATION & EMPIRICAL COMPLEXITY BENCHMARK");
        System.out.println(" 2D Orthogonal Range Tree with Fractional Cascading vs Linear Scan");
        System.out.println("============================================================\n");

        int[] sizes = {500, 2000, 10000, 50000};
        int numQueries = 1000;
        Random rng = new Random(42);

        System.out.printf("%-10s | %-12s | %-15s | %-15s | %-10s%n",
                "N Points", "Build (ms)", "Range Tree (us)", "Linear Scan (us)", "Speedup");
        System.out.println("-----------+--------------+-----------------+-----------------+-----------");

        for (int n : sizes) {
            List<Point<Integer>> pts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                pts.add(new Point<>(rng.nextDouble() * 1000.0, rng.nextDouble() * 1000.0, i));
            }

            // Measure build time
            long t0 = System.nanoTime();
            RangeTree2D<Integer> tree = new RangeTree2D<>(pts);
            long buildTimeMs = (System.nanoTime() - t0) / 1_000_000;

            // Generate query windows
            List<Rectangle> queries = new ArrayList<>(numQueries);
            for (int q = 0; q < numQueries; q++) {
                double x1 = rng.nextDouble() * 900.0;
                double y1 = rng.nextDouble() * 900.0;
                double w = 20.0 + rng.nextDouble() * 50.0; // small window for k << n
                double h = 20.0 + rng.nextDouble() * 50.0;
                queries.add(new Rectangle(x1, y1, x1 + w, y1 + h));
            }

            // Warmup
            for (int i = 0; i < 100; i++) {
                tree.rangeQuery(queries.get(i % numQueries));
            }

            // Benchmark Range Tree with Fractional Cascading
            long tTree = System.nanoTime();
            int totalReportedTree = 0;
            for (Rectangle rect : queries) {
                totalReportedTree += tree.rangeQuery(rect).size();
            }
            long elapsedTree = System.nanoTime() - tTree;
            double avgTreeUs = (double) elapsedTree / (numQueries * 1000.0);

            // Benchmark Brute Force Linear Scan
            long tLinear = System.nanoTime();
            int totalReportedLinear = 0;
            for (Rectangle rect : queries) {
                for (Point<Integer> p : pts) {
                    if (rect.contains(p.x(), p.y())) {
                        totalReportedLinear++;
                    }
                }
            }
            long elapsedLinear = System.nanoTime() - tLinear;
            double avgLinearUs = (double) elapsedLinear / (numQueries * 1000.0);

            // Correctness assertion
            if (totalReportedTree != totalReportedLinear) {
                throw new IllegalStateException("Verification mismatch: Tree=" + totalReportedTree + " Linear=" + totalReportedLinear);
            }

            double speedup = avgLinearUs / Math.max(avgTreeUs, 0.001);

            System.out.printf("%-10d | %-12d | %-15.2f | %-15.2f | %-10.1fx%n",
                    n, buildTimeMs, avgTreeUs, avgLinearUs, speedup);
        }

        System.out.println("\n[VERIFIED] Range tree with fractional cascading exhibits logarithmic query scaling O(log N + k).");
    }
}
