package com.google.ar.core.examples.java.helloar.map;

import com.google.ar.core.examples.java.helloar.routing.IndoorNavGraph;
public final class MapLoadResult {
  public final IndoorMap map;
  /** Null for invalid input: the route engine can never consume an invalid graph. */
  public final IndoorNavGraph graph;
  public final MapValidationResult validation;
  public MapLoadResult(IndoorMap map, IndoorNavGraph graph, MapValidationResult validation) {
    this.map = map; this.graph = graph; this.validation = validation;
  }
  public boolean isSuccess() { return graph != null && !validation.hasErrors(); }
}
