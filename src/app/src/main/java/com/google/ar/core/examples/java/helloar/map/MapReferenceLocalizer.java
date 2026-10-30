package com.google.ar.core.examples.java.helloar.map;

/** Extension point for QR / AprilTag / image observations; no recognition is implemented yet. */
public interface MapReferenceLocalizer {
  MapAnchor findReference(IndoorMap map, AnchorType type, String observedValue);
}
