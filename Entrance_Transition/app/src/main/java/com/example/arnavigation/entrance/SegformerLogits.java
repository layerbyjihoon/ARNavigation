package com.example.arnavigation.entrance;

/** Decodes NCHW logits with bilinear interpolation BEFORE argmax (align_corners=false). */
public final class SegformerLogits {
    private SegformerLogits() {}

    public static SegmentationMask decode(float[][][] logits, int outputWidth, int outputHeight,
            float minimumProbability) {
        if (logits == null || logits.length != SegmentationMask.CLASS_COUNT
                || !Float.isFinite(minimumProbability) || minimumProbability < 0 || minimumProbability > 1
                || outputWidth < 4 || outputHeight < 4 || (long) outputWidth * outputHeight > 1_048_576)
            throw new IllegalArgumentException("Expected 150-class CHW logits");
        int h = logits[0].length;
        int w = h == 0 ? 0 : logits[0][0].length;
        if (w == 0) throw new IllegalArgumentException("Empty logits");
        for (float[][] channel : logits) {
            if (channel.length != h) throw new IllegalArgumentException("Ragged logits");
            for (float[] row : channel) {
                if (row.length != w) throw new IllegalArgumentException("Ragged logits");
                for (float value : row) if (!Float.isFinite(value))
                    throw new IllegalArgumentException("Non-finite logits");
            }
        }
        int[] labels = new int[outputWidth * outputHeight];
        double[] scores = new double[logits.length];
        for (int y = 0; y < outputHeight; y++) for (int x = 0; x < outputWidth; x++) {
            double sx = Math.max(0, (x + .5) * w / outputWidth - .5);
            double sy = Math.max(0, (y + .5) * h / outputHeight - .5);
            int x0 = (int) sx, y0 = (int) sy;
            int x1 = Math.min(w - 1, x0 + 1), y1 = Math.min(h - 1, y0 + 1);
            double dx = sx - x0, dy = sy - y0, maximum = -Double.MAX_VALUE;
            int winner = 0;
            for (int c = 0; c < logits.length; c++) {
                float[][] a = logits[c];
                scores[c] = (1 - dy) * ((1 - dx) * a[y0][x0] + dx * a[y0][x1])
                        + dy * ((1 - dx) * a[y1][x0] + dx * a[y1][x1]);
                if (scores[c] > maximum) { maximum = scores[c]; winner = c; }
            }
            double sum = 0;
            for (double score : scores) sum += Math.exp(score - maximum);
            labels[y * outputWidth + x] = 1 / sum >= minimumProbability ? winner : -1;
        }
        return new SegmentationMask(outputWidth, outputHeight, labels);
    }
}
