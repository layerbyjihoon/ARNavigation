package com.example.arnavigation.journey;
import org.junit.Test;
import static org.junit.Assert.*;
public final class JourneyGateTest {
    @Test public void farOrInaccurateOrStaleGpsNeverStartsVision() {
        JourneyGate gate = new JourneyGate();
        assertFalse(gate.updateGps(31, 5, 0));
        assertFalse(gate.updateGps(1, 21, 0));
        assertFalse(gate.updateGps(1, 5, 5001));
        assertFalse(gate.updateGps(1, 5, -1));
        assertFalse(gate.updateGps(Double.NaN, 5, 0));
        assertFalse(gate.observe(.99, true, 0));
    }
    @Test public void nearThresholdStartsOnceAndGpsJitterDoesNotRestart() {
        JourneyGate gate = new JourneyGate();
        assertTrue(gate.updateGps(30, 20, 5000));
        assertTrue(gate.updateGps(50, 40, 0));
        assertTrue(gate.isChecking());
    }
    @Test public void singleValidFrameAtThresholdHandsOffExactlyOnce() {
        JourneyGate gate = new JourneyGate(); gate.updateGps(1, 1, 0);
        assertFalse(gate.observe(.749999, true, 0));
        assertFalse(gate.observe(.99, false, 0));
        assertFalse(gate.observe(.99, true, 2001));
        assertFalse(gate.observe(.99, true, -1));
        assertFalse(gate.observe(Double.NaN, true, 0));
        assertFalse(gate.observe(1.1, true, 0));
        assertTrue(gate.observe(.75, true, 2000));
        assertFalse(gate.observe(.99, true, 0));
        assertFalse(gate.updateGps(1, 1, 0));
        assertFalse(gate.isChecking()); assertTrue(gate.isHandingOff());
    }
    @Test public void aNewSessionNeedsFreshGpsAndDoesNotInheritOldHandoff() {
        JourneyGate first = new JourneyGate(); first.updateGps(1, 1, 0); first.observe(.8, true, 0);
        JourneyGate next = new JourneyGate();
        assertFalse(next.isChecking()); assertFalse(next.observe(.8, true, 0));
        assertTrue(next.updateGps(1, 1, 0)); assertTrue(next.observe(.8, true, 0));
    }
}
