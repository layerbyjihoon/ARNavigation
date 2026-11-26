package com.example.arnavigation.location;

/** Receives exactly one best-effort result for a single location request. */
public interface LocationResultCallback {
    void onLocationResult(LocationResult result);
}
