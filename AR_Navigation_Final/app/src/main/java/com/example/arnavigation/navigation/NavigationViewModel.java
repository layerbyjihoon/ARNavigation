package com.example.arnavigation.navigation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.arnavigation.heading.HeadingProvider;
import com.example.arnavigation.heading.HeadingResult;
import com.example.arnavigation.heading.HeadingStatus;
import com.example.arnavigation.location.LocationProvider;
import com.example.arnavigation.location.LocationResult;
import com.example.arnavigation.location.LocationStatus;
import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.DestinationSource;
import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.HeadingSample;
import com.example.arnavigation.model.LocationSample;
import com.example.arnavigation.model.OutdoorRoute;
import com.example.arnavigation.model.Waypoint;
import com.example.arnavigation.routing.Cancelable;
import com.example.arnavigation.routing.RouteCallback;
import com.example.arnavigation.routing.RouteError;
import com.example.arnavigation.routing.RouteProvider;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Coordinates route, location, and heading sources without retaining Android UI objects. */
public final class NavigationViewModel extends ViewModel {
    private static final String KEY_ID = "destination.id";
    private static final String KEY_NAME = "destination.name";
    private static final String KEY_LATITUDE = "destination.latitude";
    private static final String KEY_LONGITUDE = "destination.longitude";
    private static final String KEY_SOURCE = "destination.source";
    private static final String KEY_PLACE_ID = "destination.placeId";

    private final SavedStateHandle savedStateHandle;
    private final RouteProvider routeProvider;
    private final LocationProvider locationProvider;
    private final HeadingProvider headingProvider;
    private final NavigationConfig config;
    private final DirectionCalculator directionCalculator;
    private final RouteProgressTracker progressTracker;
    private final ArrivalDetector arrivalDetector;
    private final MutableLiveData<NavigationUiState> state = new MutableLiveData<>();

    private NavigationStage stage = NavigationStage.NO_DESTINATION;
    private Destination destination;
    private OutdoorRoute route;
    private Waypoint activeWaypoint;
    private int activeWaypointIndex;
    private LocationSample location;
    private LocationStatus locationStatus = LocationStatus.UNAVAILABLE;
    private HeadingSample heading;
    private HeadingStatus headingStatus = HeadingStatus.UNAVAILABLE;
    private Double distanceToWaypointMeters;
    private Double relativeAngleDegrees;
    private boolean offRoute;
    private boolean rerouting;
    private RouteError routeError;
    private boolean cameraAvailable;
    private boolean navigationSourcesRunning;
    private long navigationStreamGeneration;
    private Cancelable activeRouteRequest;
    private long sessionGeneration;
    private long routeRequestGeneration;
    private boolean hasRerouteTimestamp;
    private long lastRerouteElapsedNanos;

    public NavigationViewModel(
            SavedStateHandle savedStateHandle,
            RouteProvider routeProvider,
            LocationProvider locationProvider,
            HeadingProvider headingProvider) {
        this(savedStateHandle, routeProvider, locationProvider, headingProvider,
                NavigationConfig.defaults(), new DirectionCalculator());
    }

    NavigationViewModel(
            SavedStateHandle savedStateHandle,
            RouteProvider routeProvider,
            LocationProvider locationProvider,
            HeadingProvider headingProvider,
            NavigationConfig config,
            DirectionCalculator directionCalculator) {
        this.savedStateHandle = Objects.requireNonNull(
                savedStateHandle, "savedStateHandle must not be null");
        this.routeProvider = Objects.requireNonNull(routeProvider, "routeProvider must not be null");
        this.locationProvider = Objects.requireNonNull(
                locationProvider, "locationProvider must not be null");
        this.headingProvider = Objects.requireNonNull(
                headingProvider, "headingProvider must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.directionCalculator = Objects.requireNonNull(
                directionCalculator, "directionCalculator must not be null");
        progressTracker = new RouteProgressTracker(config, directionCalculator);
        arrivalDetector = new ArrivalDetector(config, directionCalculator);
        restoreDestination();
        publish();
    }

    public LiveData<NavigationUiState> getState() {
        return state;
    }

    public void dispatch(NavigationAction action) {
        Objects.requireNonNull(action, "action must not be null");
        if (action instanceof NavigationAction.SelectDestination) {
            selectDestination(((NavigationAction.SelectDestination) action).getDestination());
        } else if (action instanceof NavigationAction.ConfirmDestination
                || action instanceof NavigationAction.RetryRoute) {
            confirmDestination();
        } else if (action instanceof NavigationAction.StartNavigation) {
            startNavigation();
        } else if (action instanceof NavigationAction.PauseNavigation) {
            pauseNavigation();
        } else if (action instanceof NavigationAction.ResumeNavigation) {
            resumeNavigation();
        } else if (action instanceof NavigationAction.StopNavigation) {
            stopAndClear();
        } else if (action instanceof NavigationAction.SetCameraAvailable) {
            boolean available = ((NavigationAction.SetCameraAvailable) action).isAvailable();
            // Camera synchronization runs from a LiveData observer and can report the same
            // permission/binding state synchronously. Re-publishing it never lets LiveData
            // finish dispatching, starving the main thread (including permission dialogs).
            if (cameraAvailable == available) return;
            cameraAvailable = available;
            publish();
        } else {
            throw new IllegalArgumentException("unknown navigation action");
        }
    }

