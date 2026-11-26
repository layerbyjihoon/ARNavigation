package com.example.arnavigation.ui.map;

import com.example.arnavigation.config.AppConfig;
import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.OutdoorRoute;
import com.example.arnavigation.navigation.NavigationStage;
import com.example.arnavigation.navigation.NavigationUiState;
import com.google.android.gms.maps.GoogleMap;

/** Idempotently projects NavigationUiState onto Google Map primitives. */
public final class MapRenderer {
    private MapSurface surface;
    private NavigationUiState pendingState;
    private Destination renderedDestination;
    private OutdoorRoute renderedRoute;
    private java.util.List<com.example.arnavigation.model.GeoPoint> renderedPoints = java.util.Collections.emptyList();
    private RemainingMapRoute remainingRoute = new RemainingMapRoute();
    private boolean renderedDestinationConfirmed;
    private boolean initialCameraPositioned;
    private boolean locationPermissionGranted;
    private boolean centerRequested;
    private boolean centerNextRouteOnLocation;

    public void attach(GoogleMap map) {
        attachSurface(new GoogleMapSurface(map));
    }

    void attachSurface(MapSurface surface) {
        this.surface = java.util.Objects.requireNonNull(surface, "surface must not be null");
        renderedDestination = null;
        renderedRoute = null;
        renderedPoints = java.util.Collections.emptyList();
        remainingRoute = new RemainingMapRoute();
        initialCameraPositioned = false;
        surface.setMyLocationEnabled(locationPermissionGranted);
        if (pendingState != null) renderNow(pendingState);
    }

    public void detach() {
        surface = null;
    }

    public void setLocationPermissionGranted(boolean granted) {
        locationPermissionGranted = granted;
        if (surface != null) surface.setMyLocationEnabled(granted);
        applyCenterRequest();
    }

    /** Center once on map entry or a current-location tap; await map/GPS readiness if needed. */
    public void centerOnCurrentLocation() {
        centerRequested = true;
        applyCenterRequest();
    }

    private void applyCenterRequest() {
        if (!centerRequested || surface == null || !locationPermissionGranted || pendingState == null
                || pendingState.getLocation() == null || !pendingState.getLocationStatus().isMapUsable()) return;
        surface.moveTo(pendingState.getLocation().getPoint(), 18f);
        centerRequested = false;
    }

    public void onDestinationConfirmed() {
        centerNextRouteOnLocation = true;
        centerOnCurrentLocation();
    }

    public void onMapOpened() {
        if (pendingState != null && pendingState.getStage() == NavigationStage.DESTINATION_SELECTED
                && pendingState.getDestination() != null) {
            centerRequested = false;
            if (surface != null) centerOnDestination(pendingState.getDestination());
        } else {
            centerOnCurrentLocation();
        }
    }

    private void centerOnDestination(Destination destination) {
        centerNextRouteOnLocation = false;
        centerRequested = false;
        surface.moveTo(destination.getPoint(), 18f);
        initialCameraPositioned = true;
    }

    public void render(NavigationUiState state) {
        pendingState = java.util.Objects.requireNonNull(state, "state must not be null");
        if (surface != null) renderNow(state);
    }

    private void renderNow(NavigationUiState state) {
        Destination destination = state.getDestination();
        boolean destinationChanged = !java.util.Objects.equals(destination, renderedDestination);
        if (destinationChanged && destination != null && state.getStage() == NavigationStage.DESTINATION_SELECTED)
            centerOnDestination(destination);
        if (!initialCameraPositioned) {
            if (state.getLocation() != null) {
                surface.moveTo(state.getLocation().getPoint(), AppConfig.CAMPUS_DEFAULT_ZOOM);
            } else {
                surface.moveTo(
                        new com.example.arnavigation.model.GeoPoint(
                                AppConfig.CAMPUS_CENTER_LATITUDE,
                                AppConfig.CAMPUS_CENTER_LONGITUDE),
                        AppConfig.CAMPUS_DEFAULT_ZOOM);
            }
            initialCameraPositioned = true;
        }

        if (destinationChanged) {
            surface.clearDestination();
            surface.clearRoute();
            surface.clearActiveWaypoint();
            renderedDestination = destination;
            renderedRoute = null;
            renderedPoints = java.util.Collections.emptyList();
            remainingRoute = new RemainingMapRoute();
        }
        if (destination != null) {
            boolean confirmed = state.getStage() != NavigationStage.DESTINATION_SELECTED;
            if (destinationChanged || confirmed != renderedDestinationConfirmed) {
                surface.showDestination(destination.getPoint(), destination.getName(), confirmed);
                renderedDestinationConfirmed = confirmed;
            }
        } else {
            surface.clearDestination();
            renderedDestinationConfirmed = false;
        }

        boolean routeChanged = state.getRoute() != renderedRoute;
        if (routeChanged) {
            renderedRoute = state.getRoute();
            remainingRoute = new RemainingMapRoute();
        }
        java.util.List<com.example.arnavigation.model.GeoPoint> points = renderedPoints;
        if (renderedRoute == null || state.getStage() == NavigationStage.ARRIVED) {
            points = java.util.Collections.emptyList();
        } else if (state.getLocation() != null
                && state.getLocationStatus() == com.example.arnavigation.location.LocationStatus.AVAILABLE) {
            points = remainingRoute.update(renderedRoute.getPolylinePoints(),
                    state.getLocation().getPoint(), state.getActiveWaypoint() == null
                            ? null : state.getActiveWaypoint().getPoint());
        } else if (routeChanged) {
            points = renderedRoute.getPolylinePoints();
        }
        if (!points.equals(renderedPoints)) {
            renderedPoints = points;
            surface.clearRoute();
            if (points.size() >= 2) surface.showRoute(points);
        }
        // Refit on a new route only; GPS/heading updates must not interrupt manual map panning.
        if (routeChanged && points.size() >= 2) {
            if (centerNextRouteOnLocation) {
                centerRequested = true;
                centerNextRouteOnLocation = false;
            } else {
                surface.fitRoute(points);
            }
        }
        applyCenterRequest();
    }
}
