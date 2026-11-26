package com.example.arnavigation.navigation;

import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.OutdoorRoute;
import java.util.List;

/** Keeps the AR direction local while instruction/arrival progress remains unchanged. */
final class RouteDirectionTarget {
    static final double LOOK_AHEAD_METERS = 10.0;
    private static final DirectionCalculator DISTANCE = new DirectionCalculator();

    private RouteDirectionTarget() {}

    static GeoPoint select(OutdoorRoute route, GeoPoint current, int activeIndex) {
        return aim(route, current, activeIndex).target;
    }

    static double bearing(OutdoorRoute route, GeoPoint current, int activeIndex, double corridorMeters) {
        Aim aim = aim(route, current, activeIndex);
        GeoPoint origin = DISTANCE.distanceMeters(current, aim.snapped) <= corridorMeters
                ? aim.origin : current;
        return DISTANCE.bearingDegrees(origin, aim.target);
    }

    private static Aim aim(OutdoorRoute route, GeoPoint current, int activeIndex) {
        List<GeoPoint> path = route.getPolylinePoints();
        GeoPoint fallback = route.getWaypoints().get(activeIndex).getPoint();
        if (path.size() < 2) return new Aim(current, current, fallback);
        double[] stations = new double[path.size()];
        for (int i = 1; i < path.size(); i++) {
            stations[i] = stations[i - 1] + DISTANCE.distanceMeters(path.get(i - 1), path.get(i));
        }
        // Match instructions in route order so nearby later loops cannot steal the target.
        double from = 0, end = stations[stations.length - 1], to = end;
        for (int i = 0; i <= activeIndex; i++) {
            to = nearest(path, stations, route.getWaypoints().get(i).getPoint(), from, end);
            if (i < activeIndex) from = to;
        }
        double here = nearest(path, stations, current, from, to);
        GeoPoint snapped = at(path, stations, here);
        GeoPoint target = at(path, stations, Math.min(to, here + LOOK_AHEAD_METERS));
        // At an instruction boundary GPS can project exactly onto the target. Keep the
        // incoming direction until progress advances, instead of bearing(same, same)=north.
        GeoPoint origin = DISTANCE.distanceMeters(snapped, target) < .1
                ? at(path, stations, Math.max(from, here - LOOK_AHEAD_METERS)) : snapped;
        if (DISTANCE.distanceMeters(origin, target) < .1) origin = current;
        return new Aim(snapped, origin, target);
    }

    private static final class Aim {
        final GeoPoint snapped, origin, target;
        Aim(GeoPoint snapped, GeoPoint origin, GeoPoint target) {
            this.snapped = snapped; this.origin = origin; this.target = target;
        }
    }

    private static double nearest(List<GeoPoint> path, double[] stations, GeoPoint location,
            double from, double to) {
        double best = from, bestDistance = Double.POSITIVE_INFINITY;
        double scale = Math.cos(Math.toRadians(location.getLatitude()));
        for (int i = 1; i < path.size(); i++) {
            double length = stations[i] - stations[i - 1];
            if (length < 1e-6 || stations[i] < from || stations[i - 1] > to) continue;
            GeoPoint a = path.get(i - 1), b = path.get(i);
            double ax = (a.getLongitude() - location.getLongitude()) * scale;
            double ay = a.getLatitude() - location.getLatitude();
            double dx = (b.getLongitude() - a.getLongitude()) * scale;
            double dy = b.getLatitude() - a.getLatitude();
            double squared = dx * dx + dy * dy;
            if (squared < 1e-18) continue;
            double lower = Math.max(0, (from - stations[i - 1]) / length);
            double upper = Math.min(1, (to - stations[i - 1]) / length);
            double t = Math.max(lower, Math.min(upper, -(ax * dx + ay * dy) / squared));
            double distance = Math.hypot(ax + t * dx, ay + t * dy);
            if (distance < bestDistance - 1e-12) {
                bestDistance = distance;
                best = stations[i - 1] + t * length;
            }
        }
        return best;
    }

    private static GeoPoint at(List<GeoPoint> path, double[] stations, double station) {
        for (int i = 1; i < path.size(); i++) {
            double length = stations[i] - stations[i - 1];
            if (length < 1e-6 || station > stations[i]) continue;
            double t = Math.max(0, Math.min(1, (station - stations[i - 1]) / length));
            GeoPoint a = path.get(i - 1), b = path.get(i);
            return new GeoPoint(a.getLatitude() + t * (b.getLatitude() - a.getLatitude()),
                    a.getLongitude() + t * (b.getLongitude() - a.getLongitude()));
        }
        return path.get(path.size() - 1);
    }
}
