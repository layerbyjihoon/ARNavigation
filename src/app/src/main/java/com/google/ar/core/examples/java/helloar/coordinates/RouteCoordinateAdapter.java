package com.google.ar.core.examples.java.helloar.coordinates;

import com.google.ar.core.examples.java.helloar.map.MapPosition;
import com.google.ar.core.examples.java.helloar.routing.NavigationRoute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public final class RouteCoordinateAdapter {
  private RouteCoordinateAdapter() {}
  public static List<MapPosition> toAr(NavigationRoute route, MapCoordinateTransformer transformer) {
    List<MapPosition> result = new ArrayList<>();
    for (MapPosition position : route.geometry()) result.add(transformer.mapToAr(position));
    return Collections.unmodifiableList(result);
  }
}
