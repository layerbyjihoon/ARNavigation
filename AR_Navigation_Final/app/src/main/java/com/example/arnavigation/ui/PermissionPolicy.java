package com.example.arnavigation.ui;

/** Pure permission-state decisions shared by runtime code and JVM tests. */
public final class PermissionPolicy {
    private PermissionPolicy() {}

    public static PermissionState camera(
            boolean granted, boolean requestedBefore, boolean showRationale) {
        if (granted) return PermissionState.GRANTED;
        return denied(requestedBefore, showRationale);
    }

    public static PermissionState location(
            boolean fineGranted,
            boolean coarseGranted,
            boolean requestedBefore,
            boolean showRationale) {
        if (fineGranted) return PermissionState.GRANTED;
        if (coarseGranted) return PermissionState.COARSE_ONLY;
        return denied(requestedBefore, showRationale);
    }

    private static PermissionState denied(boolean requestedBefore, boolean showRationale) {
        return requestedBefore && !showRationale
                ? PermissionState.PERMANENTLY_DENIED
                : PermissionState.REQUESTABLE;
    }
}
