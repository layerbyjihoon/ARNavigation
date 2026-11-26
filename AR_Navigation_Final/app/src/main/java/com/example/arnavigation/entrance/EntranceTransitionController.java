package com.example.arnavigation.entrance;

import java.util.ArrayDeque;
import java.util.Deque;

/** Call from one owner thread. All times are elapsedRealtime milliseconds, never wall clock. */
public final class EntranceTransitionController {
    public enum State { UNCONFIGURED, OUTDOOR, SCANNING, INSIDE_CANDIDATE,
        INDOOR_DETECTED, INDOOR_NAVIGATING, TIMED_OUT }
    public static final class Config {
        public final double activationM, deactivationM, maxAccuracyM, score, margin;
        public final long maxLocationAgeMs, scanTimeoutMs;
        public Config(double activationM, double deactivationM, double maxAccuracyM,
                double score, double margin, long maxLocationAgeMs, long scanTimeoutMs) {
            if (!Double.isFinite(activationM) || !Double.isFinite(deactivationM)
                    || !Double.isFinite(maxAccuracyM) || !Double.isFinite(score)
                    || !Double.isFinite(margin) || activationM <= 0 || deactivationM <= activationM
                    || maxAccuracyM <= 0 || score <= 0 || score > 1 || margin < 0 || margin > 1
                    || maxLocationAgeMs <= 0 || scanTimeoutMs <= 2000)
                throw new IllegalArgumentException("Invalid transition configuration");
            this.activationM = activationM; this.deactivationM = deactivationM;
            this.maxAccuracyM = maxAccuracyM; this.score = score; this.margin = margin;
            this.maxLocationAgeMs = maxLocationAgeMs; this.scanTimeoutMs = scanTimeoutMs;
        }
        public static Config defaults() { return new Config(30, 50, 20, .8, .1, 5000, 90000); }
    }

    private final Config config;
    private EntranceProfile profile;
    private State state = State.UNCONFIGURED;
    private long generation, scanStarted, nearSince = -1, farSince = -1;
    private long lastLocation = -1, lastFrame = -1, lastNow = -1, evidenceAt = -1;
    private double insideScore, negativeScore;
    private final Deque<Long> votes = new ArrayDeque<>();

    public EntranceTransitionController(Config config) { this.config = config; }
    public State state() { return state; }
    public long generation() { return generation; }
    public double insideScore() { return insideScore; }
    public double negativeScore() { return negativeScore; }
    public boolean shouldRunVision() { return state == State.SCANNING || state == State.INSIDE_CANDIDATE; }
    public EntranceProfile profile() { return profile; }

    /** Also call on background/session restart/target change to invalidate pending inference. */
    public void select(EntranceProfile profile) {
        this.profile = profile;
        generation++;
        state = profile != null && profile.isReady() ? State.OUTDOOR : State.UNCONFIGURED;
        nearSince = farSince = lastLocation = lastFrame = lastNow = evidenceAt = -1;
        insideScore = negativeScore = 0;
        votes.clear();
    }

    public void location(double latitude, double longitude, double accuracyM,
            long capturedMs, long nowMs) {
        tick(nowMs);
        if (profile == null || capturedMs <= lastLocation || capturedMs < 0 || capturedMs > nowMs
                || nowMs - capturedMs > config.maxLocationAgeMs || !Double.isFinite(accuracyM)
                || accuracyM < 0 || accuracyM > config.maxAccuracyM
                || !Double.isFinite(latitude) || Math.abs(latitude) > 90
                || !Double.isFinite(longitude) || Math.abs(longitude) > 180) return;
        boolean continuous = lastLocation >= 0 && capturedMs - lastLocation <= config.maxLocationAgeMs;
        lastLocation = capturedMs;
        double distance = distanceM(latitude, longitude, profile.latitude, profile.longitude);
        if (state == State.OUTDOOR) {
            if (distance > config.activationM) { nearSince = -1; return; }
            if (nearSince < 0 || !continuous) nearSince = capturedMs;
            else if (capturedMs - nearSince >= 1000) startScan(nowMs);
        } else if (shouldRunVision()) {
            if (distance <= config.deactivationM) farSince = -1;
            else if (farSince < 0 || !continuous) farSince = capturedMs;
            else if (capturedMs - farSince >= 5000) select(profile);
        }
    }

