package com.example.arnavigation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.example.arnavigation.config.ApiAvailability;
import com.example.arnavigation.heading.HeadingProvider;
import com.example.arnavigation.heading.SensorHeadingProvider;
import com.example.arnavigation.location.FusedLocationProvider;
import com.example.arnavigation.location.LocationProvider;
import com.example.arnavigation.routing.Cancelable;
import com.example.arnavigation.routing.TmapRouteProvider;
import com.example.arnavigation.routing.RouteCallback;
import com.example.arnavigation.routing.RouteError;
import com.example.arnavigation.routing.RouteProvider;
import java.util.Objects;

/** Application-scoped composition root for a single outdoor navigation session. */
public final class NavigationDependencies {
    private final RouteProvider routeProvider;
    private final LocationProvider locationProvider;
    private final HeadingProvider headingProvider;

    private NavigationDependencies(
            RouteProvider routeProvider,
            LocationProvider locationProvider,
            HeadingProvider headingProvider) {
        this.routeProvider = Objects.requireNonNull(routeProvider);
        this.locationProvider = Objects.requireNonNull(locationProvider);
        this.headingProvider = Objects.requireNonNull(headingProvider);
    }

    public static NavigationDependencies create(
            Context context, ApiAvailability apiAvailability) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(apiAvailability, "apiAvailability must not be null");
        RouteProvider routes = apiAvailability.isRoutesAvailable()
                ? TmapRouteProvider.fromBuildConfig()
                : new UnavailableRouteProvider();
        return new NavigationDependencies(
                routes,
                new FusedLocationProvider(context),
                new SensorHeadingProvider(context));
    }

    public RouteProvider getRouteProvider() { return routeProvider; }
    public LocationProvider getLocationProvider() { return locationProvider; }
    public HeadingProvider getHeadingProvider() { return headingProvider; }

    private static final class UnavailableRouteProvider implements RouteProvider {
        @Override
        public Cancelable computeWalkingRoute(
                com.example.arnavigation.model.GeoPoint origin,
                com.example.arnavigation.model.Destination destination,
                RouteCallback callback) {
            Cancelable handle = new Cancelable() {
                private boolean canceled;
                @Override public void cancel() { canceled = true; }
                @Override public boolean isCanceled() { return canceled; }
            };
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!handle.isCanceled()) callback.onFailure(RouteError.AUTHENTICATION);
            });
            return handle;
        }
    }
}
