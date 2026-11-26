package com.example.entrancetransition;

/** Owner-thread classifier. Timestamps use elapsedRealtime milliseconds. */
public final class EnvironmentTransitionController {
    public enum Mode { UNKNOWN, OUTDOOR, INDOOR }
    public static final double MIN_INSIDE_SCORE = 0.75;
    private static final long MAX_FRAME_AGE_MS = 2000;
    private Mode mode = Mode.UNKNOWN;
    private long lastFrameMs = -1;

    public Mode mode() { return mode; }
    public boolean isAmbiguous() { return mode == Mode.UNKNOWN; }

    /** One fresh valid observation immediately determines the environment from the indoor score. */
    public void observe(double inside, boolean valid, long capturedMs, long nowMs) {
        tick(nowMs);
        if (capturedMs < 0 || capturedMs <= lastFrameMs || capturedMs > nowMs
                || nowMs - capturedMs > MAX_FRAME_AGE_MS) return;
        lastFrameMs = capturedMs;
        if (!valid || !Double.isFinite(inside) || inside < 0 || inside > 1) {
            mode = Mode.UNKNOWN;
            return;
        }
        mode = inside >= MIN_INSIDE_SCORE ? Mode.INDOOR : Mode.OUTDOOR;
    }

    public void tick(long nowMs) {
        if (lastFrameMs >= 0 && nowMs - lastFrameMs > MAX_FRAME_AGE_MS) mode = Mode.UNKNOWN;
    }

    /** Discards the previous session's classification. */
    public void reset() {
        mode = Mode.UNKNOWN;
        lastFrameMs = -1;
    }
}
