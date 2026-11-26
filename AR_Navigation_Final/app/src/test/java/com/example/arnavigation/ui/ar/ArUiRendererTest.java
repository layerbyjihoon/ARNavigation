package com.example.arnavigation.ui.ar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.arnavigation.camera.CameraStatus;
import com.example.arnavigation.heading.HeadingStatus;
import com.example.arnavigation.location.LocationStatus;
import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.DestinationSource;
import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.ManeuverType;
import com.example.arnavigation.model.OutdoorRoute;
import com.example.arnavigation.model.Waypoint;
import com.example.arnavigation.navigation.NavigationStage;
import com.example.arnavigation.navigation.NavigationUiState;
import com.example.arnavigation.routing.RouteError;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

public final class ArUiRendererTest {
    private FakeTarget target;
    private ArUiRenderer renderer;

    @Before
    public void setUp() {
        target = new FakeTarget();
        renderer = new ArUiRenderer(target, new NavigationInstructionFormatter(15.0d));
        renderer.setCameraStatus(CameraStatus.BOUND);
    }

    @Test
    public void validNavigationShowsComputedArrowAndDistances() {
        renderer.render(state(
                NavigationStage.NAVIGATING,
                LocationStatus.AVAILABLE,
                HeadingStatus.AVAILABLE,
                32.0d,
                true,
                null));

        assertTrue(target.arrowVisible);
        assertEquals(32.0d, target.angle, 0.0d);
        assertEquals("20m 앞에서 우회전하세요", target.instruction);
        assertEquals("다음 안내 약 18 m · 전체 약 240 m", target.distance);
        assertEquals("AR 안내 중", target.status);
    }

    @Test
    public void lowGpsHidesArrowAndEmphasizesMap() {
        renderer.render(state(
                NavigationStage.NAVIGATING,
                LocationStatus.LOW_ACCURACY,
                HeadingStatus.AVAILABLE,
                10.0d,
                false,
                null));

        assertFalse(target.arrowVisible);
        assertTrue(target.mapEmphasized);
        assertEquals("GPS 정확도가 낮습니다", target.status);
    }

    @Test
    public void lowHeadingHidesArrowButKeepsApiInstruction() {
        renderer.render(state(
                NavigationStage.NAVIGATING,
                LocationStatus.AVAILABLE,
                HeadingStatus.LOW_ACCURACY,
                10.0d,
                false,
                null));

        assertFalse(target.arrowVisible);
        assertEquals("방향 확인 중", target.status);
        assertEquals("20m 앞에서 우회전하세요", target.instruction);
    }

    @Test
    public void noDestinationUsesMapCtaAndHidesEnd() {
        renderer.render(new NavigationUiState(
                NavigationStage.NO_DESTINATION,
                null, null, null, 0,
                null, LocationStatus.UNAVAILABLE,
                null, HeadingStatus.UNAVAILABLE,
                null, null, false, false, null, false, false));

        assertFalse(target.arrowVisible);
        assertTrue(target.mapEmphasized);
        assertFalse(target.endVisible);
        assertEquals("지도에서 목적지를 먼저 선택하세요", target.status);
    }

    @Test
    public void routeErrorUsesMapCtaAndHidesArrow() {
        NavigationUiState state = state(
                NavigationStage.DESTINATION_SELECTED,
                LocationStatus.AVAILABLE,
                HeadingStatus.AVAILABLE,
                null,
                false,
                RouteError.NETWORK);
        renderer.render(new NavigationUiState(
                state.getStage(), state.getDestination(), null, null, 0,
                null, state.getLocationStatus(), null, state.getHeadingStatus(),
                null, null, false, false, state.getRouteError(), false, false));

        assertFalse(target.arrowVisible);
        assertTrue(target.mapEmphasized);
        assertEquals("경로를 불러오지 못했습니다: NETWORK", target.status);
    }

    @Test
    public void arrivedRemovesArrow() {
        renderer.render(state(
                NavigationStage.ARRIVED,
                LocationStatus.AVAILABLE,
                HeadingStatus.AVAILABLE,
                0.0d,
                false,
                null));

        assertFalse(target.arrowVisible);
        assertEquals("목적지에 도착했습니다", target.status);
    }

    @Test
    public void unavailableCameraKeepsMapAndEndActionsUsable() {
        renderer.setCameraStatus(CameraStatus.PERMISSION_DENIED);
        renderer.render(state(
                NavigationStage.NAVIGATING,
                LocationStatus.AVAILABLE,
                HeadingStatus.AVAILABLE,
                0.0d,
                false,
                null));

        assertFalse(target.arrowVisible);
        assertTrue(target.mapEmphasized);
        assertTrue(target.endVisible);
        assertEquals("카메라 권한이 필요합니다", target.status);
    }

    private static NavigationUiState state(
            NavigationStage stage,
            LocationStatus locationStatus,
            HeadingStatus headingStatus,
            Double angle,
            boolean arrowVisible,
            RouteError routeError) {
        Destination destination = new Destination(
                "dest", "형남공학관", new GeoPoint(37.496, 126.957),
                DestinationSource.MAP_TAP, null);
        Waypoint waypoint = new Waypoint(
                "waypoint", new GeoPoint(37.495, 126.956),
                "우회전", ManeuverType.TURN_RIGHT, 1);
        OutdoorRoute route = new OutdoorRoute(
                Arrays.asList(new GeoPoint(37.494, 126.955), destination.getPoint()),
                Collections.singletonList(waypoint),
                240.0d,
                180L,
                Collections.emptyList());
        return new NavigationUiState(
                stage, destination, route, waypoint, 0,
                null, locationStatus, null, headingStatus,
                18.0d, angle, false, false, routeError, true, arrowVisible);
    }

    private static final class FakeTarget implements ArRenderTarget {
        String status;
        String instruction;
        String distance;
        boolean arrowVisible;
        double angle;
        boolean mapEmphasized;
        boolean endVisible;

        @Override public void setDestination(String text) {}
        @Override public void setStatus(String text) { status = text; }
        @Override public void setInstruction(String text) { instruction = text; }
        @Override public void setDistance(String text) { distance = text; }
        @Override public void setArrow(boolean visible, double angleDegrees, String description) {
            arrowVisible = visible;
            angle = angleDegrees;
        }
        @Override public void setMapActionEmphasized(boolean emphasized) {
            mapEmphasized = emphasized;
        }
        @Override public void setEndActionVisible(boolean visible) { endVisible = visible; }
    }
}