    public void startManualScan(long nowMs) {
        tick(nowMs);
        if (state == State.OUTDOOR || state == State.TIMED_OUT) startScan(nowMs);
    }

    public void tick(long nowMs) {
        if (nowMs < 0 || nowMs < lastNow) throw new IllegalArgumentException("Non-monotonic clock");
        lastNow = nowMs;
        if (shouldRunVision()) {
            if (nowMs - scanStarted >= config.scanTimeoutMs) {
                state = State.TIMED_OUT; generation++; votes.clear(); evidenceAt = -1;
            } else {
                prune(nowMs);
                if (state == State.INSIDE_CANDIDATE && !stable()) state = State.SCANNING;
            }
        }
    }

    public void mask(long session, long capturedMs, long nowMs, SegmentationMask mask) {
        tick(nowMs);
        if (!shouldRunVision() || session != generation || capturedMs < scanStarted
                || capturedMs <= lastFrame || capturedMs > nowMs || nowMs - capturedMs > 2000) return;
        lastFrame = capturedMs;
        StructureSignature frame = StructureSignature.from(mask);
        insideScore = best(frame, profile.inside);
        negativeScore = best(frame, profile.negatives);
        prune(capturedMs);
        if (!frame.isValid() || insideScore < config.score
                || insideScore - negativeScore < config.margin) {
            // A conflicting/occluded frame breaks the sequence; old evidence cannot trigger a later match.
            votes.clear(); evidenceAt = -1; state = State.SCANNING;
            return;
        }
        if (votes.isEmpty() || capturedMs - votes.peekLast() >= 250) votes.addLast(capturedMs);
        prune(nowMs);
        if (stable()) state = State.INSIDE_CANDIDATE;
        detectIfConfirmed(nowMs);
    }

    /** Caller supplies verified inside-only landmark / aligned boundary crossing, or explicit user confirmation. */
    public void confirmEntry(long session, long capturedMs, long nowMs) {
        tick(nowMs);
        if (session != generation || state != State.INSIDE_CANDIDATE || capturedMs < scanStarted
                || capturedMs > nowMs || nowMs - capturedMs > 2000) return;
        evidenceAt = capturedMs;
        detectIfConfirmed(nowMs);
    }

    /** AR may start only after the host verifies map identity, transform, route and TRACKING. */
    public boolean startIndoorNavigation(long session, boolean localizationAndRouteReady, boolean tracking) {
        if (session != generation || state != State.INDOOR_DETECTED
                || !localizationAndRouteReady || !tracking) return false;
        state = State.INDOOR_NAVIGATING;
        return true;
    }

    private void startScan(long nowMs) {
        generation++; scanStarted = nowMs; state = State.SCANNING;
        votes.clear(); evidenceAt = lastFrame = farSince = -1;
    }
    private void detectIfConfirmed(long nowMs) {
        if (stable() && evidenceAt >= 0 && nowMs - evidenceAt <= 2000) {
            state = State.INDOOR_DETECTED; votes.clear();
        }
    }
    private void prune(long nowMs) {
        while (!votes.isEmpty() && nowMs - votes.peekFirst() > 2000) votes.removeFirst();
    }
    private boolean stable() {
        return votes.size() >= 4 && votes.peekLast() - votes.peekFirst() >= 1000;
    }
    private static double best(StructureSignature frame, java.util.List<StructureSignature> references) {
        double score = 0;
        for (StructureSignature reference : references) score = Math.max(score, frame.similarity(reference));
        return score;
    }
    private static double distanceM(double lat, double lon, double targetLat, double targetLon) {
        double a = Math.pow(Math.sin(Math.toRadians(targetLat - lat) / 2), 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(targetLat))
                * Math.pow(Math.sin(Math.toRadians(targetLon - lon) / 2), 2);
        return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }
}
