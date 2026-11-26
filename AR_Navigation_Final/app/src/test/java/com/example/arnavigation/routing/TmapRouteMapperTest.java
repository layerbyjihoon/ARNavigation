package com.example.arnavigation.routing;

import static org.junit.Assert.*;
import com.example.arnavigation.model.*;
import com.google.gson.*;
import org.junit.Test;

public final class TmapRouteMapperTest {
    private static final Destination DESTINATION = new Destination("entry", "문화관",
            new GeoPoint(37.491, 126.952), DestinationSource.MAP_TAP, null);

    @Test public void preservesBendsAndCrosswalkAndUsesSeconds() throws Exception {
        OutdoorRoute route = new TmapRouteMapper().map(fixture(), DESTINATION);
        assertEquals(120, route.getDistanceMeters(), 0);
        assertEquals(100, route.getDurationSeconds());
        assertEquals(4, route.getPolylinePoints().size());
        assertEquals(new GeoPoint(37.49, 126.951), route.getWaypoints().get(1).getPoint());
        assertEquals(ManeuverType.TURN_LEFT, route.getWaypoints().get(2).getManeuver());
        assertEquals("좌측 횡단보도", route.getWaypoints().get(2).getInstruction());
        assertEquals(4, route.getWaypoints().size());
        assertEquals(ManeuverType.ARRIVE, route.getWaypoints().get(3).getManeuver());
    }

    @Test public void sortsFeatureIndexesAndFallsBackToLineMetrics() throws Exception {
        JsonObject response = JsonParser.parseString(fixture()).getAsJsonObject();
        JsonArray original = response.getAsJsonArray("features");
        JsonObject start = original.get(0).getAsJsonObject().getAsJsonObject("properties");
        start.remove("totalDistance"); start.remove("totalTime");
        JsonArray reversed = new JsonArray();
        for (int i = original.size()-1; i >= 0; i--) reversed.add(original.get(i));
        response.add("features", reversed);
        OutdoorRoute route = new TmapRouteMapper().map(response.toString(), DESTINATION);
        assertEquals(120, route.getDistanceMeters(), 0);
        assertEquals(100, route.getDurationSeconds());
        assertEquals(new GeoPoint(37.49, 126.95), route.getPolylinePoints().get(0));
    }

    @Test public void emptyRouteIsDistinctFromInvalidGeometryAndErrorEnvelope() throws Exception {
        assertError("{\"type\":\"FeatureCollection\",\"features\":[]}", RouteError.NO_ROUTE);
        assertError("{\"error\":{\"code\":\"AUTH\"}}", RouteError.MALFORMED_RESPONSE);
        assertError("not json", RouteError.MALFORMED_RESPONSE);
        assertError(fixture().replace("126.951", "999"), RouteError.MALFORMED_RESPONSE);
        assertError(fixture().replace("120", "-1"), RouteError.MALFORMED_RESPONSE);
        assertError(fixture().replace("100", "null"), RouteError.MALFORMED_RESPONSE);
    }

    private static String fixture() throws Exception {
        return TmapRouteProviderTest.fixture("tmap_success.json");
    }
    private static void assertError(String json, RouteError expected) throws Exception {
        try { new TmapRouteMapper().map(json, DESTINATION); fail("Expected mapping error"); }
        catch (RouteMappingException error) { assertEquals(expected, error.getError()); }
    }
}
