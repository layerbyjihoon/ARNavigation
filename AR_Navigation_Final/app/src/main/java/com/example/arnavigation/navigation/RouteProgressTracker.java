package com.example.arnavigation.navigation;

import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.LocationSample;
import com.example.arnavigation.model.OutdoorRoute;
import com.example.arnavigation.model.Waypoint;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Stateful waypoint advancement and sustained polyline-deviation detector. */
public final class RouteProgressTracker {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0d;
    private final NavigationConfig config;
    private final DirectionCalculator directionCalculator;
    private int activeWaypointIndex;
    private LocationSample previousLocation;
    private Long offRouteSinceNanos;
    private OutdoorRoute projectedRoute;
    private double[] pathDistances;
    private double[] waypointDistances;

    public RouteProgressTracker(
            NavigationConfig config, DirectionCalculator directionCalculator) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.directionCalculator = Objects.requireNonNull(
                directionCalculator, "directionCalculator must not be null");
    }

    public RouteProgress update(LocationSample location, OutdoorRoute route) {
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(route, "route must not be null");
        prepareProjection(route);
        List<Waypoint> waypoints = route.getWaypoints();
        while (activeWaypointIndex < waypoints.size()) {
            GeoPoint target = waypoints.get(activeWaypointIndex).getPoint();
            boolean reached = directionCalculator.distanceMeters(location.getPoint(), target)
                    <= config.getWaypointRadiusMeters();
            boolean passed = previousLocation != null && passedBetween(
                    previousLocation.getPoint(), location.getPoint(), target,
                    config.getWaypointRadiusMeters());
            if (!reached && !passed && !passedAlongRoute(location, route)) break;
            activeWaypointIndex++;
        }

        double routeDistance = distanceToPolylineMeters(
                location.getPoint(), route.getPolylinePoints());
        boolean offRoute = updateOffRoute(routeDistance, location.getElapsedRealtimeNanos());
        previousLocation = location;

        boolean completed = activeWaypointIndex >= waypoints.size();
        Waypoint active = completed ? null : waypoints.get(activeWaypointIndex);
        double waypointDistance = active == null ? 0.0d
                : directionCalculator.distanceMeters(location.getPoint(), active.getPoint());
        return new RouteProgress(
                activeWaypointIndex, active, waypointDistance, routeDistance, offRoute, completed);
    }

    public void reset() {
        activeWaypointIndex = 0;
        previousLocation = null;
        offRouteSinceNanos = null;
        projectedRoute = null;
        pathDistances = null;
        waypointDistances = null;
    }

    private void prepareProjection(OutdoorRoute route) {
        if (projectedRoute == route) return;
        reset();
        projectedRoute = route;
        List<GeoPoint> path = route.getPolylinePoints();
        pathDistances = new double[path.size()];
        for (int i = 1; i < path.size(); i++) {
            pathDistances[i] = pathDistances[i - 1]
                    + directionCalculator.distanceMeters(path.get(i - 1), path.get(i));
        }
        waypointDistances = new double[route.getWaypoints().size()];
        double from = 0;
        double end = path.isEmpty() ? 0 : pathDistances[path.size() - 1];
        for (int i = 0; i < waypointDistances.length; i++) {
            PathPosition match = nearestOnPath(route.getWaypoints().get(i).getPoint(), path, from, end);
            if (match != null) from = match.along;
            waypointDistances[i] = from;
        }
    }

    /** A waypoint is an instruction on the path, not a checkpoint the walker must touch. */
    private boolean passedAlongRoute(LocationSample location, OutdoorRoute route) {
        // Keep the final waypoint/arrival decision tied to actually reaching the destination.
        if (activeWaypointIndex >= waypointDistances.length - 1 || pathDistances.length < 2) return false;
        double target = waypointDistances[activeWaypointIndex];
        double from = activeWaypointIndex == 0 ? 0 : waypointDistances[activeWaypointIndex - 1];
        List<GeoPoint> path = route.getPolylinePoints();
        double end = pathDistances[pathDistances.length - 1];
        PathPosition incoming = nearestOnPath(location.getPoint(), path, from, target);
        PathPosition outgoing = nearestOnPath(location.getPoint(), path, target, end);
        if (incoming == null || outgoing == null
                || outgoing.distance > config.getOffRouteDistanceMeters()
                || outgoing.along <= target + Math.max(1, config.getWaypointRadiusMeters())) return false;
        // Prefer the current leg when parallel or crossing legs fit within GPS uncertainty.
        double margin = Math.min(config.getWaypointRadiusMeters(), Math.max(1, location.getAccuracyMeters()));
        if (outgoing.distance + margin >= incoming.distance) return false;
        if (previousLocation != null) {
            PathPosition previous = nearestOnPath(previousLocation.getPoint(), path, target, end);
            if (previous != null && previous.distance <= config.getOffRouteDistanceMeters()
                    && outgoing.along + 1 < previous.along) return false;
        }
        return true;
    }

    private PathPosition nearestOnPath(GeoPoint point, List<GeoPoint> path, double from, double to) {
        PathPosition best = null;
        for (int i = 1; i < path.size(); i++) {
            double length = pathDistances[i] - pathDistances[i - 1];
            if (length < 1.0e-6 || pathDistances[i] < from || pathDistances[i - 1] > to) continue;
            LocalPoint a = project(path.get(i - 1), point), b = project(path.get(i), point);
            double dx = b.x - a.x, dy = b.y - a.y;
            double squared = dx * dx + dy * dy;
            if (squared < 1.0e-12) continue;
            double lower = Math.max(0, (from - pathDistances[i - 1]) / length);
            double upper = Math.min(1, (to - pathDistances[i - 1]) / length);
            double t = Math.max(lower, Math.min(upper, -(a.x * dx + a.y * dy) / squared));
            double distance = Math.hypot(a.x + t * dx, a.y + t * dy);
            // Strict comparison keeps the earliest occurrence at route crossings.
            if (best == null || distance < best.distance - 1.0e-6) {
                best = new PathPosition(pathDistances[i - 1] + t * length, distance);
            }
        }
        return best;
    }

    private static final class PathPosition {
        final double along;
        final double distance;
        PathPosition(double along, double distance) { this.along = along; this.distance = distance; }
    }

    private boolean updateOffRoute(double distanceMeters, long timestampNanos) {
        if (distanceMeters <= config.getOffRouteDistanceMeters()) {
            offRouteSinceNanos = null;
            return false;
        }
        if (offRouteSinceNanos == null || timestampNanos < offRouteSinceNanos) {
            offRouteSinceNanos = timestampNanos;
            return config.getOffRouteDurationMillis() == 0L;
        }
        long requiredNanos = TimeUnit.MILLISECONDS.toNanos(config.getOffRouteDurationMillis());
        return timestampNanos - offRouteSinceNanos >= requiredNanos;
    }

    private static boolean passedBetween(
            GeoPoint previous, GeoPoint current, GeoPoint target, double radiusMeters) {
        LocalPoint start = project(previous, target);
        LocalPoint end = project(current, target);
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double movementSquared = dx * dx + dy * dy;
        if (movementSquared < 1.0e-6d) return false;
        double t = -(start.x * dx + start.y * dy) / movementSquared;
        if (t < 0.0d || t > 1.0d) return false;
        double closestX = start.x + t * dx;
        double closestY = start.y + t * dy;
        return Math.hypot(closestX, closestY) <= radiusMeters;
    }

    static double distanceToPolylineMeters(GeoPoint point, List<GeoPoint> polyline) {
        if (polyline == null || polyline.isEmpty()) return Double.POSITIVE_INFINITY;
        if (polyline.size() == 1) {
            LocalPoint only = project(polyline.get(0), point);
            return Math.hypot(only.x, only.y);
        }
        double minimum = Double.POSITIVE_INFINITY;
        for (int index = 1; index < polyline.size(); index++) {
            LocalPoint start = project(polyline.get(index - 1), point);
            LocalPoint end = project(polyline.get(index), point);
            double dx = end.x - start.x;
            double dy = end.y - start.y;
            double lengthSquared = dx * dx + dy * dy;
            double t = lengthSquared == 0.0d ? 0.0d
                    : Math.max(0.0d, Math.min(1.0d,
                            -(start.x * dx + start.y * dy) / lengthSquared));
            minimum = Math.min(minimum,
                    Math.hypot(start.x + t * dx, start.y + t * dy));
        }
        return minimum;
    }

    private static LocalPoint project(GeoPoint point, GeoPoint origin) {
        double meanLatitude = Math.toRadians((point.getLatitude() + origin.getLatitude()) / 2.0d);
        double x = Math.toRadians(point.getLongitude() - origin.getLongitude())
                * Math.cos(meanLatitude) * EARTH_RADIUS_METERS;
        double y = Math.toRadians(point.getLatitude() - origin.getLatitude())
                * EARTH_RADIUS_METERS;
        return new LocalPoint(x, y);
    }

    private static final class LocalPoint {
        final double x;
        final double y;

        LocalPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}
