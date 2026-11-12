package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import androidx.test.platform.app.InstrumentationRegistry;
import android.app.Instrumentation;
import com.google.gson.*;
import com.google.ar.core.examples.java.helloar.segmentation.FrameSnapshot;
import com.google.ar.core.examples.java.helloar.localization.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;

/** Real Culture Hall grayscale frames and real model boxes, executed on the phone CPU. */
public final class FeatureTrackingIntegrationTest {
  private byte[] read(String name)throws IOException{
    try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] block=new byte[8192];int n;while((n=input.read(block))!=-1)out.write(block,0,n);return out.toByteArray();
    }
  }
  private float[] identity(){return new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};}
  @Test public void actualVideoTracksConfirmedDoorsBetweenModelFramesOnDevice()throws Exception{
    Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();byte[] gray=read("tracking-smoke.gray");
    JsonArray frames=JsonParser.parseString(new String(read("tracking-smoke.json"),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("frames");
    FeatureLandmarkTracker tracker=new FeatureLandmarkTracker();int index=0,confirmed=0,between=0;List<Double> timings=new ArrayList<>();
    for(JsonElement value:frames){JsonObject frame=value.getAsJsonObject();long time=(long)((frame.get("time_s").getAsDouble()+1)*1e9);
      FrameSnapshot snapshot=new FrameSnapshot(time,512,512,0,identity(),null,new float[]{400,400,256,256},new float[]{0,0,1,0,0,1},new float[]{0,0,1,0,0,1},null,false);
      List<LandmarkObservation> detections=null;
      if(!frame.get("detections").isJsonNull()){
        detections=new ArrayList<>();for(JsonElement item:frame.getAsJsonArray("detections")){JsonObject ob=item.getAsJsonObject();JsonArray box=ob.getAsJsonArray("box");
          detections.add(new LandmarkObservation(ob.get("type").getAsString(),box.get(0).getAsFloat(),box.get(1).getAsFloat(),box.get(2).getAsFloat(),box.get(3).getAsFloat(),
              ob.get("area").getAsInt(),ob.get("class_id").getAsInt(),ob.get("confidence").getAsFloat(),snapshot,false,null,null));}
      }
      long start=System.nanoTime();List<LandmarkObservation> result=tracker.update(new TrackingImage(256,256,Arrays.copyOfRange(gray,index*256*256,(index+1)*256*256),snapshot),detections);
      timings.add((System.nanoTime()-start)/1e6);
      boolean any=false;for(LandmarkObservation ob:result)if(ob.stable){any=true;assertTrue(ob.trackId>0);assertFalse(ob.metricValid);}
      if(any){confirmed++;if(detections==null)between++;}index++;
    }
    assertTrue("No actual-video confirmed tracks",confirmed>0);assertTrue("No tracks between model frames",between>0);
    Collections.sort(timings);android.os.Bundle evidence=new android.os.Bundle();evidence.putString("stream","\nActual video tracking: "+index+" frames; confirmed="+confirmed+" between-model="+between
        +" median="+timings.get(timings.size()/2)+"ms p95="+timings.get((int)(timings.size()*.95))+"ms\n");instrumentation.sendStatus(0,evidence);
  }
}
