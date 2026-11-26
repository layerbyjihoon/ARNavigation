package com.google.ar.core.examples.java.helloar.map;

public final class EdgeCost {
  /** Null means compute the polyline's 3D length. */
  public final Double distance, time;
  public EdgeCost(Double distance, Double time) { this.distance = distance; this.time = time; }
}
