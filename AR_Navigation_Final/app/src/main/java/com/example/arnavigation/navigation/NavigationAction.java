package com.example.arnavigation.navigation;

import com.example.arnavigation.model.Destination;
import java.util.Objects;

/** User intents accepted by NavigationViewModel. */
public interface NavigationAction {
    final class SelectDestination implements NavigationAction {
        private final Destination destination;

        public SelectDestination(Destination destination) {
            this.destination = Objects.requireNonNull(destination, "destination must not be null");
        }

        public Destination getDestination() { return destination; }
    }

    final class ConfirmDestination implements NavigationAction {}
    final class StartNavigation implements NavigationAction {}
    /** Temporarily releases foreground sensors while preserving navigation progress. */
    final class PauseNavigation implements NavigationAction {}
    /** Restarts foreground sensors for a previously navigating session. */
    final class ResumeNavigation implements NavigationAction {}
    final class StopNavigation implements NavigationAction {}
    final class RetryRoute implements NavigationAction {}

    final class SetCameraAvailable implements NavigationAction {
        private final boolean available;

        public SetCameraAvailable(boolean available) { this.available = available; }
        public boolean isAvailable() { return available; }
    }
}
