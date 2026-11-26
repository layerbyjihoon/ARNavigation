package com.example.arnavigation;

import android.app.Application;
import android.util.Log;

import com.example.arnavigation.config.ApiAvailability;
import com.google.android.libraries.places.api.Places;

/** Application-level initialization that does not depend on an Activity. */
public final class ArNavigationApplication extends Application {
    private static final String TAG = "ArNavigationApp";

    private ApiAvailability apiAvailability;
    private NavigationDependencies navigationDependencies;

    @Override
    public void onCreate() {
        super.onCreate();

        apiAvailability = ApiAvailability.fromBuildConfig();
        navigationDependencies = NavigationDependencies.create(this, apiAvailability);
        if (apiAvailability.isPlacesAvailable()) {
            Places.initializeWithNewPlacesApiEnabled(this, BuildConfig.PLACES_API_KEY);
        } else if (BuildConfig.DEBUG) {
            Log.w(TAG, "Places API key is not configured; place search will be unavailable.");
        }
    }

    public ApiAvailability getApiAvailability() {
        return apiAvailability;
    }

    public NavigationDependencies getNavigationDependencies() {
        return navigationDependencies;
    }
}
