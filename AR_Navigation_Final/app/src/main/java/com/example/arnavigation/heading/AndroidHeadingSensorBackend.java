package com.example.arnavigation.heading;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import com.example.arnavigation.model.SignalConfidence;

/** Rotation-vector sensor with accelerometer/magnetometer fallback. */
final class AndroidHeadingSensorBackend implements HeadingSensorBackend, SensorEventListener {
    private final SensorManager sensorManager;
    private final Display display;
    private final Sensor rotationVector;
    private final Sensor accelerometer;
    private final Sensor magneticField;
    private final float[] rotationMatrix = new float[9];
    private final float[] adjustedMatrix = new float[9];
    private final float[] orientation = new float[3];

    private Callback callback;
    private float[] accelerationValues;
    private float[] magneticValues;
    private int rotationAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    private int accelerometerAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    private int magneticAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;

    @SuppressWarnings("deprecation")
    AndroidHeadingSensorBackend(Context context) {
        Context applicationContext = context.getApplicationContext();
        sensorManager = (SensorManager) applicationContext.getSystemService(Context.SENSOR_SERVICE);
        WindowManager windowManager =
                (WindowManager) applicationContext.getSystemService(Context.WINDOW_SERVICE);
        display = windowManager == null ? null : windowManager.getDefaultDisplay();
        rotationVector = sensorManager == null
                ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        accelerometer = sensorManager == null
                ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magneticField = sensorManager == null
                ? null : sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    @Override
    public boolean start(Callback callback) {
        if (this.callback != null) return true;
        if (sensorManager == null) return false;
        this.callback = callback;
        if (rotationVector != null) {
            if (sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_GAME)) {
                return true;
            }
        } else if (accelerometer != null && magneticField != null) {
            boolean accelerationRegistered = sensorManager.registerListener(
                    this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            boolean magneticRegistered = sensorManager.registerListener(
                    this, magneticField, SensorManager.SENSOR_DELAY_GAME);
            if (accelerationRegistered && magneticRegistered) {
                return true;
            }
        }
        sensorManager.unregisterListener(this);
        this.callback = null;
        return false;
    }

    @Override
    public void stop() {
        if (callback != null && sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        callback = null;
        accelerationValues = null;
        magneticValues = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Callback current = callback;
        if (current == null) return;

        int sensorType = event.sensor.getType();
        SignalConfidence confidence;
        if (sensorType == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            confidence = confidenceFromAccuracy(rotationAccuracy);
        } else {
            if (sensorType == Sensor.TYPE_ACCELEROMETER) {
                accelerationValues = event.values.clone();
            } else if (sensorType == Sensor.TYPE_MAGNETIC_FIELD) {
                magneticValues = event.values.clone();
            } else {
                return;
            }
            if (accelerationValues == null || magneticValues == null
                    || !SensorManager.getRotationMatrix(
                            rotationMatrix, null, accelerationValues, magneticValues)) {
                return;
            }
            confidence = lowerConfidence(
                    confidenceFromAccuracy(accelerometerAccuracy),
                    confidenceFromAccuracy(magneticAccuracy));
        }

        if (!remapForUprightCamera(rotationMatrix, adjustedMatrix, displayRotation())) {
            current.onUnavailable();
            return;
        }
        SensorManager.getOrientation(adjustedMatrix, orientation);
        double magneticHeading = HeadingMath.normalizeDegrees(Math.toDegrees(orientation[0]));
        current.onMagneticHeading(
                magneticHeading, confidence, event.timestamp, System.currentTimeMillis());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        switch (sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
                rotationAccuracy = accuracy;
                break;
            case Sensor.TYPE_ACCELEROMETER:
                accelerometerAccuracy = accuracy;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magneticAccuracy = accuracy;
                break;
            default:
                break;
        }
    }

    private int displayRotation() {
        return display == null ? Surface.ROTATION_0 : display.getRotation();
    }

    private static boolean remapForUprightCamera(float[] input, float[] output, int rotation) {
        // The rear camera looks along -device Z. Android sensor axes are fixed to the
        // hardware and do not rotate with the UI. Using a display-dependent X here moves
        // the axis measured by getOrientation and can turn north into east in landscape.
        // Fixed X/Z remapping makes adjusted +Y equal -device Z for every display rotation.
        double horizontalCameraAxis = Math.hypot(input[2], input[5]);
        if (!Double.isFinite(horizontalCameraAxis) || horizontalCameraAxis < .2) {
            // A camera looking nearly straight up/down has no stable horizontal bearing.
            return false;
        }
        return SensorManager.remapCoordinateSystem(
                input, SensorManager.AXIS_X, SensorManager.AXIS_Z, output);
    }

    static SignalConfidence confidenceFromAccuracy(int accuracy) {
        switch (accuracy) {
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                return SignalConfidence.HIGH;
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                return SignalConfidence.MEDIUM;
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
            case SensorManager.SENSOR_STATUS_UNRELIABLE:
                return SignalConfidence.LOW;
            default:
                return SignalConfidence.UNAVAILABLE;
        }
    }

    private static SignalConfidence lowerConfidence(
            SignalConfidence first, SignalConfidence second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }
}
