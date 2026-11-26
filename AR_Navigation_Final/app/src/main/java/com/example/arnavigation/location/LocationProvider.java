package com.example.arnavigation.location;

/** Lifecycle-bound foreground location source. */
public interface LocationProvider {
    void start(LocationListener listener);

    void stop();

    void requestSingleUpdate(LocationResultCallback callback);
}
