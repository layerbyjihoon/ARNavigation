package com.example.arnavigation.model;

import java.util.Objects;

/** Immutable route destination. placeId is null for non-Places selections. */
public final class Destination {
    private final String id;
    private final String name;
    private final GeoPoint point;
    private final DestinationSource source;
    private final String placeId;

    public Destination(
            String id,
            String name,
            GeoPoint point,
            DestinationSource source,
            String placeId) {
        this.id = ModelValidation.requireText(id, "id");
        this.name = ModelValidation.requireText(name, "name");
        this.point = ModelValidation.requireNonNull(point, "point");
        this.source = ModelValidation.requireNonNull(source, "source");
        this.placeId = ModelValidation.optionalText(placeId, "placeId");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public GeoPoint getPoint() {
        return point;
    }

    public DestinationSource getSource() {
        return source;
    }

    /** Returns the Google Place ID, or null when the destination did not come from Places. */
    public String getPlaceId() {
        return placeId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Destination)) return false;
        Destination that = (Destination) other;
        return id.equals(that.id)
                && name.equals(that.name)
                && point.equals(that.point)
                && source == that.source
                && Objects.equals(placeId, that.placeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, point, source, placeId);
    }
}
