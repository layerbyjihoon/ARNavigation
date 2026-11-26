package com.example.arnavigation.ui.map;

import com.example.arnavigation.model.GeoPoint;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import java.util.ArrayList;
import java.util.List;

final class GoogleMapSurface implements MapSurface {
    private final GoogleMap map;
    private Marker destinationMarker;
    private Marker waypointMarker;
    private Polyline routePolyline;

    GoogleMapSurface(GoogleMap map) {
        this.map = map;
    }

    @Override
    public void setMyLocationEnabled(boolean enabled) {
        try {
            map.setMyLocationEnabled(enabled);
        } catch (SecurityException ignored) {
            map.setMyLocationEnabled(false);
        }
    }

    @Override
    public void showDestination(GeoPoint point, String title, boolean confirmed) {
        clearDestination();
        destinationMarker = map.addMarker(new MarkerOptions()
                .position(toLatLng(point))
                .title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(
                        confirmed ? BitmapDescriptorFactory.HUE_RED
                                : BitmapDescriptorFactory.HUE_ORANGE)));
    }

    @Override
    public void clearDestination() {
        if (destinationMarker != null) destinationMarker.remove();
        destinationMarker = null;
    }

    @Override
    public void showRoute(List<GeoPoint> points) {
        clearRoute();
        ArrayList<LatLng> latLngs = new ArrayList<>(points.size());
        for (GeoPoint point : points) latLngs.add(toLatLng(point));
        routePolyline = map.addPolyline(new PolylineOptions()
                .addAll(latLngs)
                .color(0xff1565c0)
                .width(12.0f)
                .geodesic(true));
    }

    @Override
    public void clearRoute() {
        if (routePolyline != null) routePolyline.remove();
        routePolyline = null;
    }

    @Override
    public void showActiveWaypoint(GeoPoint point) {
        clearActiveWaypoint();
        waypointMarker = map.addMarker(new MarkerOptions()
                .position(toLatLng(point))
                .title("Next waypoint")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
    }

    @Override
    public void clearActiveWaypoint() {
        if (waypointMarker != null) waypointMarker.remove();
        waypointMarker = null;
    }

    @Override
    public void moveTo(GeoPoint point, float zoom) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(toLatLng(point), zoom));
    }

    @Override
    public void fitRoute(List<GeoPoint> points) {
        if (points.isEmpty()) return;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (GeoPoint point : points) builder.include(toLatLng(point));
        try {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 96));
        } catch (IllegalStateException ignored) {
            moveTo(points.get(0), 16.0f);
        }
    }

    private static LatLng toLatLng(GeoPoint point) {
        return new LatLng(point.getLatitude(), point.getLongitude());
    }
}
