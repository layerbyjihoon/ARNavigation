package com.example.arnavigation.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.*;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.arnavigation.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class FigmaLayoutTest {
    @Test public void arCardsAndActionsStayApartInPortraitAndLandscape() {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            var context = new androidx.appcompat.view.ContextThemeWrapper(
                instrumentation.getTargetContext(), R.style.Theme_ARNavigation);
            for (int[] size : new int[][]{{360,752},{752,312}}) {
                float density = context.getResources().getDisplayMetrics().density;
                View root = LayoutInflater.from(context).inflate(R.layout.fragment_ar_navigation, null);
                ((TextView)root.findViewById(R.id.ar_destination)).setText("문화관");
                ((TextView)root.findViewById(R.id.ar_instruction)).setText("직진 후 30m 앞 우회전");
                ((TextView)root.findViewById(R.id.ar_status)).setText("위치 신뢰도 높음");
                ((TextView)root.findViewById(R.id.ar_distance)).setText("120m 남음");
                int width = Math.round(size[0]*density), height = Math.round(size[1]*density);
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                root.layout(0,0,width,height);
                View header = root.findViewById(R.id.ar_header_card);
                View footer = root.findViewById(R.id.ar_guidance_card);
                assertTrue("Guidance leaves camera space", header.getBottom() < footer.getTop());
                View cancel = root.findViewById(R.id.end_navigation_button);
                View map = root.findViewById(R.id.show_map_button);
                View search = root.findViewById(R.id.change_route_button);
                assertTrue("Actions do not overlap",cancel.getRight() <= map.getLeft() && map.getRight() <= search.getLeft());
                assertTrue("Search stays inside footer", search.getRight() <= footer.getWidth());
                assertTrue("48dp touch target",search.getHeight() >= Math.round(48*density));
                Bitmap bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                root.draw(new Canvas(bitmap));
                try (FileOutputStream file = new FileOutputStream(new File(context.getExternalFilesDir(null),
                        "figma-ar-"+size[0]+".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,file); }
                catch(IOException e) { throw new AssertionError(e); }
                bitmap.recycle();
            }
        });
    }
}
