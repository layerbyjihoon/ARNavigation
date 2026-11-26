package com.example.arnavigation.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import com.example.arnavigation.heading.HeadingListener;
import com.example.arnavigation.heading.HeadingProvider;
import com.example.arnavigation.heading.HeadingResult;
import com.example.arnavigation.heading.HeadingStatus;
import com.example.arnavigation.location.LocationListener;
import com.example.arnavigation.location.LocationProvider;
import com.example.arnavigation.location.LocationResult;
import com.example.arnavigation.location.LocationResultCallback;
import com.example.arnavigation.location.LocationStatus;
import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.DestinationSource;
import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.HeadingSample;
import com.example.arnavigation.model.LocationSample;
import com.example.arnavigation.model.ManeuverType;
import com.example.arnavigation.model.OutdoorRoute;
import com.example.arnavigation.model.SignalConfidence;
import com.example.arnavigation.model.Waypoint;
import com.example.arnavigation.routing.Cancelable;
import com.example.arnavigation.routing.RouteCallback;
import com.example.arnavigation.routing.RouteError;
import com.example.arnavigation.routing.RouteProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;

public final class NavigationViewModelTest {
    @Rule public final InstantTaskExecutorRule instantTaskExecutorRule =
            new InstantTaskExecutorRule();

    @Test
    public void cameraObserverFeedbackSettlesForDeniedBindingAndBoundStates() {
        Fixture fixture = readyAndStarted();
        int[] emissions = {0};
        boolean[] previewAvailable = {false};
        androidx.lifecycle.Observer<NavigationUiState> cameraObserver = state -> {
            // syncCamera reports false when permission is denied or binding, and true when bound.
            // LiveData drains reentrant setValue calls until no observer publishes another value.
            assertTrue("Camera feedback must settle instead of starving the UI thread", ++emissions[0] < 5);
            fixture.viewModel.dispatch(new NavigationAction.SetCameraAvailable(previewAvailable[0]));
        };
        try {
            fixture.viewModel.getState().observeForever(cameraObserver);
            assertEquals(1, emissions[0]);
            previewAvailable[0] = true;
            fixture.viewModel.dispatch(new NavigationAction.SetCameraAvailable(true));
            assertEquals(2, emissions[0]);
            previewAvailable[0] = false;
            fixture.viewModel.dispatch(new NavigationAction.SetCameraAvailable(false));
            assertEquals(3, emissions[0]);
        } finally {
            fixture.viewModel.getState().removeObserver(cameraObserver);
        }
    }

    @Test
    public void followsCompleteStateFlowAndHidesArrowOnArrival() {
        Fixture fixture = new Fixture();
        assertEquals(NavigationStage.NO_DESTINATION, fixture.state().getStage());

        fixture.viewModel.dispatch(new NavigationAction.SelectDestination(fixture.destination));
        assertEquals(NavigationStage.DESTINATION_SELECTED, fixture.state().getStage());

        fixture.viewModel.dispatch(new NavigationAction.ConfirmDestination());
        assertEquals(NavigationStage.ROUTE_LOADING, fixture.state().getStage());
        fixture.location.emitSingle(available(sample(0.0, 0.0, 1L)));
        fixture.routes.succeed(0, fixture.route);
        assertEquals(NavigationStage.ROUTE_READY, fixture.state().getStage());

        fixture.viewModel.dispatch(new NavigationAction.SetCameraAvailable(true));
        fixture.viewModel.dispatch(new NavigationAction.StartNavigation());
        assertEquals(NavigationStage.NAVIGATING, fixture.state().getStage());
        fixture.heading.emit(new HeadingResult(
                HeadingStatus.AVAILABLE,
                new HeadingSample(90.0, SignalConfidence.HIGH, 1L)));
        fixture.location.emitStream(available(sample(0.0, 0.0, 2L)));
        assertTrue(fixture.state().isArrowVisible());
        assertEquals(0.0, fixture.state().getRelativeAngleDegrees(), 0.5);

        fixture.location.emitStream(available(sample(0.0, 0.0006, 3L)));
        assertEquals(NavigationStage.NAVIGATING, fixture.state().getStage());
        fixture.location.emitStream(available(sample(0.0, 0.0006, 4L)));
        assertEquals(NavigationStage.ARRIVED, fixture.state().getStage());
        assertFalse(fixture.state().isArrowVisible());
    }

