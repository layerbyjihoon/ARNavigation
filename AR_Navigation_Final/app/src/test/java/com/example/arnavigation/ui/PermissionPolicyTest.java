package com.example.arnavigation.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PermissionPolicyTest {
    @Test
    public void classifiesFineCoarseAndDeniedLocationCombinations() {
        assertEquals(
                PermissionState.GRANTED,
                PermissionPolicy.location(true, true, true, false));
        assertEquals(
                PermissionState.COARSE_ONLY,
                PermissionPolicy.location(false, true, true, false));
        assertEquals(
                PermissionState.REQUESTABLE,
                PermissionPolicy.location(false, false, false, false));
        assertEquals(
                PermissionState.REQUESTABLE,
                PermissionPolicy.location(false, false, true, true));
        assertEquals(
                PermissionState.PERMANENTLY_DENIED,
                PermissionPolicy.location(false, false, true, false));
    }

    @Test
    public void classifiesCameraDenialAndPermanentDenial() {
        assertEquals(PermissionState.GRANTED, PermissionPolicy.camera(true, true, false));
        assertEquals(PermissionState.REQUESTABLE, PermissionPolicy.camera(false, false, false));
        assertEquals(PermissionState.REQUESTABLE, PermissionPolicy.camera(false, true, true));
        assertEquals(
                PermissionState.PERMANENTLY_DENIED,
                PermissionPolicy.camera(false, true, false));
    }
}
