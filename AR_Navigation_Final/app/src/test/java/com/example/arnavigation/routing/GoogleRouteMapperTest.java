package com.example.arnavigation.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.DestinationSource;
import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.ManeuverType;
import com.example.arnavigation.model.OutdoorRoute;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class GoogleRouteMapperTest {
    private final Destination destination = new Destination(
            "destination", "Destination", new GeoPoint(43.252, -126.453),
            DestinationSource.MAP_TAP, null);
    private final GoogleRouteMapper mapper = new GoogleRouteMapper(
            new Gson(), new PolylineDecoder(), new WaypointBuilder(3.0));

    @Test
    public void mapsFixtureWithDistanceDurationPolylineAndOrderedSteps() throws Exception {
        OutdoorRoute route = mapper.map(fixture("routes_success.json"), destination);

        assertEquals(123.0, route.getDistanceMeters(), 0.0);
        assertEquals(96L, route.getDurationSeconds());
        assertEquals(3, route.getPolylinePoints().size());
        assertEquals(3, route.getWaypoints().size());
        assertEquals(ManeuverType.TURN_LEFT, route.getWaypoints().get(0).getManeuver());
        assertEquals("좌회전 & 직진", route.getWaypoints().get(0).getInstruction());
        assertEquals(ManeuverType.ARRIVE, route.getWaypoints().get(2).getManeuver());
    }

    @Test
    public void emptyRoutesAreNoRoute() {
        RouteMappingException error = assertThrows(
                RouteMappingException.class,
                () -> mapper.map("{\"routes\":[]}", destination));

        assertEquals(RouteError.NO_ROUTE, error.getError());
    }

    @Test
    public void emptyLegsStillProduceDestinationWaypoint() throws Exception {
        String json = "{\"routes\":[{\"distanceMeters\":1,\"duration\":\"1s\","
                + "\"polyline\":{\"encodedPolyline\":\"??\"},\"legs\":[]}]}";

        OutdoorRoute route = mapper.map(json, destination);

        assertEquals(1, route.getWaypoints().size());
        assertEquals(ManeuverType.ARRIVE, route.getWaypoints().get(0).getManeuver());
    }

    @Test
    public void missingStepCoordinateAndInvalidDurationAreMalformed() {
        String missingCoordinate = "{\"routes\":[{\"distanceMeters\":1,\"duration\":\"1s\","
                + "\"polyline\":{\"encodedPolyline\":\"??\"},"
                + "\"legs\":[{\"steps\":[{}]}]}]}";
        String invalidDuration = "{\"routes\":[{\"distanceMeters\":1,\"duration\":\"later\","
                + "\"polyline\":{\"encodedPolyline\":\"??\"}}]}";

        assertEquals(RouteError.MALFORMED_RESPONSE,
                assertThrows(RouteMappingException.class,
                        () -> mapper.map(missingCoordinate, destination)).getError());
        assertEquals(RouteError.MALFORMED_RESPONSE,
                assertThrows(RouteMappingException.class,
                        () -> mapper.map(invalidDuration, destination)).getError());
    }

    private static String fixture(String name) throws IOException {
        try (InputStream input = GoogleRouteMapperTest.class.getResourceAsStream(
                "/fixtures/" + name)) {
            if (input == null) throw new IOException("missing fixture " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
