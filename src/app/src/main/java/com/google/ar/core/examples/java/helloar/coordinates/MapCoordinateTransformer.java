package com.google.ar.core.examples.java.helloar.coordinates;

import com.google.ar.core.examples.java.helloar.map.MapPosition;
/** Transform between meter map coordinates and any AR reference frame; owns no ARCore Anchor. */
public interface MapCoordinateTransformer {
  MapPosition mapToAr(MapPosition position);
  MapPosition arToMap(MapPosition position);
}
