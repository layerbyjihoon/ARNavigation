package com.google.ar.core.examples.java.helloar;

/** Building orientation relative to the magnetic north supplied by Android's rotation vector. */
final class MapOrientation {
  static final String MUNHWAGWAN_ASSET = "maps/munhwagwan.indoor.json";
  static final String SENSOR_SOURCE = "MAGNETIC_NORTH_CCW";
  static final double MUNHWAGWAN_CCW_DEGREES = 85.5;

  private MapOrientation() {}
  static boolean usesSensor(String asset) { return MUNHWAGWAN_ASSET.equals(asset); }
  static double yawFromNorth(double northYaw) {
    return MapAlignment.wrap(northYaw + Math.toRadians(MUNHWAGWAN_CCW_DEGREES));
  }
  static double mapBearing() { return 360 - MUNHWAGWAN_CCW_DEGREES; }
  static double entryBearing(double canonicalEntryYawDegrees) {
    return ((mapBearing() - canonicalEntryYawDegrees) % 360 + 360) % 360;
  }
  static boolean matchesRecording(ArRecordingStore.Entry entry) {
    return SENSOR_SOURCE.equals(entry.mapOrientationSource) && entry.mapCounterClockwiseDegrees != null
        && Math.abs(entry.mapCounterClockwiseDegrees - MUNHWAGWAN_CCW_DEGREES) < 1e-6;
  }
}
