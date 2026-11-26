package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.*;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.ar.core.examples.java.helloar.segmentation.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.*;
import org.junit.Test;

/** Actual ONNX Android inference; no fake logits or camera frames. */
public final class SegFormerIntegrationTest {
  private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  private float[] identity(){return new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};}
  @Test public void staticCultureHallFrameAgreesWithDesktopOnnx()throws Exception {
    byte[] bytes;
    try(InputStream in=instrumentation.getContext().getAssets().open("segformer-smoke-input.f32");ByteArrayOutputStream out=new ByteArrayOutputStream()) {
      byte[] block=new byte[8192];int n;while((n=in.read(block))>=0)out.write(block,0,n);bytes=out.toByteArray();
    }
    ByteBuffer direct=ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.LITTLE_ENDIAN);direct.put(bytes).flip();
    FrameSnapshot snapshot=new FrameSnapshot(1,512,512,0,identity(),null,new float[]{500,500,256,256},
        new float[]{0,0,1,0,0,1},new float[]{0,0,1,0,0,1},null,true);
    try(SegFormerRunner runner=new SegFormerRunner(instrumentation.getTargetContext().getAssets())) {
      SegmentationResult result=runner.inferNchw(direct.asFloatBuffer(),snapshot);
      assertEquals(128,result.width);assertEquals(128,result.height);
      Bitmap expected;
      try(InputStream in=instrumentation.getContext().getAssets().open("segformer-smoke-mask.png")){expected=BitmapFactory.decodeStream(in);}
      int agreement=0;for(int y=0;y<128;y++)for(int x=0;x<128;x++)
        if((expected.getPixel(x,y)&255)==(result.labels[y*128+x]&255))agreement++;
      double fraction=agreement/(128.0*128);
      assertTrue("Desktop/Android mask agreement="+fraction,fraction>.98);
      android.os.Bundle evidence=new android.os.Bundle();evidence.putString("stream","\nStatic ONNX: "+result.inferenceMillis+" ms; desktop agreement="+fraction+"\n");
      instrumentation.sendStatus(0,evidence);expected.recycle();
    }
  }

  @Test public void realArCameraInfersAndResumesWithoutReplacingRenderer()throws Exception {
    instrumentation.getUiAutomation().grantRuntimePermission(instrumentation.getTargetContext().getPackageName(),Manifest.permission.CAMERA);
    HelloArActivity activity=(HelloArActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),HelloArActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      Field field=HelloArActivity.class.getDeclaredField("segmentation");field.setAccessible(true);
      SegmentationController controller=(SegmentationController)field.get(activity);
      SegmentationController.Completed first=await(controller,20_000);
      assertTrue(first.segmentation.frame.timestamp>0);assertEquals(150,first.segmentation.names.length);
      assertNotEquals(com.google.ar.core.examples.java.helloar.localization.MatchResult.Status.MATCH_CONFIRMED,first.match.status);
      NavigationTest.assertRouteRendersOnGlThread(activity);
      instrumentation.runOnMainSync(()->instrumentation.callActivityOnPause(activity));
      assertNull(controller.latest());
      instrumentation.runOnMainSync(()->instrumentation.callActivityOnResume(activity));
      SegmentationController.Completed resumed=await(controller,15_000);
      assertTrue(resumed.segmentation.frame.timestamp>first.segmentation.frame.timestamp);
      NavigationTest.assertRouteRendersOnGlThread(activity);
      android.os.Bundle evidence=new android.os.Bundle();evidence.putString("stream","\nAR camera ONNX: "+resumed.segmentation.inferenceMillis
          +" ms; depth="+(resumed.segmentation.frame.depth!=null)+"; rotation="+resumed.segmentation.frame.rotationDegrees+"\n");
      instrumentation.sendStatus(0,evidence);
      // Capture the actual debug overlay for visual QA in the test-only external directory.
      instrumentation.runOnMainSync(()->{
        ((android.widget.CheckBox)activity.findViewById(R.id.semantic_mask)).setChecked(true);
        ((android.widget.CheckBox)activity.findViewById(R.id.semantic_landmarks)).setChecked(true);
        ((android.widget.CheckBox)activity.findViewById(R.id.semantic_status)).setChecked(true);
      });
      SystemClock.sleep(1000);
      Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
      if(screenshot!=null)try(FileOutputStream out=new FileOutputStream(new File(activity.getExternalFilesDir(null),"segformer-debug-qa.png"))) {
        screenshot.compress(Bitmap.CompressFormat.PNG,100,out);screenshot.recycle();
      }
    } finally {instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();}
  }
  private SegmentationController.Completed await(SegmentationController controller,long timeout) {
    long deadline=SystemClock.elapsedRealtime()+timeout;
    while(SystemClock.elapsedRealtime()<deadline){SegmentationController.Completed result=controller.latest();if(result!=null)return result;SystemClock.sleep(100);}
    throw new AssertionError("No camera inference: "+controller.state());
  }
}