    private void selectDestination(Destination selected) {
        stopSourcesAndCancelRoute();
        sessionGeneration++;
        resetRouteState();
        destination = selected;
        stage = NavigationStage.DESTINATION_SELECTED;
        saveDestination(selected);
        publish();
    }

    private void confirmDestination() {
        if (destination == null) return;
        stopSourcesAndCancelRoute();
        resetRouteOnly();
        stage = NavigationStage.ROUTE_LOADING;
        routeError = null;
        publish();
        long session = sessionGeneration;
        long locationRequest = routeRequestGeneration;
        locationProvider.requestSingleUpdate(result -> {
            if (session != sessionGeneration || locationRequest != routeRequestGeneration
                    || destination == null) return;
            applyLocationResult(result, false);
            if (result.getSample() != null && result.getStatus().isMapUsable()) {
                requestRoute(result.getSample().getPoint(), false);
            } else {
                stage = NavigationStage.DESTINATION_SELECTED;
                publish();
            }
        });
    }

    private void startNavigation() {
        if (stage != NavigationStage.ROUTE_READY || route == null) return;
        stage = NavigationStage.NAVIGATING;
        routeError = null;
        publish();
        startNavigationSources();
    }

    private void pauseNavigation() {
        if (stage != NavigationStage.NAVIGATING || !navigationSourcesRunning) return;
        navigationSourcesRunning = false;
        navigationStreamGeneration++;
        locationProvider.stop();
        headingProvider.stop();
        cameraAvailable = false;
        publish();
    }

    private void resumeNavigation() {
        if (stage == NavigationStage.NAVIGATING) startNavigationSources();
    }

    private void startNavigationSources() {
        if (navigationSourcesRunning) return;
        navigationSourcesRunning = true;
        long stream = ++navigationStreamGeneration;
        locationProvider.start(result -> {
            if (navigationSourcesRunning && stream == navigationStreamGeneration
                    && stage == NavigationStage.NAVIGATING) {
                applyLocationResult(result, true);
            }
        });
        headingProvider.start(result -> {
            if (navigationSourcesRunning && stream == navigationStreamGeneration
                    && stage == NavigationStage.NAVIGATING) {
                applyHeadingResult(result);
            }
        });
    }

    private boolean indoorTransferPending;
    public void setIndoorTransferPending(boolean pending) { indoorTransferPending = pending; }

    private void applyLocationResult(LocationResult result, boolean updateProgress) {
        locationStatus = result.getStatus();
        location = result.getSample();
        if (updateProgress && stage == NavigationStage.NAVIGATING
                && route != null && location != null
                && locationStatus == LocationStatus.AVAILABLE) {
            RouteProgress progress = progressTracker.update(location, route);
            activeWaypoint = progress.getActiveWaypoint();
            activeWaypointIndex = progress.getActiveWaypointIndex();
            distanceToWaypointMeters = progress.getDistanceToWaypointMeters();
            offRoute = progress.isOffRoute();
            if (!indoorTransferPending && arrivalDetector.update(location, destination.getPoint())) {
                stage = NavigationStage.ARRIVED;
                offRoute = false;
                rerouting = false;
                navigationSourcesRunning = false;
                navigationStreamGeneration++;
                locationProvider.stop();
                headingProvider.stop();
            } else if (offRoute) {
                maybeRequestReroute(location);
            }
        }
        updateRelativeAngle();
        publish();
    }

    private void applyHeadingResult(HeadingResult result) {
        headingStatus = result.getStatus();
        heading = result.getSample();
        updateRelativeAngle();
        publish();
    }

    private void maybeRequestReroute(LocationSample currentLocation) {
        if (rerouting) return;
        long now = currentLocation.getElapsedRealtimeNanos();
        long cooldownNanos = TimeUnit.MILLISECONDS.toNanos(config.getRerouteCooldownMillis());
        if (hasRerouteTimestamp && now >= lastRerouteElapsedNanos
                && now - lastRerouteElapsedNanos < cooldownNanos) {
            return;
        }
        hasRerouteTimestamp = true;
        lastRerouteElapsedNanos = now;
        requestRoute(currentLocation.getPoint(), true);
    }

    private void requestRoute(GeoPoint origin, boolean isReroute) {
        if (destination == null) return;
        long request = ++routeRequestGeneration;
        if (activeRouteRequest != null) activeRouteRequest.cancel();
        if (isReroute) rerouting = true;
        routeError = null;
        publish();
        activeRouteRequest = routeProvider.computeWalkingRoute(origin, destination, new RouteCallback() {
            @Override
            public void onSuccess(OutdoorRoute newRoute) {
                if (request != routeRequestGeneration || destination == null) return;
                activeRouteRequest = null;
                route = newRoute;
                progressTracker.reset();
                arrivalDetector.reset();
                activeWaypointIndex = 0;
                activeWaypoint = newRoute.getWaypoints().isEmpty()
                        ? null : newRoute.getWaypoints().get(0);
                distanceToWaypointMeters = null;
                offRoute = false;
                rerouting = false;
                routeError = null;
                if (!isReroute) stage = NavigationStage.ROUTE_READY;
                updateRelativeAngle();
                publish();
            }

            @Override
            public void onFailure(RouteError error) {
                if (request != routeRequestGeneration) return;
                activeRouteRequest = null;
                rerouting = false;
                routeError = error;
                if (!isReroute) stage = NavigationStage.DESTINATION_SELECTED;
                publish();
            }
        });
    }

