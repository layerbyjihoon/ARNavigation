package com.example.arnavigation.journey;

/** GPS starts analysis. Fresh, sustained indoor evidence latches one handoff. */
public final class JourneyGate {
    public static final double NEAR_METERS = 30, MAX_ACCURACY_METERS = 20;
    public static final long MAX_GPS_AGE_MS = 5000, MAX_FRAME_AGE_MS = 2000;
    private static final double MIN_INSIDE_SCORE = .75, OUTSIDE_VETO_SCORE = .90;
    private static final int MIN_INDOOR_FRAMES = 3;
    private static final long MIN_CONFIRMATION_MS = 300, MAX_CONFIRMATION_GAP_MS = 1800;
    private boolean checking, handoff;
    private long lastFrameMs = -1, indoorSinceMs = -1;
    private int indoorFrames;
    public boolean updateGps(double distance, double accuracy, long ageMs) {
        if (!handoff && Double.isFinite(distance) && distance >= 0 && distance <= NEAR_METERS
                && Double.isFinite(accuracy) && accuracy >= 0 && accuracy <= MAX_ACCURACY_METERS
                && ageMs >= 0 && ageMs <= MAX_GPS_AGE_MS) checking = true;
        return checking && !handoff;
    }
    public boolean observe(double inside, double outside, boolean valid, long capturedMs, long nowMs) {
        if (!checking || handoff || capturedMs < 0 || capturedMs <= lastFrameMs
                || capturedMs > nowMs || nowMs - capturedMs > MAX_FRAME_AGE_MS) return false;
        if (lastFrameMs >= 0 && capturedMs - lastFrameMs > MAX_CONFIRMATION_GAP_MS) resetConfirmation();
        lastFrameMs = capturedMs;
        if (!valid || !Double.isFinite(inside) || inside < MIN_INSIDE_SCORE || inside > 1
                || !Double.isFinite(outside) || outside < 0 || outside >= OUTSIDE_VETO_SCORE) {
            resetConfirmation();
            return false;
        }
        if (indoorSinceMs < 0) indoorSinceMs = capturedMs;
        indoorFrames++;
        if (indoorFrames < MIN_INDOOR_FRAMES || capturedMs - indoorSinceMs < MIN_CONFIRMATION_MS) return false;
        handoff = true;
        return true;
    }

    public void resetObservations() {
        lastFrameMs = -1;
        resetConfirmation();
    }

    private void resetConfirmation() {
        indoorSinceMs = -1;
        indoorFrames = 0;
    }
    public boolean isChecking() { return checking && !handoff; }
    public boolean isHandingOff() { return handoff; }
}
