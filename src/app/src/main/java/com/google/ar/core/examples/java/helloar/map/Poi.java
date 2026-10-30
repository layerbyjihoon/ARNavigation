package com.google.ar.core.examples.java.helloar.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Poi {
  public final String id, name, nodeId, floorId;
  public final PoiCategory category;
  /** Candidate entrances; nodeId remains the backwards-compatible primary entrance. */
  public final List<String> nodeIds;
  public Poi(String id, String name, PoiCategory category, String nodeId, String floorId) {
    this(id, name, category, nodeId, floorId, Collections.singletonList(nodeId));
  }
  public Poi(String id, String name, PoiCategory category, String nodeId, String floorId,
      List<String> nodeIds) {
    this.id = id; this.name = name; this.category = category;
    this.nodeId = nodeId; this.floorId = floorId;
    this.nodeIds = Collections.unmodifiableList(new ArrayList<>(nodeIds));
  }
  @Override public String toString() { return name + " (" + floorId + ")"; }
}
