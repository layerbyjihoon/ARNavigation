package com.google.ar.core.examples.java.helloar;

/** Rotates the route about the observed camera position before applying lateral wall clearance. */
final class CorridorCenterTracker {
  private float x, z, yaw, targetX, targetZ, targetYaw;
  private float candidateX, candidateZ, candidateYaw, pivotX, pivotZ;
  private int agreement;
  private boolean singleWall, pivotSet;
  private long lastObservationTimestamp = -1, lastSeenMs, lastTickMs;

  void observe(CorridorCenterEstimator.Observation observation, long nowMs) {
    if (observation == null || observation.timestamp <= lastObservationTimestamp) return;
    lastObservationTimestamp = observation.timestamp;
    lastSeenMs = nowMs;
    singleWall = observation.singleWall;
    float globalX = observation.pivotX - rotatedX(observation.pivotX,
        observation.pivotZ, observation.yaw) + observation.x;
    float globalZ = observation.pivotZ - rotatedZ(observation.pivotX,
        observation.pivotZ, observation.yaw) + observation.z;
    if (agreement > 0 && Math.hypot(globalX - candidateX,
        globalZ - candidateZ) <= 0.3f
        && Math.abs(wrap(observation.yaw - candidateYaw)) <= Math.toRadians(4))
      agreement = Math.min(3, agreement + 1);
    else agreement = 1;
    candidateX = globalX;
    candidateZ = globalZ;
    candidateYaw = observation.yaw;
    if (agreement >= 3 || (singleWall && !pivotSet)) {
      if (!pivotSet) {
        pivotX = observation.pivotX;
        pivotZ = observation.pivotZ;
        pivotSet = true;
      }
      targetYaw = observation.yaw;
      targetX = globalX - pivotX + rotatedX(pivotX, pivotZ, targetYaw);
      targetZ = globalZ - pivotZ + rotatedZ(pivotX, pivotZ, targetYaw);
    }
  }

  void tick(long nowMs) {
    if (lastTickMs == 0) { lastTickMs = nowMs; return; }
    long elapsed = Math.max(0, Math.min(100, nowMs - lastTickMs));
    lastTickMs = nowMs;
    boolean stale = nowMs - lastSeenMs > (singleWall ? 4000 : 1800);
    if (stale) { targetX = targetZ = targetYaw = 0; agreement = 0; }
    float fraction = Math.min(1f, elapsed / 700f);
    if (stale) {
      x += (targetX - x) * fraction;
      z += (targetZ - z) * fraction;
      if (Math.hypot(x, z) < 0.03f) yaw += wrap(targetYaw - yaw) * fraction;
      if (Math.hypot(x, z) < 0.01f && Math.abs(yaw) < Math.toRadians(0.1)) {
        pivotSet = false;
        pivotX = pivotZ = 0;
      }
    } else {
      yaw += wrap(targetYaw - yaw) * fraction;
      if (Math.abs(wrap(targetYaw - yaw)) < Math.toRadians(1)) {
        x += (targetX - x) * fraction;
        z += (targetZ - z) * fraction;
      }
    }
  }

  float x() { return x; }
  float z() { return z; }
  float yaw() { return yaw; }
  float poseX(float baseX, float baseZ) {
    return rotatedX(baseX, baseZ, yaw) + pivotX
        - rotatedX(pivotX, pivotZ, yaw) + x;
  }
  float poseZ(float baseX, float baseZ) {
    return rotatedZ(baseX, baseZ, yaw) + pivotZ
        - rotatedZ(pivotX, pivotZ, yaw) + z;
  }

  private static float rotatedX(float x, float z, float angle) {
    return (float) (Math.cos(angle) * x + Math.sin(angle) * z);
  }
  private static float rotatedZ(float x, float z, float angle) {
    return (float) (-Math.sin(angle) * x + Math.cos(angle) * z);
  }
  private static float wrap(float angle) {
    return (float) Math.atan2(Math.sin(angle), Math.cos(angle));
  }

  void reset() {
    x = z = yaw = targetX = targetZ = targetYaw = 0;
    candidateX = candidateZ = candidateYaw = pivotX = pivotZ = 0;
    agreement = 0;
    singleWall = pivotSet = false;
    lastObservationTimestamp = -1;
    lastSeenMs = lastTickMs = 0;
  }
}
