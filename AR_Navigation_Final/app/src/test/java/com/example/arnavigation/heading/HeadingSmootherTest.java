package com.example.arnavigation.heading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HeadingSmootherTest {
    @Test
    public void averagesAcrossNorthInsteadOfReturningSouth() {
        HeadingSmoother smoother = new HeadingSmoother(2);

        smoother.add(359.0);
        double result = smoother.add(1.0);

        assertTrue(result < 0.001 || result > 359.999);
    }

    @Test
    public void resetDiscardsPreviousWindow() {
        HeadingSmoother smoother = new HeadingSmoother(3);
        smoother.add(90.0);
        smoother.add(90.0);
        smoother.reset();

        assertEquals(270.0, smoother.add(270.0), 0.0);
    }
}
