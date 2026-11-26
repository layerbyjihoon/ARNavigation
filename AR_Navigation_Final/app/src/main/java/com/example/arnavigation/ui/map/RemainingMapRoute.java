package com.example.arnavigation.ui.map;

import com.example.arnavigation.model.GeoPoint;
import java.util.ArrayList;
import java.util.List;

/** Clips the displayed route at the current position without altering the provider's route. */
final class RemainingMapRoute {
    int segment;

    List<GeoPoint> update(List<GeoPoint> path, GeoPoint current, GeoPoint nextWaypoint) {
        if (path.size() < 2) return new ArrayList<>(path);
        segment = Math.min(segment, path.size() - 2);
        // Restrict snapping to the upcoming waypoint, so a later loop near the user cannot
        // discard a bend that has not been walked yet.
        int last = path.size() - 2;
        if (nextWaypoint != null) last = nearest(path, nextWaypoint, segment, last).segment;
        Projection cut = nearest(path, current, segment, last);
        segment = cut.segment;
        List<GeoPoint> result = new ArrayList<>();
        append(result, current);
        append(result, cut.point);
        for (int i = segment + 1; i < path.size(); i++) append(result, path.get(i));
        return result;
    }

    private static void append(List<GeoPoint> points, GeoPoint point) {
        if (points.isEmpty() || !points.get(points.size() - 1).equals(point)) points.add(point);
    }

    private static Projection nearest(List<GeoPoint> points, GeoPoint location, int first, int last) {
        Projection best = null;
        double scale = Math.cos(Math.toRadians(location.getLatitude()));
        for (int i = first; i <= last; i++) {
            GeoPoint a = points.get(i), b = points.get(i + 1);
            double ax = (a.getLongitude() - location.getLongitude()) * scale;
            double ay = a.getLatitude() - location.getLatitude();
            double dx = (b.getLongitude() - a.getLongitude()) * scale;
            double dy = b.getLatitude() - a.getLatitude();
            double length = dx * dx + dy * dy;
            double t = length == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / length));
            double distance = Math.pow(ax + t * dx, 2) + Math.pow(ay + t * dy, 2);
            if (best == null || distance < best.distance) {
                best = new Projection(i, new GeoPoint(a.getLatitude() + t * dy,
                        a.getLongitude() + t * (b.getLongitude() - a.getLongitude())), distance);
            }
        }
        return best;
    }

    private static final class Projection {
        final int segment;
        final GeoPoint point;
        final double distance;
        Projection(int segment, GeoPoint point, double distance) {
            this.segment = segment; this.point = point; this.distance = distance;
        }
    }
}
