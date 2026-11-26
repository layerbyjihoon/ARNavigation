package com.example.arnavigation.ui;

/** Implemented by visible screens that react to an Activity-owned permission result. */
public interface PermissionAware {
    void onPermissionStateChanged();
}
