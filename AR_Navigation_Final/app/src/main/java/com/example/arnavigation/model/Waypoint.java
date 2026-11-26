package com.example.arnavigation.model;

import java.util.Objects;

/** Immutable route waypoint. instruction may be null when the provider supplies no text. */
public final class Waypoint {
    private final String id;
    private final GeoPoint point;
    private final String instruction;
    private final ManeuverType maneuver;
    private final int routeIndex;

    public Waypoint(
            String id,
            GeoPoint point,
            String instruction,
            ManeuverType maneuver,
            int routeIndex) {
        this.id = ModelValidation.requireText(id, "id");
        this.point = ModelValidation.requireNonNull(point, "point");
        this.instruction = ModelValidation.optionalText(instruction, "instruction");
        this.maneuver = ModelValidation.requireNonNull(maneuver, "maneuver");
        if (routeIndex < 0) {
            throw new IllegalArgumentException("routeIndex must not be negative");
        }
        this.routeIndex = routeIndex;
    }

    public String getId() {
        return id;
    }

    public GeoPoint getPoint() {
        return point;
    }

    public String getInstruction() {
        return instruction;
    }

    public ManeuverType getManeuver() {
        return maneuver;
    }

    public int getRouteIndex() {
        return routeIndex;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Waypoint)) return false;
        Waypoint that = (Waypoint) other;
        return routeIndex == that.routeIndex
                && id.equals(that.id)
                && point.equals(that.point)
                && Objects.equals(instruction, that.instruction)
                && maneuver == that.maneuver;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, point, instruction, maneuver, routeIndex);
    }
}
