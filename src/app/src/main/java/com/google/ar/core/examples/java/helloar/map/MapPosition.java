package com.google.ar.core.examples.java.helloar.map;

/** Immutable local map coordinate. All dimensions are meters, independent of ARCore. */
public final class MapPosition {
  public final double x, y, z;
  public MapPosition(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
  public boolean isFinite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
  public double distanceTo(MapPosition other) {
    return Math.hypot(Math.hypot(x - other.x, y - other.y), z - other.z);
  }
}
