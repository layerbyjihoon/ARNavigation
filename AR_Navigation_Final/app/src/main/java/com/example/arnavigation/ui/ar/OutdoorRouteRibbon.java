package com.example.arnavigation.ui.ar;

import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.Waypoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** GPS/heading ground-plane projection. This is guidance, not an ARCore world anchor. */
final class OutdoorRouteRibbon {
    static final int BORDER = 0xff041f29;
    static final int ACTIVE = 0xff0ae0ff;
    static final int FUTURE = 0xff0a6b99;
    static final int ARROW = 0xffebffff;
    private static final double NEAR = 1.5;
    private static final double FAR = 12;
    static final double LOOK_AHEAD_METERS = 12;
    private static final double EARTH_RADIUS = 6371000;
    private static final int MAX_SEGMENTS = 128;

    static final class Polygon {
        final float[] xy;
        final int color;
        Polygon(float[] xy, int color) { this.xy = xy; this.color = color; }
    }

    static List<Polygon> build(GeoPoint origin, double headingDegrees,
            List<Waypoint> waypoints, int activeIndex) {
        if (origin == null || !Double.isFinite(headingDegrees) || activeIndex < 0
                || activeIndex >= waypoints.size()) return Collections.emptyList();
        double heading = Math.toRadians(headingDegrees);
        List<Polygon> result = new ArrayList<>();
        Point from = new Point(0, 0);
        int segments = 0;
        double remaining = LOOK_AHEAD_METERS;
        for (int i = activeIndex; i < waypoints.size() && segments < MAX_SEGMENTS && remaining > .01; i++) {
            GeoPoint target = waypoints.get(i).getPoint();
            double east = EARTH_RADIUS * Math.toRadians(target.getLongitude() - origin.getLongitude())
                    * Math.cos(Math.toRadians(origin.getLatitude()));
            double north = EARTH_RADIUS * Math.toRadians(target.getLatitude() - origin.getLatitude());
            Point to = new Point(east * Math.cos(heading) - north * Math.sin(heading),
                    east * Math.sin(heading) + north * Math.cos(heading));
            double length = Math.hypot(to.x - from.x, to.z - from.z);
            if (length > 0.01) {
                // Limit by distance along the upcoming path, not just camera depth. Otherwise
                // a distant U-turn can re-enter the frustum and draw unrelated future arrows.
                double used = Math.min(length, remaining);
                Point end = new Point(from.x + (to.x - from.x) * used / length,
                        from.z + (to.z - from.z) * used / length);
                addSegment(result, from, end, segments == 0 ? ACTIVE : FUTURE);
                remaining -= used;
                segments++;
            }
            from = to;
        }
        return result;
    }

    private static void addSegment(List<Polygon> result, Point from, Point to, int color) {
        double length = Math.hypot(to.x - from.x, to.z - from.z);
        double dx = (to.x - from.x) / length;
        double dz = (to.z - from.z) / length;
        // Same ribbon proportions and 60cm repeated triangle spacing as the indoor renderer.
        quad(result, from, to, -dz * .09, dx * .09, BORDER);
        quad(result, from, to, -dz * .06, dx * .06, color);
        // Limit work to the portion intersecting the visible depth range, even for long routes.
        double start = 0;
        double end = length;
        if (Math.abs(dz) < 1e-9) {
            if (from.z < NEAR || from.z > FAR) return;
        } else {
            double nearD = (NEAR - from.z) / dz;
            double farD = (FAR - from.z) / dz;
            start = Math.max(0, Math.min(nearD, farD));
            end = Math.min(length, Math.max(nearD, farD));
        }
        // Bounded independently of API response size or the distance to a remote destination.
        for (double d = Math.max(.6, Math.ceil(start / .6) * .6);
                d < end - .12 && d < start + 80; d += .6) {
            Point tip = new Point(from.x + dx * d, from.z + dz * d);
            Point tail = new Point(tip.x - dx * .16, tip.z - dz * .16);
            polygon(result, new Point[] {tip,
                    new Point(tail.x - dz * .045, tail.z + dx * .045),
                    new Point(tail.x + dz * .045, tail.z - dx * .045)}, ARROW);
        }
    }

    private static void quad(List<Polygon> result, Point a, Point b,
            double sideX, double sideZ, int color) {
        polygon(result, new Point[] {new Point(a.x + sideX, a.z + sideZ),
                new Point(a.x - sideX, a.z - sideZ), new Point(b.x - sideX, b.z - sideZ),
                new Point(b.x + sideX, b.z + sideZ)}, color);
    }

    private static void polygon(List<Polygon> result, Point[] points, int color) {
        List<Point> clipped = clip(java.util.Arrays.asList(points), NEAR, true);
        clipped = clip(clipped, FAR, false);
        if (clipped.size() < 3) return;
        float[] xy = new float[clipped.size() * 2];
        for (int i = 0; i < clipped.size(); i++) {
            Point p = clipped.get(i);
            // Fixed virtual camera height/horizon; no claim of physical ground registration.
            xy[i * 2] = (float) (.5 + .75 * p.x / p.z);
            xy[i * 2 + 1] = (float) (.42 + 1.05 / p.z);
        }
        result.add(new Polygon(xy, color));
    }

    private static List<Point> clip(List<Point> points, double plane, boolean keepGreater) {
        List<Point> result = new ArrayList<>();
        if (points.isEmpty()) return result;
        Point previous = points.get(points.size() - 1);
        boolean previousInside = keepGreater ? previous.z >= plane : previous.z <= plane;
        for (Point current : points) {
            boolean inside = keepGreater ? current.z >= plane : current.z <= plane;
            if (inside != previousInside) {
                double t = (plane - previous.z) / (current.z - previous.z);
                result.add(new Point(previous.x + t * (current.x - previous.x), plane));
            }
            if (inside) result.add(current);
            previous = current;
            previousInside = inside;
        }
        return result;
    }

    private static final class Point {
        final double x;
        final double z;
        Point(double x, double z) { this.x = x; this.z = z; }
    }
}
