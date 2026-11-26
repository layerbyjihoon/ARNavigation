package com.example.arnavigation.routing;

/** Handle for an asynchronous route request. */
public interface Cancelable {
    void cancel();

    boolean isCanceled();
}
