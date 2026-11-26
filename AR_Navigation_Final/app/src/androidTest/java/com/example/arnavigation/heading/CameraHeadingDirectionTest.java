package com.example.arnavigation.heading;

import android.hardware.SensorManager;
import android.view.Surface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class CameraHeadingDirectionTest {
    @Test public void northFacingRearCameraMustNotPointSouth() throws Exception {
        // Device right = east, device top = sky, display normal = south.
        // Therefore the rear camera optical axis (-device Z) points north.
        float[] deviceToWorld = {1,0,0, 0,0,-1, 0,1,0};
        float[] adjusted = new float[9], orientation = new float[3];
        Method remap = AndroidHeadingSensorBackend.class.getDeclaredMethod(
                "remapForUprightCamera",float[].class,float[].class,int.class);
        remap.setAccessible(true);
        assertTrue((Boolean)remap.invoke(null,deviceToWorld,adjusted,Surface.ROTATION_0));
        SensorManager.getOrientation(adjusted,orientation);
        double heading = HeadingMath.normalizeDegrees(Math.toDegrees(orientation[0]));
        assertEquals("Rear camera faces north, not the display normal toward the user",0,
                Math.min(heading,360-heading),.01);
    }
    @Test public void cameraHeadingMustStayTheSameAcrossDisplayRotationAndRoll() throws Exception {
        Method remap = AndroidHeadingSensorBackend.class.getDeclaredMethod(
                "remapForUprightCamera",float[].class,float[].class,int.class);
        remap.setAccessible(true);
        for (int rotation=0;rotation<4;rotation++) {
            double roll = Math.toRadians(rotation*90);
            for (int bearing : new int[]{0,90,180,270}) {
                double a=Math.toRadians(bearing),c=Math.cos(roll),s=Math.sin(roll);
                float[] matrix={(float)(Math.cos(a)*c),(float)(-Math.cos(a)*s),(float)-Math.sin(a),
                        (float)(-Math.sin(a)*c),(float)(Math.sin(a)*s),(float)-Math.cos(a),
                        (float)Math.sin(roll),(float)Math.cos(roll),0};
                float[] adjusted=new float[9],orientation=new float[3];
                assertTrue((Boolean)remap.invoke(null,matrix,adjusted,rotation));
                SensorManager.getOrientation(adjusted,orientation);
                double heading=HeadingMath.normalizeDegrees(Math.toDegrees(orientation[0]));
                double delta=Math.abs(heading-bearing);delta=Math.min(delta,360-delta);
                assertEquals("rear bearing="+bearing+" display rotation="+rotation+" measured="+heading,
                        0,delta,.01);
            }
        }
    }

    @Test public void pitchedAndRolledCameraUsesOpticalAxisAndRejectsVerticalLook() throws Exception {
        Method remap = AndroidHeadingSensorBackend.class.getDeclaredMethod(
                "remapForUprightCamera",float[].class,float[].class,int.class);
        remap.setAccessible(true);
        for(int bearing : new int[]{0,45,90,180,270,359}) {
            for(int pitch : new int[]{-60,-30,0,30,60}) {
                for(int roll : new int[]{-90,-20,0,20,90,180}) {
                    double a=Math.toRadians(bearing),p=Math.toRadians(pitch),r=Math.toRadians(roll);
                    double[] right={Math.cos(a),-Math.sin(a),0};
                    double[] up={Math.sin(a)*Math.sin(p),Math.cos(a)*Math.sin(p),Math.cos(p)};
                    double[] back={-Math.sin(a)*Math.cos(p),-Math.cos(a)*Math.cos(p),Math.sin(p)};
                    float[] matrix=new float[9];
                    for(int row=0;row<3;row++) {
                        matrix[row*3]=(float)(right[row]*Math.cos(r)+up[row]*Math.sin(r));
                        matrix[row*3+1]=(float)(-right[row]*Math.sin(r)+up[row]*Math.cos(r));
                        matrix[row*3+2]=(float)back[row];
                    }
                    float[] adjusted=new float[9],orientation=new float[3];
                    assertTrue((Boolean)remap.invoke(null,matrix,adjusted,Surface.ROTATION_0));
                    SensorManager.getOrientation(adjusted,orientation);
                    double measured=HeadingMath.normalizeDegrees(Math.toDegrees(orientation[0]));
                    double delta=Math.abs(measured-bearing);delta=Math.min(delta,360-delta);
                    assertEquals("bearing="+bearing+" pitch="+pitch+" roll="+roll,0,delta,.01);
                }
            }
        }
        assertFalse("Straight down has no stable compass direction",(Boolean)remap.invoke(null,
                new float[]{1,0,0,0,1,0,0,0,1},new float[9],Surface.ROTATION_0));
    }

}
