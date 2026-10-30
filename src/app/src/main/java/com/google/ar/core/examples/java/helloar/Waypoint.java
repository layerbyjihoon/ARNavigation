package com.google.ar.core.examples.java.helloar;

/** An immutable waypoint in meters, relative to the navigation origin anchor. */
public final class Waypoint {
  public final float x;
  public final float y;
  public final float z;

  public Waypoint(float x, float y, float z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /** Arrival ignores phone height; only distance in the origin's X/Z plane matters. */
  public float horizontalDistanceTo(float currentX, float currentZ) {
    float dx = x - currentX;
    float dz = z - currentZ;
    return (float) Math.sqrt(dx * dx + dz * dz);
  }
}
