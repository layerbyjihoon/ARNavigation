package com.example.arnavigation.ui.ar;

import com.example.arnavigation.navigation.NavigationViewModel;
import com.example.arnavigation.ui.PermissionState;

/** Implemented by the Task 09 activity shell. */
public interface ArNavigationHost {
    NavigationViewModel getNavigationViewModel();

    void openMapTab();

    PermissionState getCameraPermissionState();

    void requestCameraPermission();
}
