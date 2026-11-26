package com.example.arnavigation.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.Map;
import java.util.Objects;

/** Owns foreground location/camera permission launchers and permanent-denial routing. */
final class PermissionCoordinator {
    private static final String PREFERENCES = "runtime_permissions";
    private static final String KEY_LOCATION_REQUESTED = "location_requested";
    private static final String KEY_CAMERA_REQUESTED = "camera_requested";

    interface Callback { void onPermissionStateChanged(); }

    private final AppCompatActivity activity;
    private final Callback callback;
    private final SharedPreferences preferences;
    private final ActivityResultLauncher<String[]> locationLauncher;
    private final ActivityResultLauncher<String> cameraLauncher;

    PermissionCoordinator(AppCompatActivity activity, Callback callback) {
        this.activity = Objects.requireNonNull(activity, "activity must not be null");
        this.callback = Objects.requireNonNull(callback, "callback must not be null");
        preferences = activity.getSharedPreferences(PREFERENCES, 0);
        locationLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), this::locationResult);
        cameraLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> callback.onPermissionStateChanged());
    }

    PermissionState getLocationState() {
        boolean fine = granted(Manifest.permission.ACCESS_FINE_LOCATION);
        boolean coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION);
        boolean rationale = ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACCESS_FINE_LOCATION)
                || ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACCESS_COARSE_LOCATION);
        return PermissionPolicy.location(
                fine, coarse, preferences.getBoolean(KEY_LOCATION_REQUESTED, false), rationale);
    }

    PermissionState getCameraState() {
        return PermissionPolicy.camera(
                granted(Manifest.permission.CAMERA),
                preferences.getBoolean(KEY_CAMERA_REQUESTED, false),
                ActivityCompat.shouldShowRequestPermissionRationale(
                        activity, Manifest.permission.CAMERA));
    }

    void requestLocation() {
        if (getLocationState() == PermissionState.PERMANENTLY_DENIED) {
            openSettings();
            return;
        }
        preferences.edit().putBoolean(KEY_LOCATION_REQUESTED, true).apply();
        locationLauncher.launch(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    void requestCamera() {
        if (getCameraState() == PermissionState.PERMANENTLY_DENIED) {
            openSettings();
            return;
        }
        preferences.edit().putBoolean(KEY_CAMERA_REQUESTED, true).apply();
        cameraLauncher.launch(Manifest.permission.CAMERA);
    }

    private void locationResult(Map<String, Boolean> ignored) {
        callback.onPermissionStateChanged();
    }

    private boolean granted(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openSettings() {
        activity.startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.getPackageName(), null)));
    }
}
