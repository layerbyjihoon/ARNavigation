package com.example.arnavigation.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import com.example.arnavigation.NavigationDependencies;
import com.example.arnavigation.navigation.NavigationViewModel;
import java.util.Objects;

/** Creates the activity-scoped ViewModel with saved state and app-level providers. */
final class NavigationViewModelFactory implements ViewModelProvider.Factory {
    private final NavigationDependencies dependencies;

    NavigationViewModelFactory(NavigationDependencies dependencies) {
        this.dependencies = Objects.requireNonNull(
                dependencies, "dependencies must not be null");
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
        if (!modelClass.isAssignableFrom(NavigationViewModel.class)) {
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
        SavedStateHandle handle = SavedStateHandleSupport.createSavedStateHandle(extras);
        return (T) new NavigationViewModel(
                handle,
                dependencies.getRouteProvider(),
                dependencies.getLocationProvider(),
                dependencies.getHeadingProvider());
    }
}
