package com.example.arnavigation.heading;

/** Lifecycle-bound source of smoothed true-north headings. */
public interface HeadingProvider {
    void start(HeadingListener listener);

    void stop();
}
