package com.google.ar.core.examples.java.helloar.map;

public final class MapAnchor {
  public final String id, value, floorId;
  public final AnchorType type;
  public final MapPosition position;
  public final Double heading;
  public MapAnchor(String id, AnchorType type, String value, String floorId,
      MapPosition position, Double heading) {
    this.id = id; this.type = type; this.value = value; this.floorId = floorId;
    this.position = position; this.heading = heading;
  }
}
