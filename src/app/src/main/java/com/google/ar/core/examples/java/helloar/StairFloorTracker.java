package com.google.ar.core.examples.java.helloar;

/** Best-effort floor estimate from ARCore camera height relative to the stair entry. */
final class StairFloorTracker {
  enum Phase { START_FLOOR, BETWEEN_FLOORS, NEXT_FLOOR }

  private static final float BETWEEN_METERS = 0.8f;
  private static final float DEFAULT_FLOOR_HEIGHT_METERS = 3.3f;
  private static final float ARRIVAL_FRACTION = 0.8f;
  private static final long CONFIRM_MS = 1200;

  final String startFloor;
  final String nextFloor;
  private final float startCameraY;
  private final int direction;
  private final float floorHeightMeters;
  private final float nextFloorMeters;
  private final float resetMeters;
  private Phase phase = Phase.START_FLOOR;
  private long nextFloorSince = -1;
  private float climbedMeters;

  StairFloorTracker(String startFloor, String nextFloor, boolean ascending, float startCameraY) {
    this(startFloor, nextFloor, ascending, startCameraY, 0f);
  }

  StairFloorTracker(String startFloor, String nextFloor, boolean ascending, float startCameraY,
      float mappedRiseMeters) {
    this.startFloor = startFloor;
    this.nextFloor = nextFloor;
    this.direction = ascending ? 1 : -1;
    this.startCameraY = startCameraY;
    floorHeightMeters = mappedRiseMeters >= 2f
        ? mappedRiseMeters : DEFAULT_FLOOR_HEIGHT_METERS;
    nextFloorMeters = Math.max(1.8f, floorHeightMeters * ARRIVAL_FRACTION);
    resetMeters = Math.max(1.3f, nextFloorMeters * 0.6f);
  }

  void update(float cameraY, long elapsedRealtimeMs) {
    if (!Float.isFinite(cameraY)) return;
    climbedMeters = (cameraY - startCameraY) * direction;
    if (climbedMeters >= nextFloorMeters) {
      if (nextFloorSince < 0) nextFloorSince = elapsedRealtimeMs;
      if (elapsedRealtimeMs - nextFloorSince >= CONFIRM_MS) phase = Phase.NEXT_FLOOR;
      else if (phase != Phase.NEXT_FLOOR) phase = Phase.BETWEEN_FLOORS;
    } else if (climbedMeters < resetMeters) {
      nextFloorSince = -1;
      phase = climbedMeters >= BETWEEN_METERS ? Phase.BETWEEN_FLOORS : Phase.START_FLOOR;
    } else if (phase != Phase.NEXT_FLOOR) {
      nextFloorSince = -1;
      phase = Phase.BETWEEN_FLOORS;
    }
  }

  Phase phase() { return phase; }
  float climbedMeters() { return climbedMeters; }
  float verticalDeltaMeters() { return climbedMeters * direction; }
  float floorRiseMeters() { return floorHeightMeters * direction; }
  void trackingLost() { nextFloorSince = -1; }

  static String label(String floorId) {
    return floorId != null && floorId.matches("F[0-9]+")
        ? floorId.substring(1) + "층" : floorId;
  }
}
