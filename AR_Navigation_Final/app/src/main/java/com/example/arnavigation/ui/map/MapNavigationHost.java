package com.example.arnavigation.ui.map;

import com.example.arnavigation.navigation.NavigationViewModel;
import com.example.arnavigation.ui.PermissionState;

/** Implemented by the Task 09 activity shell. */
public interface MapNavigationHost {
    NavigationViewModel getNavigationViewModel();

    void openArTab();

    PermissionState getLocationPermissionState();

    void requestLocationPermission();
}
