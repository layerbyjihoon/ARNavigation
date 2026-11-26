package com.google.ar.core.examples.java.helloar.map;

import java.util.Map;
public final class MapNode {
  public final String id, floorId;
  public final NodeType type;
  public final MapPosition position;
  public final Map<String, Object> properties;
  public MapNode(String id, String floorId, NodeType type, MapPosition position,
      Map<String, Object> properties) {
    this.id = id; this.floorId = floorId; this.type = type; this.position = position;
    this.properties = ImmutableValues.object(properties);
  }
}
