package com.google.ar.core.examples.java.helloar.map;

public final class Floor {
  public final String id, name;
  public final int level;
  public final Double elevation;
  public Floor(String id, String name, int level, Double elevation) {
    this.id = id; this.name = name; this.level = level; this.elevation = elevation;
  }
}