    @Test public void arrowUsesNearbyRouteDirectionWhileActiveInstructionStaysFarAway() {
        Fixture fixture = new Fixture();
        GeoPoint start = new GeoPoint(0, 0), bend = new GeoPoint(0, .001), end = new GeoPoint(.001, .001);
        OutdoorRoute curved = new OutdoorRoute(Arrays.asList(start, bend, end), Collections.singletonList(
                new Waypoint("end", end, "도착", ManeuverType.ARRIVE, 0)), 222, 180, Collections.emptyList());
        fixture.viewModel.dispatch(new NavigationAction.SelectDestination(fixture.destination));
        fixture.viewModel.dispatch(new NavigationAction.ConfirmDestination());
        fixture.location.emitSingle(available(sample(0, 0, 1)));
        fixture.routes.succeed(0, curved);
        fixture.viewModel.dispatch(new NavigationAction.SetCameraAvailable(true));
        fixture.viewModel.dispatch(new NavigationAction.StartNavigation());
        fixture.heading.emit(new HeadingResult(HeadingStatus.AVAILABLE,
                new HeadingSample(90, SignalConfidence.HIGH, 1)));
        fixture.location.emitStream(available(sample(0, 0, 2)));
        assertTrue(fixture.state().isArrowVisible());
        assertEquals("Arrow follows the nearby eastbound leg", 0, fixture.state().getRelativeAngleDegrees(), .1);
        assertEquals("Instruction target is preserved", end, fixture.state().getActiveWaypoint().getPoint());
        assertTrue(fixture.state().getDistanceToWaypointMeters() > 100);
    }

    @Test public void fiftyMeterBoundarySwitchesFromRouteLookAheadToSelectedDestination() {
        for (double meters : new double[]{49.9, 50.1}) {
            Fixture fixture = new Fixture();
            GeoPoint end = fixture.destination.getPoint();
            OutdoorRoute detour = new OutdoorRoute(Arrays.asList(new GeoPoint(0,0),
                    new GeoPoint(-.001,0),end),Collections.singletonList(
                    new Waypoint("end",end,"도착",ManeuverType.ARRIVE,0)),250,180,Collections.emptyList());
            fixture.viewModel.dispatch(new NavigationAction.SelectDestination(fixture.destination));
            fixture.viewModel.dispatch(new NavigationAction.ConfirmDestination());
            fixture.location.emitSingle(available(sample(0,0,1)));
            fixture.routes.succeed(0,detour);
            fixture.viewModel.dispatch(new NavigationAction.SetCameraAvailable(true));
            fixture.viewModel.dispatch(new NavigationAction.StartNavigation());
            fixture.heading.emit(new HeadingResult(HeadingStatus.AVAILABLE,
                    new HeadingSample(90,SignalConfidence.HIGH,1)));
            double longitude = end.getLongitude() - Math.toDegrees(meters / 6371000.0);
            fixture.location.emitStream(available(sample(0,longitude,2)));
            assertTrue(fixture.state().isArrowVisible());
            assertEquals("Near goal points east; farther away follows the southbound detour",
                    meters <= 50 ? 0 : 90,fixture.state().getRelativeAngleDegrees(),.1);
            assertEquals(end,fixture.state().getActiveWaypoint().getPoint());
        }
    }

    @Test public void gpsSidewaysOffsetInsideCorridorDoesNotTurnForwardArrowSideways() {
        Fixture fixture = readyAndStarted();
        fixture.viewModel.dispatch(new NavigationAction.SetCameraAvailable(true));
        fixture.heading.emit(new HeadingResult(HeadingStatus.AVAILABLE,
                new HeadingSample(90, SignalConfidence.HIGH, 1)));
        fixture.location.emitStream(available(sample(.00027, .0001, 2)));
        assertTrue(fixture.state().isArrowVisible());
        assertEquals("30m sideways GPS drift must not steer a straight route sideways", 0,
                fixture.state().getRelativeAngleDegrees(), 1);
        fixture.location.emitStream(available(sample(-.00027, .0001, 3)));
        assertEquals(0, fixture.state().getRelativeAngleDegrees(), 1);
        fixture.heading.emit(new HeadingResult(HeadingStatus.AVAILABLE,
                new HeadingSample(180, SignalConfidence.HIGH, 4)));
        assertEquals("Camera turns still rotate the arrow", -90, fixture.state().getRelativeAngleDegrees(), 1);
    }

    @Test
    public void lowLocationOrHeadingAccuracyHidesArrow() {
        Fixture fixture = readyAndStarted();
        fixture.viewModel.dispatch(new NavigationAction.SetCameraAvailable(true));
        fixture.heading.emit(new HeadingResult(
                HeadingStatus.LOW_ACCURACY,
                new HeadingSample(90.0, SignalConfidence.LOW, 1L)));
        fixture.location.emitStream(available(sample(0.0, 0.0, 2L)));
        assertFalse(fixture.state().isArrowVisible());

        fixture.heading.emit(new HeadingResult(
                HeadingStatus.AVAILABLE,
                new HeadingSample(90.0, SignalConfidence.HIGH, 2L)));
        fixture.location.emitStream(new LocationResult(
                LocationStatus.LOW_ACCURACY, sample(0.0, 0.0, 3L)));
        assertFalse(fixture.state().isArrowVisible());
    }

