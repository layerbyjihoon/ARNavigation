package com.google.ar.core.examples.java.helloar.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Canonical IndoorNavGraph document. Does not contain ARCore objects or session coordinates. */
public final class IndoorMap {
  public final String schema, version;
  public final IndoorMapMetadata metadata;
  public final IndoorCoordinateSystem coordinateSystem;
  public final List<Floor> floors;
  public final List<MapNode> nodes;
  public final List<MapEdge> edges;
  public final List<Poi> pois;
  public final List<MapAnchor> anchors;
  public IndoorMap(String schema, String version, IndoorMapMetadata metadata,
      IndoorCoordinateSystem coordinateSystem, List<Floor> floors, List<MapNode> nodes,
      List<MapEdge> edges, List<Poi> pois, List<MapAnchor> anchors) {
    this.schema = schema; this.version = version; this.metadata = metadata;
    this.coordinateSystem = coordinateSystem;
    this.floors = copy(floors); this.nodes = copy(nodes); this.edges = copy(edges);
    this.pois = copy(pois); this.anchors = copy(anchors);
  }
  private static <T> List<T> copy(List<T> values) {
    return Collections.unmodifiableList(new ArrayList<>(values));
  }
}
