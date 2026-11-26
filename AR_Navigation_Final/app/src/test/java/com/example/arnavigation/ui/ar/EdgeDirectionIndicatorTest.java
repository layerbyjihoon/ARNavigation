package com.example.arnavigation.ui.ar;

import static org.junit.Assert.*;
import org.junit.Test;

public final class EdgeDirectionIndicatorTest {
    private EdgeDirectionIndicator at(double angle, boolean visible) {
        return EdgeDirectionIndicator.place(angle,50,200,950,800,visible);
    }
    @Test public void usesTopSideAndBottomEdgesForCameraRelativeDirection() {
        assertEquals(200,at(0,false).y,0.01);
        assertEquals(950,at(90,false).x,0.01);
        assertEquals(50,at(-90,false).x,0.01);
        assertEquals(800,at(180,false).y,0.01);
        assertEquals(500,at(180,false).x,0.01);
        assertEquals(90,at(180,false).rotation,0.01);
        assertTrue(at(-135,false).x<500);
        assertEquals(800,at(-135,false).y,0.01);
        assertTrue(at(135,false).x>500);
    }
    @Test public void disappearsWhenForwardRouteReturnsButNotForUnrelatedFutureSegment() {
        assertNull(at(0,true));
        assertNull(at(30,true));
        assertNotNull(at(180,true));
        assertNotNull(at(0,false));
    }
    @Test public void handlesAngleWrapAndNoSpaceWithoutInvalidCoordinates() {
        assertEquals(at(-135,false).x,at(225,false).x,0.01);
        assertNull(EdgeDirectionIndicator.place(0,20,20,10,10,false));
        assertNull(at(Double.NaN,false));
    }
}
