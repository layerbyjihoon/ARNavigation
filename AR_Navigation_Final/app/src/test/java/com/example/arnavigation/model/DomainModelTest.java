package com.example.arnavigation.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class DomainModelTest {
    @Test
    public void geoPointAcceptsBoundariesAndUsesValueEquality() {
        assertEquals(new GeoPoint(-90.0, -180.0), new GeoPoint(-90.0, -180.0));
        assertEquals(new GeoPoint(90.0, 180.0).hashCode(), new GeoPoint(90.0, 180.0).hashCode());
    }

    @Test
    public void geoPointRejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(-90.01, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0.0, 180.01));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0.0, Double.POSITIVE_INFINITY));
    }

    @Test
    public void nullableContractsArePreserved() {
        GeoPoint point = new GeoPoint(37.496, 126.957);
        Destination destination = new Destination(
                "destination-1", "숭실대학교", point, DestinationSource.MAP_TAP, null);
        Waypoint waypoint = new Waypoint("waypoint-1", point, null, ManeuverType.UNKNOWN, 0);
        LocationSample location = new LocationSample(point, 5.0, null, 0.0, 1L);

        assertNull(destination.getPlaceId());
        assertNull(waypoint.getInstruction());
        assertNull(location.getBearingDegrees());
    }

    @Test
    public void routeDefensivelyCopiesAndExposesUnmodifiableCollections() {
        GeoPoint point = new GeoPoint(37.496, 126.957);
        Waypoint waypoint = new Waypoint("waypoint-1", point, "직진", ManeuverType.STRAIGHT, 0);
        List<GeoPoint> polyline = new ArrayList<>(Collections.singletonList(point));
        List<Waypoint> waypoints = new ArrayList<>(Collections.singletonList(waypoint));
        List<String> warnings = new ArrayList<>(Collections.singletonList("보행로 주의"));

        OutdoorRoute route = new OutdoorRoute(polyline, waypoints, 10.0, 8L, warnings);
        polyline.clear();
        waypoints.clear();
        warnings.clear();

        assertEquals(1, route.getPolylinePoints().size());
        assertEquals(1, route.getWaypoints().size());
        assertEquals(1, route.getWarnings().size());
        assertThrows(UnsupportedOperationException.class,
                () -> route.getPolylinePoints().add(new GeoPoint(0.0, 0.0)));
        assertThrows(UnsupportedOperationException.class, () -> route.getWaypoints().clear());
        assertThrows(UnsupportedOperationException.class, () -> route.getWarnings().clear());
    }

    @Test
    public void invalidMeasurementsAndRequiredValuesAreRejected() {
        GeoPoint point = new GeoPoint(0.0, 0.0);

        assertThrows(IllegalArgumentException.class,
                () -> new Destination(" ", "name", point, DestinationSource.PLACES, "place"));
        assertThrows(NullPointerException.class,
                () -> new Destination("id", "name", point, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Waypoint("id", point, null, ManeuverType.UNKNOWN, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new LocationSample(point, -1.0, null, 0.0, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new LocationSample(point, 1.0, 360.0, 0.0, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new LocationSample(point, 1.0, null, -0.1, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new HeadingSample(-0.1, SignalConfidence.HIGH, 1L));
        assertThrows(IllegalArgumentException.class, () -> new RouteSummary(-1.0, 1L));
        assertThrows(IllegalArgumentException.class, () -> new RouteSummary(1.0, -1L));
    }

    @Test
    public void aggregateModelsUseValueEquality() {
        GeoPoint point = new GeoPoint(37.496, 126.957);
        Waypoint waypoint = new Waypoint("waypoint-1", point, null, ManeuverType.ARRIVE, 0);
        OutdoorRoute first = new OutdoorRoute(
                Collections.singletonList(point),
                Collections.singletonList(waypoint),
                12.5,
                10L,
                Collections.emptyList());
        OutdoorRoute second = new OutdoorRoute(
                Collections.singletonList(new GeoPoint(37.496, 126.957)),
                Collections.singletonList(new Waypoint(
                        "waypoint-1", point, null, ManeuverType.ARRIVE, 0)),
                12.5,
                10L,
                Collections.emptyList());

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(new RouteSummary(12.5, 10L), new RouteSummary(12.5, 10L));
    }
}
