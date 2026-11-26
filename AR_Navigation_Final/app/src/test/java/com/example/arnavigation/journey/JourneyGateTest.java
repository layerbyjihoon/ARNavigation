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
        assertFalse(gate.observe(.99, .1, true, 0, 0));
    }
    @Test public void nearThresholdStartsOnceAndGpsJitterDoesNotRestart() {
        JourneyGate gate = new JourneyGate();
        assertTrue(gate.updateGps(30, 20, 5000));
        assertTrue(gate.updateGps(50, 40, 0));
        assertTrue(gate.isChecking());
    }
    @Test public void freshConsecutiveFramesAtThresholdHandOffExactlyOnce() {
        JourneyGate gate = new JourneyGate(); gate.updateGps(1, 1, 0);
        assertFalse(gate.observe(.749999, .1, true, 0, 0));
        assertFalse(gate.observe(.99, .1, false, 1, 1));
        assertFalse(gate.observe(.99, .1, true, 2, 2003));
        assertFalse(gate.observe(.99, .1, true, 4, 3));
        assertFalse(gate.observe(Double.NaN, .1, true, 4, 4));
        assertFalse(gate.observe(1.1, .1, true, 5, 5));
        assertFalse(gate.observe(.75, .89, true, 10, 10));
        assertFalse(gate.observe(.99, .1, true, 10, 600));
        assertFalse(gate.observe(.75, .89, true, 310, 2310));
        assertTrue(gate.observe(.75, .89, true, 610, 2610));
        assertFalse(gate.observe(.99, .1, true, 700, 700));
        assertFalse(gate.updateGps(1, 1, 0));
        assertFalse(gate.isChecking()); assertTrue(gate.isHandingOff());
    }
    @Test public void glassViewBreaksTheStreakEvenWhenRepeated() {
        JourneyGate gate = new JourneyGate(); gate.updateGps(1, 1, 0);
        assertFalse(gate.observe(.8, .2, true, 0, 0));
        assertFalse(gate.observe(.99, .9, true, 500, 500));
        assertFalse(gate.observe(.99, .99, true, 1000, 1000));
        assertFalse(gate.observe(.8, .2, true, 1500, 1500));
        assertFalse(gate.observe(.8, .2, true, 2000, 2000));
        assertTrue(gate.observe(.8, .2, true, 2500, 2500));
    }
    @Test public void anInvalidFrameOrLongGapRequiresFreshConfirmation() {
        JourneyGate gate = new JourneyGate(); gate.updateGps(1, 1, 0);
        assertFalse(gate.observe(.8, .2, true, 10, 10));
        assertFalse(gate.observe(.8, .2, false, 500, 500));
        assertFalse(gate.observe(.8, .2, true, 750, 750));
        assertFalse(gate.observe(.8, .2, true, 3000, 3000));
        gate.resetObservations();
        assertFalse(gate.observe(.8, .2, true, 3500, 3500));
        assertFalse(gate.observe(.8, .2, true, 3900, 3900));
        assertTrue(gate.observe(.8, .2, true, 4300, 4300));
    }
    @Test public void aNewSessionNeedsFreshGpsAndDoesNotInheritOldHandoff() {
        JourneyGate first = new JourneyGate(); first.updateGps(1, 1, 0);
        assertFalse(first.observe(.8, .1, true, 0, 0));
        assertFalse(first.observe(.8, .1, true, 500, 500));
        assertTrue(first.observe(.8, .1, true, 1000, 1000));
        JourneyGate next = new JourneyGate();
        assertFalse(next.isChecking()); assertFalse(next.observe(.8, .1, true, 0, 0));
        assertTrue(next.updateGps(1, 1, 0));
        assertFalse(next.observe(.8, .1, true, 100, 100));
        assertFalse(next.observe(.8, .1, true, 500, 500));
        assertTrue(next.observe(.8, .1, true, 1000, 1000));
    }
}
