package com.example.arnavigation.routing;

import static org.junit.Assert.assertEquals;

import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.ManeuverType;
import com.example.arnavigation.model.Waypoint;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class WaypointBuilderTest {
    @Test
    public void mergesNearbyStraightPointsButPreservesTurnsAndDestination() {
        GeoPoint first = new GeoPoint(37.0, 127.0);
        GeoPoint nearbyStraight = new GeoPoint(37.000005, 127.0);
        GeoPoint nearbyTurn = new GeoPoint(37.000010, 127.0);
        GeoPoint destination = new GeoPoint(37.001, 127.001);
        List<WaypointBuilder.StepWaypoint> steps = Arrays.asList(
                new WaypointBuilder.StepWaypoint(first, "직진", ManeuverType.STRAIGHT),
                new WaypointBuilder.StepWaypoint(
                        nearbyStraight, "계속 직진", ManeuverType.STRAIGHT),
                new WaypointBuilder.StepWaypoint(nearbyTurn, "좌회전", ManeuverType.TURN_LEFT));

        List<Waypoint> result = new WaypointBuilder(3.0).build(steps, destination);

        assertEquals(3, result.size());
        assertEquals(nearbyStraight, result.get(0).getPoint());
        assertEquals(ManeuverType.TURN_LEFT, result.get(1).getManeuver());
        assertEquals(ManeuverType.ARRIVE, result.get(2).getManeuver());
        assertEquals(destination, result.get(2).getPoint());
        assertEquals(2, result.get(2).getRouteIndex());
    }
}