    @Test
    public void destinationChangeIgnoresStaleRouteAndStopClearsSessionState() {
        Fixture fixture = new Fixture();
        fixture.viewModel.dispatch(new NavigationAction.SelectDestination(fixture.destination));
        fixture.viewModel.dispatch(new NavigationAction.ConfirmDestination());
        fixture.location.emitSingle(available(sample(0.0, 0.0, 1L)));
        Destination replacement = new Destination(
                "replacement", "Replacement", new GeoPoint(0.0, 0.001),
                DestinationSource.MAP_TAP, null);

        fixture.viewModel.dispatch(new NavigationAction.SelectDestination(replacement));
        fixture.routes.succeed(0, fixture.route);

        assertEquals(NavigationStage.DESTINATION_SELECTED, fixture.state().getStage());
        assertEquals(replacement, fixture.state().getDestination());
        assertNull(fixture.state().getRoute());

        fixture.viewModel.dispatch(new NavigationAction.StopNavigation());
        assertEquals(NavigationStage.NO_DESTINATION, fixture.state().getStage());
        assertNull(fixture.state().getDestination());
        assertNull(fixture.state().getRouteError());
        assertEquals(0, fixture.state().getActiveWaypointIndex());
    }

    @Test
    public void sustainedOffRouteUsesSingleRequestAndCooldown() {
        Fixture fixture = readyAndStarted();
        long second = TimeUnit.SECONDS.toNanos(1L);

        fixture.location.emitStream(available(sample(0.001, 0.0001, 1L * second)));
        fixture.location.emitStream(available(sample(0.001, 0.0001, 6L * second)));
        assertEquals(2, fixture.routes.callbacks.size());
        assertTrue(fixture.state().isRerouting());

        fixture.location.emitStream(available(sample(0.001, 0.0001, 7L * second)));
        assertEquals(2, fixture.routes.callbacks.size());
        fixture.routes.fail(1, RouteError.NETWORK);
        assertEquals(RouteError.NETWORK, fixture.state().getRouteError());

        fixture.location.emitStream(available(sample(0.001, 0.0001, 10L * second)));
        assertEquals(2, fixture.routes.callbacks.size());
        fixture.location.emitStream(available(sample(0.001, 0.0001, 16L * second)));
        assertEquals(3, fixture.routes.callbacks.size());
    }

    @Test
    public void backgroundPauseReleasesSourcesAndResumeStartsOneFreshStream() {
        Fixture fixture = readyAndStarted();
        assertEquals(1, fixture.location.startCount);
        assertEquals(1, fixture.heading.startCount);

        fixture.viewModel.dispatch(new NavigationAction.PauseNavigation());
        fixture.viewModel.dispatch(new NavigationAction.PauseNavigation());
        assertEquals(1, fixture.location.stopCount);
        assertEquals(1, fixture.heading.stopCount);
        assertEquals(NavigationStage.NAVIGATING, fixture.state().getStage());

        fixture.viewModel.dispatch(new NavigationAction.ResumeNavigation());
        fixture.viewModel.dispatch(new NavigationAction.ResumeNavigation());
        assertEquals(2, fixture.location.startCount);
        assertEquals(2, fixture.heading.startCount);
        assertEquals(1, fixture.location.stopCount);
    }

    @Test
    public void callbacksFromPausedStreamAreIgnoredAfterResume() {
        Fixture fixture = readyAndStarted();
        LocationListener oldLocation = fixture.location.streamListener;
        HeadingListener oldHeading = fixture.heading.listener;
        LocationSample locationBeforePause = fixture.state().getLocation();
        fixture.viewModel.dispatch(new NavigationAction.PauseNavigation());
        fixture.viewModel.dispatch(new NavigationAction.ResumeNavigation());

        oldHeading.onHeadingChanged(new HeadingResult(
                HeadingStatus.AVAILABLE,
                new HeadingSample(90.0, SignalConfidence.HIGH, 2L)));
        oldLocation.onLocationChanged(available(sample(0.0, 0.0006, 3L)));
        oldLocation.onLocationChanged(available(sample(0.0, 0.0006, 4L)));

        assertEquals(NavigationStage.NAVIGATING, fixture.state().getStage());
        assertEquals(locationBeforePause, fixture.state().getLocation());
        assertNull(fixture.state().getHeading());
    }

