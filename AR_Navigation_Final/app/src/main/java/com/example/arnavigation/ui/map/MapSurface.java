package com.example.arnavigation.ui.map;

import com.example.arnavigation.model.GeoPoint;
import java.util.List;

interface MapSurface {
    void setMyLocationEnabled(boolean enabled);
    void showDestination(GeoPoint point, String title, boolean confirmed);
    void clearDestination();
    void showRoute(List<GeoPoint> points);
    void clearRoute();
    void showActiveWaypoint(GeoPoint point);
    void clearActiveWaypoint();
    void moveTo(GeoPoint point, float zoom);
    void fitRoute(List<GeoPoint> points);
}
