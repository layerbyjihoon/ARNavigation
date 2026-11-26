package com.example.arnavigation.ui.ar;
import android.graphics.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.arnavigation.model.*;
import com.example.arnavigation.navigation.*;
import com.example.arnavigation.location.LocationStatus;
import com.example.arnavigation.heading.HeadingStatus;
import java.util.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class OutdoorDirectionArrowRenderTest {
    @Test public void forwardAndRearDrawOneCentralArrowWithoutRouteLine() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var context=instrumentation.getTargetContext();
        for(int angle:new int[]{0,180}) {
            Bitmap bitmap=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888);
            instrumentation.runOnMainSync(() -> {
                GeoPoint origin=new GeoPoint(37.49,126.95),target=new GeoPoint(37.4901,126.95);
                Waypoint point=new Waypoint("next",target,"직진",ManeuverType.STRAIGHT,0);
                OutdoorRoute route=new OutdoorRoute(Arrays.asList(origin,target),Collections.singletonList(point),11,10,Collections.emptyList());
                var state=new NavigationUiState(NavigationStage.NAVIGATING,
                    new Destination("d","문화관",target,DestinationSource.MAP_TAP,null),route,point,0,
                    new LocationSample(origin,1,null,0,1),LocationStatus.AVAILABLE,
                    new HeadingSample(angle,SignalConfidence.HIGH,1),HeadingStatus.AVAILABLE,
                    11.0,(double)angle,false,false,null,true,true);
                ArrowOverlayView view=new ArrowOverlayView(context); view.layout(0,0,1080,1920);
                view.setRoute(state); view.setDirection(angle,"방향 안내"); view.draw(new Canvas(bitmap));
            });
            int colored=0;
            for(int y=0;y<1920;y++)for(int x=0;x<1080;x++) if(Color.alpha(bitmap.getPixel(x,y))>0) {
                colored++;
                assertTrue("Only a central arrow, no line toward the horizon or screen edge",x>216 && x<864 && y>480 && y<1440);
            }
            assertTrue("Direction arrow is visible",colored>1000);
            try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"outdoor-direction-"+angle+".png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
            }
            bitmap.recycle();
        }
    }
}
