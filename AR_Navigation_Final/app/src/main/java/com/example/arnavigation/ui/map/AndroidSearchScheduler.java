package com.example.arnavigation.ui.map;

import android.os.Handler;
import android.os.Looper;

final class AndroidSearchScheduler implements SearchScheduler {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public ScheduledTask schedule(long delayMillis, Runnable action) {
        handler.postDelayed(action, delayMillis);
        return () -> handler.removeCallbacks(action);
    }
}
