package com.example.arnavigation.ui.ar;

import static org.junit.Assert.*;
import com.example.arnavigation.model.*;
import java.util.*;
import org.junit.Test;

public final class OutdoorRouteRibbonTest {
    private static final GeoPoint ORIGIN = new GeoPoint(37.49, 126.95);
    private static Waypoint point(double east, double north, int index) {
        return new Waypoint("p"+index, new GeoPoint(37.49 + Math.toDegrees(north/6371000),
                126.95 + Math.toDegrees(east/(6371000*Math.cos(Math.toRadians(37.49))))),
                null, ManeuverType.UNKNOWN, index);
    }
    @Test public void forwardRouteHasRibbonAndRepeatedArrowsAndPerspective() {
        List<OutdoorRouteRibbon.Polygon> shapes = OutdoorRouteRibbon.build(ORIGIN, 0,
                Arrays.asList(point(0, 20, 0)), 0);
        assertTrue(shapes.stream().anyMatch(p -> p.color == OutdoorRouteRibbon.ACTIVE));
        assertTrue(shapes.stream().filter(p -> p.color == OutdoorRouteRibbon.ARROW).count() > 10);
        OutdoorRouteRibbon.Polygon ribbon = shapes.stream()
                .filter(p -> p.color == OutdoorRouteRibbon.ACTIVE).findFirst().get();
        float maxWidth = 0, minWidth = 1;
        for (float x : ribbon.xy) assertTrue(Float.isFinite(x));
        for (int i=0; i<ribbon.xy.length; i+=2) {
            float width = Math.abs(ribbon.xy[i]-.5f);
            maxWidth = Math.max(maxWidth,width); minWidth = Math.min(minWidth,width);
        }
        assertTrue("Near ribbon is wider than distant ribbon", maxWidth > minWidth * 5);
    }
    @Test public void bendIsPreservedAndHeadingTurnsRouteToCameraSpace() {
        List<Waypoint> route = Arrays.asList(point(0, 8, 0), point(8, 8, 1));
        List<OutdoorRouteRibbon.Polygon> shapes = OutdoorRouteRibbon.build(ORIGIN, 0, route, 0);
        assertTrue(shapes.stream().filter(p -> p.color == OutdoorRouteRibbon.FUTURE)
                .anyMatch(p -> { for(int i=0;i<p.xy.length;i+=2) if(p.xy[i]>.8f)return true; return false;}));
        assertTrue("A route behind the camera is clipped", OutdoorRouteRibbon.build(
                ORIGIN, 180, Collections.singletonList(point(0,20,0)),0).isEmpty());
        assertFalse("East-facing camera sees eastward route", OutdoorRouteRibbon.build(
                ORIGIN, 90, Collections.singletonList(point(20,0,0)),0).isEmpty());
    }
    @Test public void nearCrossingsAreFiniteAndLongRoutesHaveBoundedWork() {
        List<OutdoorRouteRibbon.Polygon> shapes = OutdoorRouteRibbon.build(ORIGIN, 0,
                Arrays.asList(point(0,-5,0),point(0,10000,1)),0);
        assertFalse(shapes.isEmpty());
        assertTrue(shapes.size()<150);
        for (OutdoorRouteRibbon.Polygon shape : shapes) {
            for (float coordinate : shape.xy) assertTrue(Float.isFinite(coordinate));
        }
        assertTrue(OutdoorRouteRibbon.build(ORIGIN,0,Collections.emptyList(),0).isEmpty());
        assertTrue(OutdoorRouteRibbon.build(ORIGIN,0,Collections.singletonList(point(0,10,0)),1).isEmpty());
    }
    @Test public void stopsAlongPathBeforeDistantRouteReturnsToTheScreen() {
        List<Waypoint> route = Arrays.asList(point(0,50,0),point(0,5,1),point(10,5,2));
        List<OutdoorRouteRibbon.Polygon> shapes = OutdoorRouteRibbon.build(ORIGIN,0,route,0);
        assertFalse(shapes.isEmpty());
        assertFalse("Later returning segment must not render",shapes.stream()
                .anyMatch(p -> p.color == OutdoorRouteRibbon.FUTURE));
        for (OutdoorRouteRibbon.Polygon polygon : shapes) {
            for (int i=1;i<polygon.xy.length;i+=2)
                assertTrue("Only local depth is rendered",polygon.xy[i] >= .5074f);
        }
    }

    @Test public void lookAheadBudgetIncludesBendsAndStartsAtActiveWaypoint() {
        List<Waypoint> route = Arrays.asList(point(0,8,0),point(8,8,1),point(0,3,2));
        List<OutdoorRouteRibbon.Polygon> shapes = OutdoorRouteRibbon.build(ORIGIN,0,route,0);
        assertTrue(shapes.stream().anyMatch(p -> p.color == OutdoorRouteRibbon.FUTURE));
        // 8m forward + only 4m right: no more distant or returning geometry is included.
        for (OutdoorRouteRibbon.Polygon polygon : shapes) {
            for (int i=0;i<polygon.xy.length;i+=2)
                assertTrue("Right bend stops at 12m total",polygon.xy[i] < .89f);
        }
        List<OutdoorRouteRibbon.Polygon> advanced = OutdoorRouteRibbon.build(ORIGIN,0,route,2);
        assertFalse(advanced.isEmpty());
        assertFalse(advanced.stream().anyMatch(p -> p.color == OutdoorRouteRibbon.FUTURE));
    }

}
