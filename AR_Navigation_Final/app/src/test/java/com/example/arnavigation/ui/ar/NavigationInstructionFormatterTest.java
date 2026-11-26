package com.example.arnavigation.ui.ar;

import static org.junit.Assert.assertEquals;

import com.example.arnavigation.model.ManeuverType;
import org.junit.Test;

public final class NavigationInstructionFormatterTest {
    private final NavigationInstructionFormatter formatter =
            new NavigationInstructionFormatter(15.0d);

    @Test
    public void formatsRequiredAngleBoundaries() {
        assertEquals("왼쪽으로 180°", formatter.format(ManeuverType.UNKNOWN, -180.0d));
        assertEquals("직진 방향", formatter.format(ManeuverType.UNKNOWN, -15.0d));
        assertEquals("직진 방향", formatter.format(ManeuverType.UNKNOWN, 0.0d));
        assertEquals("직진 방향", formatter.format(ManeuverType.UNKNOWN, 15.0d));
        assertEquals("오른쪽으로 180°", formatter.format(ManeuverType.UNKNOWN, 180.0d));
    }

    @Test
    public void arriveManeuverOverridesAngleWording() {
        assertEquals("목적지에 도착합니다", formatter.format(ManeuverType.ARRIVE, 90.0d));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonFiniteAngle() {
        formatter.format(ManeuverType.UNKNOWN, Double.NaN);
    }
}
