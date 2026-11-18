package com.example.arnavigation.entrance;

/** Upright, full-frame ADE20K output IDs (0..149); -1 means ignored/uncertain. */
public final class SegmentationMask {
    public static final int CLASS_COUNT = 150;
    public final int width, height;
    private final int[] labels;

    public SegmentationMask(int width, int height, int[] labels) {
        if (width < 4 || height < 4 || (long) width * height > 1_048_576
                || labels == null || labels.length != width * height)
            throw new IllegalArgumentException("Invalid mask dimensions");
        for (int label : labels) if (label < -1 || label >= CLASS_COUNT)
            throw new IllegalArgumentException("Expected ADE20K output ID or -1");
        this.width = width;
        this.height = height;
        this.labels = labels.clone();
    }

    public int labelAt(int x, int y) { return labels[y * width + x]; }
}
