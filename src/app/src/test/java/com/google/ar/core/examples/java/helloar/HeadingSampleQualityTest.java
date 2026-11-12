package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import org.junit.Test;

public final class HeadingSampleQualityTest {
  @Test public void samsungVectorWithUnavailableHeadingErrorIsAccepted() {
    // Values observed on SM-S937N: a valid rotation with optional heading error = -1.
    float[] vector = {.07f,.19f,.67f,.71f,-1f};
    assertTrue(HeadingSampleQuality.isUsable(2,vector));
    assertTrue(HeadingSampleQuality.isUsable(3,vector));
  }

  @Test public void missingOptionalHeadingEstimateIsAccepted() {
    assertTrue(HeadingSampleQuality.isUsable(3,new float[]{0,0,0,1}));
    assertTrue(HeadingSampleQuality.isUsable(2,new float[]{0,0,0}));
    assertTrue(HeadingSampleQuality.isUsable(3,new float[]{0,0,0,1,0}));
  }

  @Test public void unknownErrorDoesNotBypassLowOrUnreliableSensorStatus() {
    for (int accuracy : new int[]{-1,0,1})
      assertFalse(HeadingSampleQuality.isUsable(accuracy,new float[]{0,0,0,1,-1}));
  }

  @Test public void knownLargeErrorIsStillRejected() {
    assertTrue(HeadingSampleQuality.isUsable(3,new float[]{0,0,0,1,(float)Math.toRadians(19)}));
    assertFalse(HeadingSampleQuality.isUsable(3,new float[]{0,0,0,1,(float)Math.toRadians(21)}));
  }

  @Test public void malformedAndNonFiniteValuesAreRejected() {
    assertFalse(HeadingSampleQuality.isUsable(3,null));
    assertFalse(HeadingSampleQuality.isUsable(3,new float[]{0,0}));
    assertFalse(HeadingSampleQuality.isUsable(3,new float[]{Float.NaN,0,0,1,-1}));
    assertFalse(HeadingSampleQuality.isUsable(3,new float[]{0,0,0,Float.POSITIVE_INFINITY,-1}));
    assertFalse(HeadingSampleQuality.isUsable(3,new float[]{0,0,0,1,Float.NaN}));
    assertFalse(HeadingSampleQuality.isUsable(3,new float[]{0,0,0,1,-2}));
  }
}
