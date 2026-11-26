package com.example.arnavigation.location;

/** Receives foreground location changes while a provider is started. */
public interface LocationListener {
    void onLocationChanged(LocationResult result);
}
