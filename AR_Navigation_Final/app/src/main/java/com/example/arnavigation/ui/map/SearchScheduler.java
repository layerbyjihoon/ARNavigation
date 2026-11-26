package com.example.arnavigation.ui.map;

interface SearchScheduler {
    interface ScheduledTask {
        void cancel();
    }

    ScheduledTask schedule(long delayMillis, Runnable action);
}
