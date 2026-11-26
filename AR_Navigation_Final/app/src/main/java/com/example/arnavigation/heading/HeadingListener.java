package com.example.arnavigation.heading;

/** Receives true-north heading changes while a provider is started. */
public interface HeadingListener {
    void onHeadingChanged(HeadingResult result);
}
