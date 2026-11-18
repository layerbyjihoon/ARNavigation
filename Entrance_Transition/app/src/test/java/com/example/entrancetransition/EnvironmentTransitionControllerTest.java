package com.example.entrancetransition;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class EnvironmentTransitionControllerTest {
    @Test public void thresholdIsInclusiveAndNeedsOnlyOneFrame() {
        EnvironmentTransitionController c = new EnvironmentTransitionController();
        c.observe(.75, true, 0, 0);
        assertEquals(EnvironmentTransitionController.Mode.INDOOR, c.mode());
    }
    @Test public void belowThresholdIsOutdoorAndCanReturnIndoorsImmediately() {
        EnvironmentTransitionController c = new EnvironmentTransitionController();
        c.observe(.749999, true, 0, 0);
        assertEquals(EnvironmentTransitionController.Mode.OUTDOOR, c.mode());
        c.observe(.9, true, 334, 334);
        assertEquals(EnvironmentTransitionController.Mode.INDOOR, c.mode());
        c.observe(.74, true, 668, 668);
        assertEquals(EnvironmentTransitionController.Mode.OUTDOOR, c.mode());
    }
    @Test public void invalidStructureAndScoresAreUnknown() {
        for (double score : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 1.01}) {
            EnvironmentTransitionController c = new EnvironmentTransitionController();
            c.observe(score, true, 0, 0);
            assertEquals(EnvironmentTransitionController.Mode.UNKNOWN, c.mode());
        }
        EnvironmentTransitionController c = new EnvironmentTransitionController();
        c.observe(.9, false, 0, 0);
        assertEquals(EnvironmentTransitionController.Mode.UNKNOWN, c.mode());
    }
    @Test public void staleFutureDuplicateAndOutOfOrderFramesCannotOverrideFreshMode() {
        EnvironmentTransitionController c = new EnvironmentTransitionController();
        c.observe(.9, true, 1000, 1000);
        c.observe(.1, true, 1000, 1000);
        c.observe(.1, true, 999, 1000);
        c.observe(.1, true, 2000, 1000);
        assertEquals(EnvironmentTransitionController.Mode.INDOOR, c.mode());
        c.reset();
        c.observe(.9, true, 0, 2001);
        assertEquals(EnvironmentTransitionController.Mode.UNKNOWN, c.mode());
    }
    @Test public void sessionResetAndMissingFramesClearClassification() {
        EnvironmentTransitionController c = new EnvironmentTransitionController();
        c.observe(.9, true, 0, 0);
        c.tick(2000);
        assertEquals(EnvironmentTransitionController.Mode.INDOOR, c.mode());
        c.tick(2001);
        assertEquals(EnvironmentTransitionController.Mode.UNKNOWN, c.mode());
        c.observe(.9, true, 3000, 3000);
        c.reset();
        assertEquals(EnvironmentTransitionController.Mode.UNKNOWN, c.mode());
    }
}
