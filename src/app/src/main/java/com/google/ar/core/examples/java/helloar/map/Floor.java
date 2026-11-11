package com.google.ar.core.examples.java.helloar.map;

public final class Floor {
  public final String id, name;
  public final int level;
  public final Double elevation;
  public final Double riseFromPreviousFloor;
  public Floor(String id, String name, int level, Double elevation, Double riseFromPreviousFloor) {
    this.id = id; this.name = name; this.level = level; this.elevation = elevation;
    this.riseFromPreviousFloor = riseFromPreviousFloor;
  }
}
