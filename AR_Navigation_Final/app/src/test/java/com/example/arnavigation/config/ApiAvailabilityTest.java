package com.example.arnavigation.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ApiAvailabilityTest {
    @Test
    public void emptyConfigurationDisablesExternalApis() {
        ApiAvailability availability = ApiAvailability.evaluate("", "", "", "");

        assertFalse(availability.isMapsAvailable());
        assertFalse(availability.isPlacesAvailable());
        assertFalse(availability.isRoutesAvailable());
    }

    @Test
    public void httpsProxyTakesPrecedenceAsAvailableRoutesConfiguration() {
        ApiAvailability availability = ApiAvailability.evaluate(
                "maps", "places", "", "https://routes.example.test/");

        assertTrue(availability.isMapsAvailable());
        assertTrue(availability.isPlacesAvailable());
        assertTrue(availability.isRoutesAvailable());
        assertTrue(availability.isRoutesUsingProxy());
    }

    @Test
    public void tmapKeyEnablesPedestrianRoutes() {
        ApiAvailability availability = ApiAvailability.evaluate(
                "maps", "places", "tmap-key", "");

        assertTrue(availability.isRoutesAvailable());
        assertFalse(availability.isRoutesUsingProxy());
    }
}

