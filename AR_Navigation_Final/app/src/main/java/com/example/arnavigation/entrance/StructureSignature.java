package com.example.arnavigation.entrance;

/** Semantic layout, independent of Android and model runtime. No entrance semantics are invented. */
public final class StructureSignature {
    private static final int GRID = 4;
    // Building/sky/ground are useful negative evidence, not mandatory outdoor recognition.
    private static final int[] CLASSES = {0, 1, 2, 3, 4, 5, 6, 8, 9, 11, 14, 38,
            42, 43, 53, 58, 59, 86, 106, 121, 144};
    private final double[][] cells;
    private final double[] adjacency;
    private final boolean valid;

    private StructureSignature(double[][] cells, double[] adjacency, boolean valid) {
        this.cells = cells;
        this.adjacency = adjacency;
        this.valid = valid;
    }

    public static StructureSignature from(SegmentationMask mask) {
        int n = CLASSES.length;
        double[][] cells = new double[GRID * GRID][n];
        double[] adjacency = new double[n * n];
        int[] counts = new int[GRID * GRID], totals = new int[GRID * GRID];
        int covered = 0;
        for (int y = 0; y < mask.height; y++) for (int x = 0; x < mask.width; x++) {
            int cell = (y * GRID / mask.height) * GRID + x * GRID / mask.width;
            totals[cell]++;
            int a = index(mask.labelAt(x, y));
            if (a < 0) continue; // People, vehicles and unsupported regions do not vote.
            counts[cell]++;
            cells[cell][a]++;
            if (x + 1 < mask.width) addPair(adjacency, a, index(mask.labelAt(x + 1, y)));
            if (y + 1 < mask.height) addPair(adjacency, a, index(mask.labelAt(x, y + 1)));
        }
        for (int c = 0; c < cells.length; c++) {
            if (counts[c] >= totals[c] * 0.5) covered++;
            if (counts[c] > 0) for (int i = 0; i < n; i++) cells[c][i] /= counts[c];
        }
        double sum = 0;
        for (double value : adjacency) sum += value;
        if (sum > 0) for (int i = 0; i < adjacency.length; i++) adjacency[i] /= sum;
        return new StructureSignature(cells, adjacency, covered >= 12 && sum > 0);
    }

    public boolean isValid() { return valid; }

    public double similarity(StructureSignature other) {
        if (!valid || !other.valid) return 0;
        double distance = 0;
        for (int c = 0; c < cells.length; c++) distance += l1(cells[c], other.cells[c]);
        double gridScore = Math.max(0, 1 - distance / (2 * cells.length));
        return 0.6 * gridScore + 0.4 * Math.max(0, 1 - l1(adjacency, other.adjacency) / 2);
    }

    private static int index(int label) {
        for (int i = 0; i < CLASSES.length; i++) if (CLASSES[i] == label) return i;
        return -1;
    }

    private static void addPair(double[] values, int a, int b) {
        if (b < 0 || a == b) return;
        int n = CLASSES.length;
        values[a * n + b]++;
        values[b * n + a]++;
    }

    private static double l1(double[] a, double[] b) {
        double distance = 0;
        for (int i = 0; i < a.length; i++) distance += Math.abs(a[i] - b[i]);
        return distance;
    }
}