    private void updateRelativeAngle() {
        if (location == null || heading == null || activeWaypoint == null) {
            relativeAngleDegrees = null;
            return;
        }
        boolean nearDestination = destination != null
                && directionCalculator.distanceMeters(location.getPoint(), destination.getPoint())
                    <= com.example.arnavigation.config.AppConfig.DIRECT_DESTINATION_RADIUS_METERS;
        double bearing = nearDestination
                ? directionCalculator.bearingDegrees(location.getPoint(), destination.getPoint())
                : route == null
                    ? directionCalculator.bearingDegrees(location.getPoint(), activeWaypoint.getPoint())
                    : RouteDirectionTarget.bearing(route, location.getPoint(), activeWaypointIndex,
                            config.getOffRouteDistanceMeters());
        relativeAngleDegrees = directionCalculator.relativeAngleDegrees(
                bearing, heading.getTrueHeadingDegrees());
    }

    private void stopAndClear() {
        stopSourcesAndCancelRoute();
        sessionGeneration++;
        destination = null;
        clearSavedDestination();
        resetRouteState();
        stage = NavigationStage.NO_DESTINATION;
        location = null;
        locationStatus = LocationStatus.UNAVAILABLE;
        heading = null;
        headingStatus = HeadingStatus.UNAVAILABLE;
        publish();
    }

    private void stopSourcesAndCancelRoute() {
        navigationSourcesRunning = false;
        navigationStreamGeneration++;
        locationProvider.stop();
        headingProvider.stop();
        routeRequestGeneration++;
        if (activeRouteRequest != null) {
            activeRouteRequest.cancel();
            activeRouteRequest = null;
        }
    }

    private void resetRouteState() {
        resetRouteOnly();
        hasRerouteTimestamp = false;
        lastRerouteElapsedNanos = 0L;
        routeError = null;
    }

    private void resetRouteOnly() {
        route = null;
        activeWaypoint = null;
        activeWaypointIndex = 0;
        distanceToWaypointMeters = null;
        relativeAngleDegrees = null;
        offRoute = false;
        rerouting = false;
        progressTracker.reset();
        arrivalDetector.reset();
    }

    private void publish() {
        boolean arrowVisible = stage == NavigationStage.NAVIGATING
                && cameraAvailable
                && locationStatus == LocationStatus.AVAILABLE
                && headingStatus == HeadingStatus.AVAILABLE
                && activeWaypoint != null
                && relativeAngleDegrees != null
                && !offRoute;
        state.setValue(new NavigationUiState(
                stage, destination, route, activeWaypoint, activeWaypointIndex,
                location, locationStatus, heading, headingStatus,
                distanceToWaypointMeters, relativeAngleDegrees,
                offRoute, rerouting, routeError, cameraAvailable, arrowVisible));
    }

    private void saveDestination(Destination value) {
        savedStateHandle.set(KEY_ID, value.getId());
        savedStateHandle.set(KEY_NAME, value.getName());
        savedStateHandle.set(KEY_LATITUDE, value.getPoint().getLatitude());
        savedStateHandle.set(KEY_LONGITUDE, value.getPoint().getLongitude());
        savedStateHandle.set(KEY_SOURCE, value.getSource().name());
        savedStateHandle.set(KEY_PLACE_ID, value.getPlaceId());
    }

    private void restoreDestination() {
        String id = savedStateHandle.get(KEY_ID);
        String name = savedStateHandle.get(KEY_NAME);
        Double latitude = savedStateHandle.get(KEY_LATITUDE);
        Double longitude = savedStateHandle.get(KEY_LONGITUDE);
        String source = savedStateHandle.get(KEY_SOURCE);
        if (id == null || name == null || latitude == null || longitude == null || source == null) {
            return;
        }
        try {
            destination = new Destination(
                    id, name, new GeoPoint(latitude, longitude),
                    DestinationSource.valueOf(source), savedStateHandle.get(KEY_PLACE_ID));
            stage = NavigationStage.DESTINATION_SELECTED;
        } catch (IllegalArgumentException error) {
            clearSavedDestination();
        }
    }

    private void clearSavedDestination() {
        savedStateHandle.remove(KEY_ID);
        savedStateHandle.remove(KEY_NAME);
        savedStateHandle.remove(KEY_LATITUDE);
        savedStateHandle.remove(KEY_LONGITUDE);
        savedStateHandle.remove(KEY_SOURCE);
        savedStateHandle.remove(KEY_PLACE_ID);
    }

    @Override
    protected void onCleared() {
        stopSourcesAndCancelRoute();
    }
}
