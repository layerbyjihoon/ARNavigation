package com.example.arnavigation.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.LocationSample;
import com.example.arnavigation.model.ManeuverType;
import com.example.arnavigation.model.OutdoorRoute;
import com.example.arnavigation.model.Waypoint;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public final class RouteProgressTrackerTest {
    private final NavigationConfig config = new NavigationConfig(8.0, 20.0, 5_000L, 20.0, 2, 10_000L);

    @Test
    public void advancesWhenWaypointIsReachedOrPassedWithoutDuplicateAdvance() {
        RouteProgressTracker tracker = new RouteProgressTracker(config, new DirectionCalculator());
        OutdoorRoute route = route();

        RouteProgress before = tracker.update(sample(0.0, 0.0, 1L), route);
        RouteProgress passed = tracker.update(sample(0.0, 0.00035, 2L), route);
        RouteProgress duplicate = tracker.update(sample(0.0, 0.00035, 3L), route);

        assertEquals(0, before.getActiveWaypointIndex());
        assertEquals(1, passed.getActiveWaypointIndex());
        assertEquals(1, duplicate.getActiveWaypointIndex());
        assertFalse(duplicate.isRouteCompleted());
    }

    @Test
    public void confirmsOffRouteOnlyAfterContinuousThresholdAndResetsOnReturn() {
        RouteProgressTracker tracker = new RouteProgressTracker(config, new DirectionCalculator());
        OutdoorRoute route = route();
        long second = TimeUnit.SECONDS.toNanos(1L);

        assertFalse(tracker.update(sample(0.001, 0.0001, 0L), route).isOffRoute());
        assertFalse(tracker.update(sample(0.001, 0.0001, 4L * second), route).isOffRoute());
        assertFalse(tracker.update(sample(0.0, 0.0001, 5L * second), route).isOffRoute());
        assertFalse(tracker.update(sample(0.001, 0.0001, 10L * second), route).isOffRoute());
        assertTrue(tracker.update(sample(0.001, 0.0001, 15L * second), route).isOffRoute());
    }

    @Test public void advancesPastWaypointWithGpsOffsetInsideRouteCorridor() {
        RouteProgressTracker tracker = new RouteProgressTracker(config,new DirectionCalculator());
        OutdoorRoute path=route();
        tracker.update(sample(.00012,.0001,1),path);
        RouteProgress progress=tracker.update(sample(.00012,.00045,2),path);
        assertFalse(progress.isOffRoute()); assertEquals(1,progress.getActiveWaypointIndex());
    }

    @Test public void advancesWhenSparseGpsHasAlreadyRoundedCorner() {
        GeoPoint start=new GeoPoint(0,0),turn=new GeoPoint(0,.0003),end=new GeoPoint(.0004,.0003);
        OutdoorRoute route=new OutdoorRoute(Arrays.asList(start,turn,end),Arrays.asList(
            new Waypoint("turn",turn,null,ManeuverType.TURN_LEFT,0),
            new Waypoint("end",end,null,ManeuverType.ARRIVE,1)),80,60,Collections.emptyList());
        RouteProgressTracker tracker=new RouteProgressTracker(config,new DirectionCalculator());
        tracker.update(sample(0,.00015,1),route);
        assertEquals(1,tracker.update(sample(.0002,.0003,2),route).getActiveWaypointIndex());
    }

    @Test public void firstFixAfterWaypointDoesNotGuideBackToIt() {
        RouteProgressTracker tracker=new RouteProgressTracker(config,new DirectionCalculator());
        assertEquals(1,tracker.update(sample(0,.00045,1),route()).getActiveWaypointIndex());
    }

    @Test public void doesNotJumpToNearbyReturnLegWhileMovingOnOutboundLeg() {
        GeoPoint start=new GeoPoint(0,0),turn=new GeoPoint(0,.001),bend=new GeoPoint(.00009,.001),end=new GeoPoint(.00009,0);
        OutdoorRoute route=new OutdoorRoute(Arrays.asList(start,turn,bend,end),Arrays.asList(
            new Waypoint("turn",turn,null,ManeuverType.TURN_LEFT,0),
            new Waypoint("end",end,null,ManeuverType.ARRIVE,1)),240,180,Collections.emptyList());
        RouteProgressTracker tracker=new RouteProgressTracker(config,new DirectionCalculator());
        tracker.update(sample(0,.0005,1),route);
        assertEquals(0,tracker.update(sample(.00005,.00055,2),route).getActiveWaypointIndex());
        assertEquals(0,tracker.update(sample(.00011,.0006,3),route).getActiveWaypointIndex());
    }

    @Test public void offRouteFixCannotSkipWaypointAndPassingFinalProjectionCannotFinish() {
        RouteProgressTracker tracker=new RouteProgressTracker(config,new DirectionCalculator());
        OutdoorRoute path=route();
        assertEquals(0,tracker.update(sample(.001,.00045,1),path).getActiveWaypointIndex());
        tracker.reset();
        tracker.update(sample(.00012,.00045,1),path);
        RouteProgress beyond=tracker.update(sample(.00012,.0008,2),path);
        assertEquals(1,beyond.getActiveWaypointIndex()); assertFalse(beyond.isRouteCompleted());
    }

    @Test public void sparseFixCanPassMultipleIntermediateInstructions() {
        GeoPoint a=new GeoPoint(0,0),b=new GeoPoint(0,.0003),c=new GeoPoint(0,.0006),d=new GeoPoint(0,.001);
        OutdoorRoute path=new OutdoorRoute(Arrays.asList(a,b,c,d),Arrays.asList(
            new Waypoint("b",b,null,ManeuverType.STRAIGHT,0),
            new Waypoint("c",c,null,ManeuverType.STRAIGHT,1),
            new Waypoint("d",d,null,ManeuverType.ARRIVE,2)),111,90,Collections.emptyList());
        RouteProgressTracker tracker=new RouteProgressTracker(config,new DirectionCalculator());
        tracker.update(sample(.00012,.0001,1),path);
        assertEquals(2,tracker.update(sample(.00012,.0008,2),path).getActiveWaypointIndex());
    }

    @Test public void overlappingFutureCrossingCannotSkipUnwalkedLoop() {
        GeoPoint a=new GeoPoint(0,0),b=new GeoPoint(0,.001),c=new GeoPoint(.001,.001),d=new GeoPoint(.001,0),e=new GeoPoint(-.001,0);
        OutdoorRoute path=new OutdoorRoute(Arrays.asList(a,b,c,d,e),Arrays.asList(
            new Waypoint("b",b,null,ManeuverType.TURN_LEFT,0),
            new Waypoint("e",e,null,ManeuverType.ARRIVE,1)),555,420,Collections.emptyList());
        RouteProgressTracker tracker=new RouteProgressTracker(config,new DirectionCalculator());
        assertEquals(0,tracker.update(sample(0,.00002,1),path).getActiveWaypointIndex());
    }

    @Test public void defaultCorridorAcceptsThirtyMeterOffsetAndAdvancesPassedWaypoint() {
        RouteProgressTracker tracker = new RouteProgressTracker(NavigationConfig.defaults(), new DirectionCalculator());
        OutdoorRoute path = route();
        long second = TimeUnit.SECONDS.toNanos(1);
        tracker.update(sample(.00027, .0001, 0), path);
        RouteProgress progress = tracker.update(sample(.00027, .0006, 6 * second), path);
        assertFalse("30m GPS offset must remain on route", progress.isOffRoute());
        assertEquals("passed instruction must not point backward", 1, progress.getActiveWaypointIndex());
        assertFalse("wider corridor must not complete the destination", progress.isRouteCompleted());
    }

    @Test public void defaultCorridorStillDetectsPersistentFortyMeterDeviationAndResetsOnReturn() {
        RouteProgressTracker tracker = new RouteProgressTracker(NavigationConfig.defaults(), new DirectionCalculator());
        OutdoorRoute path = route();
        long second = TimeUnit.SECONDS.toNanos(1);
        assertFalse(tracker.update(sample(.00036, .0001, 0), path).isOffRoute());
        assertFalse(tracker.update(sample(.00036, .0001, second), path).isOffRoute());
        assertTrue(tracker.update(sample(.00036, .0001, 2 * second), path).isOffRoute());
        assertFalse(tracker.update(sample(.00027, .0001, 3 * second), path).isOffRoute());
        assertFalse(tracker.update(sample(.00036, .0001, 4 * second), path).isOffRoute());
        assertFalse(tracker.update(sample(.00036, .0001, 5 * second), path).isOffRoute());
        assertTrue(tracker.update(sample(.00036, .0001, 6 * second), path).isOffRoute());
    }

    private static OutdoorRoute route() {
        GeoPoint start = new GeoPoint(0.0, 0.0);
        GeoPoint first = new GeoPoint(0.0, 0.0003);
        GeoPoint second = new GeoPoint(0.0, 0.0006);
        return new OutdoorRoute(
                Arrays.asList(start, first, second),
                Arrays.asList(
                        new Waypoint("w0", first, null, ManeuverType.STRAIGHT, 0),
                        new Waypoint("w1", second, null, ManeuverType.ARRIVE, 1)),
                66.0,
                60L,
                Collections.emptyList());
    }

    private static LocationSample sample(double latitude, double longitude, long timeNanos) {
        return new LocationSample(
                new GeoPoint(latitude, longitude), 5.0, null, 0.0, timeNanos);
    }
}
