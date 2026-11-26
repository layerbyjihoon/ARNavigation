package com.example.arnavigation.routing;

import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.GeoPoint;

/** Provider-independent walking route API. */
public interface RouteProvider {
    Cancelable computeWalkingRoute(
            GeoPoint origin,
            Destination destination,
            RouteCallback callback);
}