    @Test
    public void savedDestinationIsRestoredIntoANewViewModel() {
        SavedStateHandle savedState = new SavedStateHandle();
        Fixture first = new Fixture(savedState);
        first.viewModel.dispatch(new NavigationAction.SelectDestination(first.destination));

        Fixture recreated = new Fixture(savedState);

        assertEquals(NavigationStage.DESTINATION_SELECTED, recreated.state().getStage());
        assertEquals(first.destination, recreated.state().getDestination());
    }

    private static Fixture readyAndStarted() {
        Fixture fixture = new Fixture();
        fixture.viewModel.dispatch(new NavigationAction.SelectDestination(fixture.destination));
        fixture.viewModel.dispatch(new NavigationAction.ConfirmDestination());
        fixture.location.emitSingle(available(sample(0.0, 0.0, 1L)));
        fixture.routes.succeed(0, fixture.route);
        fixture.viewModel.dispatch(new NavigationAction.StartNavigation());
        return fixture;
    }

    private static LocationResult available(LocationSample sample) {
        return new LocationResult(LocationStatus.AVAILABLE, sample);
    }

    private static LocationSample sample(double latitude, double longitude, long timeNanos) {
        return new LocationSample(
                new GeoPoint(latitude, longitude), 5.0, null, 0.0, timeNanos);
    }

    private static final class Fixture {
        final Destination destination = new Destination(
                "destination", "Destination", new GeoPoint(0.0, 0.0006),
                DestinationSource.MAP_TAP, null);
        final OutdoorRoute route = createRoute();
        final FakeRouteProvider routes = new FakeRouteProvider();
        final FakeLocationProvider location = new FakeLocationProvider();
        final FakeHeadingProvider heading = new FakeHeadingProvider();
        final NavigationViewModel viewModel;

        Fixture() {
            this(new SavedStateHandle());
        }

        Fixture(SavedStateHandle savedStateHandle) {
            viewModel = new NavigationViewModel(
                    savedStateHandle, routes, location, heading,
                    NavigationConfig.defaults(),
                    new DirectionCalculator());
        }

        NavigationUiState state() {
            return viewModel.getState().getValue();
        }

        private static OutdoorRoute createRoute() {
            GeoPoint start = new GeoPoint(0.0, 0.0);
            GeoPoint first = new GeoPoint(0.0, 0.0003);
            GeoPoint destination = new GeoPoint(0.0, 0.0006);
            return new OutdoorRoute(
                    Arrays.asList(start, first, destination),
                    Arrays.asList(
                            new Waypoint("w0", first, "직진", ManeuverType.STRAIGHT, 0),
                            new Waypoint("w1", destination, null, ManeuverType.ARRIVE, 1)),
                    66.0,
                    60L,
                    Collections.emptyList());
        }
    }

    private static final class FakeRouteProvider implements RouteProvider {
        final List<RouteCallback> callbacks = new ArrayList<>();
        final List<FakeCancelable> cancelables = new ArrayList<>();

        @Override
        public Cancelable computeWalkingRoute(
                GeoPoint origin, Destination destination, RouteCallback callback) {
            callbacks.add(callback);
            FakeCancelable cancelable = new FakeCancelable();
            cancelables.add(cancelable);
            return cancelable;
        }

        void succeed(int index, OutdoorRoute route) { callbacks.get(index).onSuccess(route); }
        void fail(int index, RouteError error) { callbacks.get(index).onFailure(error); }
    }

    private static final class FakeCancelable implements Cancelable {
        boolean canceled;
        @Override public void cancel() { canceled = true; }
        @Override public boolean isCanceled() { return canceled; }
    }

    private static final class FakeLocationProvider implements LocationProvider {
        LocationResultCallback singleCallback;
        LocationListener streamListener;
        int startCount;
        int stopCount;

        @Override public void start(LocationListener listener) {
            startCount++;
            streamListener = listener;
        }
        @Override public void stop() {
            if (streamListener != null) stopCount++;
            streamListener = null;
        }
        @Override public void requestSingleUpdate(LocationResultCallback callback) {
            singleCallback = callback;
        }

        void emitSingle(LocationResult result) { singleCallback.onLocationResult(result); }
        void emitStream(LocationResult result) { streamListener.onLocationChanged(result); }
    }

    private static final class FakeHeadingProvider implements HeadingProvider {
        HeadingListener listener;
        int startCount;
        int stopCount;
        @Override public void start(HeadingListener listener) {
            startCount++;
            this.listener = listener;
        }
        @Override public void stop() {
            if (listener != null) stopCount++;
            listener = null;
        }
        void emit(HeadingResult result) { listener.onHeadingChanged(result); }
    }
}
