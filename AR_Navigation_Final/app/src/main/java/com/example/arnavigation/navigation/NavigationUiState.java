package com.example.arnavigation.navigation;

import com.example.arnavigation.heading.HeadingStatus;
import com.example.arnavigation.location.LocationStatus;
import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.HeadingSample;
import com.example.arnavigation.model.LocationSample;
import com.example.arnavigation.model.OutdoorRoute;
import com.example.arnavigation.model.Waypoint;
import com.example.arnavigation.routing.RouteError;

/** Complete immutable render state shared by map and AR screens. */
public final class NavigationUiState {
    private final NavigationStage stage;
    private final Destination destination;
    private final OutdoorRoute route;
    private final Waypoint activeWaypoint;
    private final int activeWaypointIndex;
    private final LocationSample location;
    private final LocationStatus locationStatus;
    private final HeadingSample heading;
    private final HeadingStatus headingStatus;
    private final Double distanceToWaypointMeters;
    private final Double relativeAngleDegrees;
    private final boolean offRoute;
    private final boolean rerouting;
    private final RouteError routeError;
    private final boolean cameraAvailable;
    private final boolean arrowVisible;

    public NavigationUiState(
            NavigationStage stage,
            Destination destination,
            OutdoorRoute route,
            Waypoint activeWaypoint,
            int activeWaypointIndex,
            LocationSample location,
            LocationStatus locationStatus,
            HeadingSample heading,
            HeadingStatus headingStatus,
            Double distanceToWaypointMeters,
            Double relativeAngleDegrees,
            boolean offRoute,
            boolean rerouting,
            RouteError routeError,
            boolean cameraAvailable,
            boolean arrowVisible) {
        this.stage = stage;
        this.destination = destination;
        this.route = route;
        this.activeWaypoint = activeWaypoint;
        this.activeWaypointIndex = activeWaypointIndex;
        this.location = location;
        this.locationStatus = locationStatus;
        this.heading = heading;
        this.headingStatus = headingStatus;
        this.distanceToWaypointMeters = distanceToWaypointMeters;
        this.relativeAngleDegrees = relativeAngleDegrees;
        this.offRoute = offRoute;
        this.rerouting = rerouting;
        this.routeError = routeError;
        this.cameraAvailable = cameraAvailable;
        this.arrowVisible = arrowVisible;
    }

    public NavigationStage getStage() { return stage; }
    public Destination getDestination() { return destination; }
    public OutdoorRoute getRoute() { return route; }
    public Waypoint getActiveWaypoint() { return activeWaypoint; }
    public int getActiveWaypointIndex() { return activeWaypointIndex; }
    public LocationSample getLocation() { return location; }
    public LocationStatus getLocationStatus() { return locationStatus; }
    public HeadingSample getHeading() { return heading; }
    public HeadingStatus getHeadingStatus() { return headingStatus; }
    public Double getDistanceToWaypointMeters() { return distanceToWaypointMeters; }
    public Double getRelativeAngleDegrees() { return relativeAngleDegrees; }
    public boolean isOffRoute() { return offRoute; }
    public boolean isRerouting() { return rerouting; }
    public RouteError getRouteError() { return routeError; }
    public boolean isCameraAvailable() { return cameraAvailable; }
    public boolean isArrowVisible() { return arrowVisible; }
}
