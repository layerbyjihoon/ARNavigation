package com.example.arnavigation.journey;

import android.content.Intent;
import android.os.SystemClock;
import android.graphics.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.ar.core.examples.java.helloar.MapRouteActivity;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class RoomButtonAppearanceTest {
    @Test public void selectedRoomButtonKeepsReadableColoredBackground() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),MapRouteActivity.class)
            .putExtra("final_pick_room",true).putExtra("map_index",0);
        try (ActivityScenario<MapRouteActivity> scenario = ActivityScenario.launch(intent)) {
            boolean[] ready = {false};
            for(int i=0;i<80 && !ready[0];i++) {
                scenario.onActivity(activity -> ready[0] = activity.findViewById(
                    com.google.ar.core.examples.java.helloar.R.id.final_room_list) != null);
                if(!ready[0]) SystemClock.sleep(100);
            }
            assertTrue(ready[0]);
            scenario.onActivity(activity -> {
                ListView rooms = activity.findViewById(com.google.ar.core.examples.java.helloar.R.id.final_room_list);
                rooms.performItemClick(null,0,rooms.getItemIdAtPosition(0));
            });
            // Let drawable state/tint updates complete, just as after the user's tap.
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                Button button = activity.findViewById(com.google.ar.core.examples.java.helloar.R.id.apply_ar_route);
                assertTrue(button.isEnabled());
                int width = Math.max(400,button.getWidth()),height = Math.max(100,button.getHeight());
                Bitmap background = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                var drawable = button.getBackground();
                if (drawable != null) {
                    drawable.setBounds(0,0,width,height);
                    drawable.jumpToCurrentState(); drawable.draw(new Canvas(background));
                }
                int fill = background.getPixel(width/2,height/2);
                if(Color.alpha(fill)<255) fill=androidx.core.graphics.ColorUtils.compositeColors(fill,
                    activity.getColor(com.google.ar.core.examples.java.helloar.R.color.nav_surface));
                int text=button.getCurrentTextColor();
                android.view.View root=activity.findViewById(android.R.id.content);
                Bitmap screen=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
                root.draw(new Canvas(screen));
                try(FileOutputStream out=new FileOutputStream(new File(activity.getExternalFilesDir(null),"room-selected-button.png"))) {
                    screen.compress(Bitmap.CompressFormat.PNG,100,out);
                } catch(IOException e) {throw new AssertionError(e);}
                screen.recycle(); background.recycle();
                assertTrue("Selected-room button needs readable contrast: fill="+Integer.toHexString(fill)
                    +" text="+Integer.toHexString(text), androidx.core.graphics.ColorUtils.calculateContrast(text,fill)>=4.5);
                assertNotEquals("Active button must not be white",Color.WHITE,fill);
            });
        }
    }
}
