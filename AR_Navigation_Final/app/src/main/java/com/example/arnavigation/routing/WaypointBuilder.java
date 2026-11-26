package com.example.arnavigation.routing;

import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.ManeuverType;
import com.example.arnavigation.model.Waypoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Deterministically merges nearby non-turn steps and guarantees a final destination waypoint. */
public final class WaypointBuilder {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0d;
    private final double minimumSpacingMeters;

    public WaypointBuilder(double minimumSpacingMeters) {
        if (!Double.isFinite(minimumSpacingMeters) || minimumSpacingMeters < 0.0d) {
            throw new IllegalArgumentException("minimumSpacingMeters must be finite and non-negative");
        }
        this.minimumSpacingMeters = minimumSpacingMeters;
    }

    public List<Waypoint> build(List<StepWaypoint> steps, GeoPoint destination) {
        Objects.requireNonNull(steps, "steps must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        ArrayList<StepWaypoint> retained = new ArrayList<>();
        for (StepWaypoint step : steps) {
            Objects.requireNonNull(step, "step must not be null");
            if (!retained.isEmpty()) {
                StepWaypoint previous = retained.get(retained.size() - 1);
                boolean close = distanceMeters(previous.point, step.point) < minimumSpacingMeters;
                if (close && !isDecisionPoint(previous.maneuver) && !isDecisionPoint(step.maneuver)) {
                    retained.set(retained.size() - 1, step);
                    continue;
                }
            }
            retained.add(step);
        }

        ArrayList<Waypoint> result = new ArrayList<>(retained.size() + 1);
        for (StepWaypoint step : retained) {
            int index = result.size();
            result.add(new Waypoint(
                    "waypoint-" + index,
                    step.point,
                    step.instruction,
                    step.maneuver,
                    index));
        }
        int destinationIndex = result.size();
        result.add(new Waypoint(
                "waypoint-" + destinationIndex,
                destination,
                null,
                ManeuverType.ARRIVE,
                destinationIndex));
        return Collections.unmodifiableList(result);
    }

    private static boolean isDecisionPoint(ManeuverType maneuver) {
        return maneuver != ManeuverType.START
                && maneuver != ManeuverType.STRAIGHT
                && maneuver != ManeuverType.UNKNOWN
                && maneuver != ManeuverType.ARRIVE;
    }

    static double distanceMeters(GeoPoint from, GeoPoint to) {
        double latitude1 = Math.toRadians(from.getLatitude());
        double latitude2 = Math.toRadians(to.getLatitude());
        double latitudeDelta = latitude2 - latitude1;
        double longitudeDelta = Math.toRadians(to.getLongitude() - from.getLongitude());
        double a = Math.sin(latitudeDelta / 2.0d) * Math.sin(latitudeDelta / 2.0d)
                + Math.cos(latitude1) * Math.cos(latitude2)
                * Math.sin(longitudeDelta / 2.0d) * Math.sin(longitudeDelta / 2.0d);
        return EARTH_RADIUS_METERS * 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
    }

    public static final class StepWaypoint {
        private final GeoPoint point;
        private final String instruction;
        private final ManeuverType maneuver;

        public StepWaypoint(GeoPoint point, String instruction, ManeuverType maneuver) {
            this.point = Objects.requireNonNull(point, "point must not be null");
            if (instruction != null && instruction.trim().isEmpty()) {
                throw new IllegalArgumentException("instruction must be null or non-blank");
            }
            this.instruction = instruction;
            this.maneuver = Objects.requireNonNull(maneuver, "maneuver must not be null");
        }
    }
}
