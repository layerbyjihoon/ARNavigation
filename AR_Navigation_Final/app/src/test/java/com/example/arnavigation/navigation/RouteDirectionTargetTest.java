package com.example.arnavigation.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.example.arnavigation.model.*;
import java.util.*;
import org.junit.Test;

public final class RouteDirectionTargetTest {
    private final DirectionCalculator distance = new DirectionCalculator();

    @Test public void farDestinationUsesTenMeterPointAlongPathInsteadOfDirectBearing() {
        GeoPoint start = new GeoPoint(0,0), bend = new GeoPoint(0,.001), end = new GeoPoint(.001,.001);
        OutdoorRoute route = route(Arrays.asList(start,bend,end),end);
        GeoPoint target = RouteDirectionTarget.select(route,start,0);
        assertEquals(10, distance.distanceMeters(start,target), .01);
        assertEquals(90, distance.bearingDegrees(start,target), .01);
        assertTrue(distance.distanceMeters(target,end) > 100);
    }

    @Test public void nearbyInstructionCapsLookAheadBeforeTurn() {
        GeoPoint start = new GeoPoint(0,0), bend = new GeoPoint(0,.001), end = new GeoPoint(.001,.001);
        OutdoorRoute route = new OutdoorRoute(Arrays.asList(start,bend,end),Arrays.asList(
                new Waypoint("turn",bend,null,ManeuverType.TURN_LEFT,0),
                new Waypoint("end",end,null,ManeuverType.ARRIVE,1)),220,180,Collections.emptyList());
        GeoPoint target = RouteDirectionTarget.select(route,new GeoPoint(0,.00096),0);
        assertEquals(0,distance.distanceMeters(bend,target),.001);
        GeoPoint after = RouteDirectionTarget.select(route,new GeoPoint(.0002,.001),1);
        assertEquals(10,distance.distanceMeters(new GeoPoint(.0002,.001),after),.01);
        assertEquals(0,distance.bearingDegrees(new GeoPoint(.0002,.001),after),.01);
    }

    @Test public void laterLoopNearUserDoesNotReplaceUnpassedLeg() {
        GeoPoint start = new GeoPoint(0,0), bend = new GeoPoint(0,.001),
                corner = new GeoPoint(.0001,.001), end = new GeoPoint(.0001,0);
        OutdoorRoute route = new OutdoorRoute(Arrays.asList(start,bend,corner,end),Arrays.asList(
                new Waypoint("turn",bend,null,ManeuverType.TURN_LEFT,0),
                new Waypoint("end",end,null,ManeuverType.ARRIVE,1)),230,180,Collections.emptyList());
        GeoPoint target = RouteDirectionTarget.select(route,new GeoPoint(.00009,.0005),0);
        assertEquals(0,target.getLatitude(),1e-10);
        assertTrue(target.getLongitude() > .0005);
    }

    @Test public void duplicateVerticesAndShortDestinationStayFiniteAndDoNotOvershoot() {
        GeoPoint start = new GeoPoint(0,0), end = new GeoPoint(0,.00004);
        GeoPoint target = RouteDirectionTarget.select(route(Arrays.asList(start,start,end),end),start,0);
        assertEquals(0,distance.distanceMeters(end,target),.001);
    }

    @Test public void outsideCorridorStillPointsBackTowardRouteInsteadOfPretendingOnRoute() {
        GeoPoint start=new GeoPoint(0,0),end=new GeoPoint(0,.001),current=new GeoPoint(.0004,.0003);
        OutdoorRoute route=route(Arrays.asList(start,end),end);
        double bearing=RouteDirectionTarget.bearing(route,current,0,35);
        assertEquals(distance.bearingDegrees(current,RouteDirectionTarget.select(route,current,0)),bearing,.001);
        assertTrue(bearing>150 && bearing<180);
    }

    @Test public void projectionAtInstructionBoundaryKeepsIncomingDirectionInsteadOfNorth() {
        GeoPoint start=new GeoPoint(0,0),end=new GeoPoint(0,.001);
        OutdoorRoute route=route(Arrays.asList(start,end),end);
        assertEquals(90,RouteDirectionTarget.bearing(route,new GeoPoint(.00018,.001),0,35),.01);
        assertEquals(90,RouteDirectionTarget.bearing(route,end,0,35),.01);
    }

    private static OutdoorRoute route(List<GeoPoint> path,GeoPoint end) {
        return new OutdoorRoute(path,Collections.singletonList(
                new Waypoint("end",end,null,ManeuverType.ARRIVE,0)),220,180,Collections.emptyList());
    }
}
