package com.example.arnavigation.journey;

import android.os.Bundle;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

/** Activity session destination and indoor route survive configuration/process recreation. */
public final class JourneyState extends ViewModel {
    private final SavedStateHandle saved;
    public JourneyGate gate = new JourneyGate();
    public JourneyState(SavedStateHandle saved) { this.saved = saved; }
    public androidx.lifecycle.LiveData<Boolean> launchReady() { return saved.getLiveData("launch_ready", false); }
    public boolean isLaunchReady() { return Boolean.TRUE.equals(saved.get("launch_ready")); }
    public void setLaunchReady(boolean value) { saved.set("launch_ready", value); }
    public Bundle route() { return saved.get("indoor_route"); }
    public String destinationId() { return saved.get("indoor_destination_id"); }
    public void set(String id, Bundle route) {
        saved.set("indoor_destination_id", id); saved.set("indoor_route", new Bundle(route));
        setLaunchReady(false); gate = new JourneyGate();
    }
    public void clear() {
        saved.remove("indoor_destination_id"); saved.remove("indoor_route"); setLaunchReady(false); gate = new JourneyGate();
    }
}
