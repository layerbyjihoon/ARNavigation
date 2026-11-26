package com.google.ar.core.examples.java.helloar.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public final class MapEdge {
  public final String id, from, to;
  public final boolean bidirectional;
  public final EdgeType type;
  public final List<MapPosition> geometry;
  public final EdgeCost cost;
  public final Accessibility accessibility;
  public MapEdge(String id, String from, String to, boolean bidirectional, EdgeType type,
      List<MapPosition> geometry, EdgeCost cost, Accessibility accessibility) {
    this.id = id; this.from = from; this.to = to; this.bidirectional = bidirectional;
    this.type = type;
    this.geometry = Collections.unmodifiableList(new ArrayList<>(geometry));
    this.cost = cost; this.accessibility = accessibility;
  }
}
