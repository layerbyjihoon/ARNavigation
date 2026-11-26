package com.example.arnavigation.heading;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class HeadingMathTest {
    @Test
    public void normalizesUnsignedAndSignedAngles() {
        assertEquals(180.0, HeadingMath.normalizeDegrees(-180.0), 0.0);
        assertEquals(0.0, HeadingMath.normalizeDegrees(360.0), 0.0);
        assertEquals(270.0, HeadingMath.normalizeDegrees(-90.0), 0.0);
        assertEquals(-180.0, HeadingMath.normalizeSignedDegrees(180.0), 0.0);
        assertEquals(-1.0, HeadingMath.normalizeSignedDegrees(359.0), 0.0);
        assertEquals(90.0, HeadingMath.normalizeSignedDegrees(450.0), 0.0);
    }

    @Test
    public void appliesDeclinationAndWrapsResult() {
        assertEquals(5.0, HeadingMath.applyDeclination(355.0, 10.0), 0.0);
        assertEquals(345.0, HeadingMath.applyDeclination(5.0, -20.0), 0.0);
    }

    @Test
    public void cardinalHeadingsRemainCanonical() {
        assertEquals(0.0, HeadingMath.normalizeDegrees(0.0), 0.0);
        assertEquals(90.0, HeadingMath.normalizeDegrees(90.0), 0.0);
        assertEquals(180.0, HeadingMath.normalizeDegrees(180.0), 0.0);
        assertEquals(270.0, HeadingMath.normalizeDegrees(270.0), 0.0);
    }
}
