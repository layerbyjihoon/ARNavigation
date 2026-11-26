package com.example.arnavigation.journey;

/** Pure policy: GPS starts analysis; one fresh valid indoor score latches the handoff. */
public final class JourneyGate {
    public static final double NEAR_METERS = 30, MAX_ACCURACY_METERS = 20;
    public static final long MAX_GPS_AGE_MS = 5000, MAX_FRAME_AGE_MS = 2000;
    private boolean checking, handoff;
    public boolean updateGps(double distance, double accuracy, long ageMs) {
        if (!handoff && Double.isFinite(distance) && distance >= 0 && distance <= NEAR_METERS
                && Double.isFinite(accuracy) && accuracy >= 0 && accuracy <= MAX_ACCURACY_METERS
                && ageMs >= 0 && ageMs <= MAX_GPS_AGE_MS) checking = true;
        return checking && !handoff;
    }
    public boolean observe(double score, boolean valid, long ageMs) {
        if (!checking || handoff || !valid || ageMs < 0 || ageMs > MAX_FRAME_AGE_MS
                || !Double.isFinite(score) || score < .75 || score > 1) return false;
        handoff = true;
        return true;
    }
    public boolean isChecking() { return checking && !handoff; }
    public boolean isHandingOff() { return handoff; }
}
