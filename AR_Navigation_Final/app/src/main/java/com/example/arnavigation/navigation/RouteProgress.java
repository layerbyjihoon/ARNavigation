package com.example.arnavigation.navigation;

import com.example.arnavigation.model.Waypoint;

/** Immutable result of applying one location sample to route progress. */
public final class RouteProgress {
    private final int activeWaypointIndex;
    private final Waypoint activeWaypoint;
    private final double distanceToWaypointMeters;
    private final double distanceToRouteMeters;
    private final boolean offRoute;
    private final boolean routeCompleted;

    RouteProgress(
            int activeWaypointIndex,
            Waypoint activeWaypoint,
            double distanceToWaypointMeters,
            double distanceToRouteMeters,
            boolean offRoute,
            boolean routeCompleted) {
        this.activeWaypointIndex = activeWaypointIndex;
        this.activeWaypoint = activeWaypoint;
        this.distanceToWaypointMeters = distanceToWaypointMeters;
        this.distanceToRouteMeters = distanceToRouteMeters;
        this.offRoute = offRoute;
        this.routeCompleted = routeCompleted;
    }

    public int getActiveWaypointIndex() { return activeWaypointIndex; }
    public Waypoint getActiveWaypoint() { return activeWaypoint; }
    public double getDistanceToWaypointMeters() { return distanceToWaypointMeters; }
    public double getDistanceToRouteMeters() { return distanceToRouteMeters; }
    public boolean isOffRoute() { return offRoute; }
    public boolean isRouteCompleted() { return routeCompleted; }
}
