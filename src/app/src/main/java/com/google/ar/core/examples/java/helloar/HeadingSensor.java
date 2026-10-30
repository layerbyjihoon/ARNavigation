package com.google.ar.core.examples.java.helloar;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import java.util.ArrayDeque;

/** Magnetic rotation vector history, matched to the AR frame rather than the latest UI event. */
final class HeadingSensor implements SensorEventListener {
  private final SensorManager manager;
  private final Sensor sensor;
  private final ArrayDeque<Sample> samples = new ArrayDeque<>();
  private int accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
  private static final long MAX_SKEW_NS = 50_000_000L;
  private static final class Sample {
    final long time;
    final float[] matrix;
    Sample(long time, float[] matrix) { this.time = time; this.matrix = matrix; }
  }
  HeadingSensor(Context context) {
    manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    sensor = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
  }
  synchronized void resume() {
    samples.clear(); accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    if (sensor != null) manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
  }
  synchronized void pause() {
    if (manager != null) manager.unregisterListener(this);
    samples.clear();
  }
  synchronized float[] at(long timestamp) {
    if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) return null;
    Sample nearest = null;
    long delta = Long.MAX_VALUE;
    for (Sample sample : samples) {
      long distance = Math.abs(sample.time - timestamp);
      if (distance < delta) { nearest = sample; delta = distance; }
    }
    return nearest != null && delta <= MAX_SKEW_NS ? nearest.matrix.clone() : null;
  }
  @Override public synchronized void onSensorChanged(SensorEvent event) {
    accuracy = event.accuracy;
    if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        || (event.values.length >= 5 && (event.values[4] < 0
            || event.values[4] > Math.toRadians(20)))) {
      samples.clear(); return;
    }
    float[] matrix = new float[9];
    SensorManager.getRotationMatrixFromVector(matrix, event.values);
    for (float value : matrix) if (!Float.isFinite(value)) return;
    samples.addLast(new Sample(event.timestamp, matrix));
    while (samples.size() > 100) samples.removeFirst();
  }
  @Override public synchronized void onAccuracyChanged(Sensor sensor, int accuracy) {
    this.accuracy = accuracy;
    if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) samples.clear();
  }
}
