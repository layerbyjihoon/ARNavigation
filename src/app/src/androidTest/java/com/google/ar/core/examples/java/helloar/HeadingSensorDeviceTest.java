package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.assertNotNull;

import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

/** Confirms this phone supplies usable magnetic heading samples to the AR alignment path. */
public final class HeadingSensorDeviceTest {
  @Test public void rotationVectorProvidesRecentHeadingSample() {
    HeadingSensor sensor = new HeadingSensor(InstrumentationRegistry.getInstrumentation().getTargetContext());
    InstrumentationRegistry.getInstrumentation().runOnMainSync(sensor::resume);
    try {
      long deadline = SystemClock.elapsedRealtime() + 5000;
      float[] sample = null;
      while (sample == null && SystemClock.elapsedRealtime() < deadline) {
        SystemClock.sleep(30);
        sample = sensor.at(SystemClock.elapsedRealtimeNanos());
      }
      assertNotNull("No accepted rotation-vector heading sample", sample);
    } finally {
      InstrumentationRegistry.getInstrumentation().runOnMainSync(sensor::pause);
    }
  }
}
