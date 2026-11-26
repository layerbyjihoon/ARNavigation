package com.example.arnavigation.navigation;

import static org.junit.Assert.assertEquals;

import com.example.arnavigation.model.GeoPoint;
import org.junit.Test;

public final class DirectionCalculatorTest {
    private final DirectionCalculator calculator = new DirectionCalculator();

    @Test
    public void calculatesCardinalBearings() {
        GeoPoint origin = new GeoPoint(0.0, 0.0);

        assertEquals(0.0, calculator.bearingDegrees(origin, new GeoPoint(1.0, 0.0)), 1.0e-9);
        assertEquals(90.0, calculator.bearingDegrees(origin, new GeoPoint(0.0, 1.0)), 1.0e-9);
        assertEquals(180.0, calculator.bearingDegrees(origin, new GeoPoint(-1.0, 0.0)), 1.0e-9);
        assertEquals(270.0, calculator.bearingDegrees(origin, new GeoPoint(0.0, -1.0)), 1.0e-9);
    }

    @Test
    public void relativeAngleWrapsAcrossNorth() {
        assertEquals(20.0, calculator.relativeAngleDegrees(10.0, 350.0), 0.0);
        assertEquals(-20.0, calculator.relativeAngleDegrees(350.0, 10.0), 0.0);
        assertEquals(-180.0, calculator.relativeAngleDegrees(180.0, 0.0), 0.0);
    }
}
