package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

public final class VisionDebugUiTest {
  @Test public void modelStatusOpensWithoutSelectingAStairRoute() {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    instrumentation.getUiAutomation().grantRuntimePermission(
        instrumentation.getTargetContext().getPackageName(), Manifest.permission.CAMERA);
    HelloArActivity activity = (HelloArActivity) instrumentation.startActivitySync(new Intent(
        instrumentation.getTargetContext(), HelloArActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      instrumentation.runOnMainSync(() -> {
        View panel = activity.findViewById(R.id.ar_tools_panel);
        TextView status = activity.findViewById(R.id.vision_debug_status);
        assertEquals(View.GONE, panel.getVisibility());
        activity.findViewById(R.id.ar_tools_toggle).performClick();
        assertEquals(View.VISIBLE, panel.getVisibility());
        TextView stairToggle = activity.findViewById(R.id.stair_display_toggle);
        assertEquals(activity.getString(R.string.stair_display_line),
            stairToggle.getText().toString());
        stairToggle.performClick();
        assertEquals(activity.getString(R.string.stair_display_marker),
            stairToggle.getText().toString());
        stairToggle.performClick();
        assertEquals(activity.getString(R.string.stair_display_line),
            stairToggle.getText().toString());
        assertEquals(View.GONE, activity.findViewById(R.id.stair_ar_marker).getVisibility());
        activity.findViewById(R.id.vision_debug_button).performClick();
        assertEquals(View.VISIBLE, status.getVisibility());
      });
      long deadline = SystemClock.elapsedRealtime() + 7000;
      while (SystemClock.elapsedRealtime() < deadline) {
        if (((TextView) activity.findViewById(R.id.vision_debug_status))
            .getText().toString().contains("계단 모델:")) return;
        SystemClock.sleep(100);
      }
      fail("Vision model status did not update from the AR frame loop");
    } finally {
      instrumentation.runOnMainSync(activity::finish);
    }
  }
}
