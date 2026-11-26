package com.google.ar.core.examples.java.helloar;

import android.media.Image;
import android.os.SystemClock;
import com.google.ar.core.Frame;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.label.ImageLabeler;
import java.util.concurrent.atomic.AtomicBoolean;

/** Throttled on-device image classification; geometry comes from ARCore depth, not labels. */
final class StairVisionDetector implements AutoCloseable {
  private static final long INTERVAL_MS = 700;
  private static final float STAIR_CONFIDENCE = 0.3f;
  private final ImageLabeler labeler = ImageLabeling.getClient(
      new ImageLabelerOptions.Builder().setConfidenceThreshold(STAIR_CONFIDENCE).build());
  private final AtomicBoolean busy = new AtomicBoolean();
  private volatile float confidence;
  private volatile long resultAt;
  private volatile String lastError;
  private long submittedAt;

  void submit(Frame frame, int rotationDegrees) {
    long now = SystemClock.elapsedRealtime();
    if (busy.get() || now - submittedAt < INTERVAL_MS) return;
    submittedAt = now;
    if (!busy.compareAndSet(false, true)) return;
    Image cameraImage;
    try {
      cameraImage = frame.acquireCameraImage();
    } catch (Exception unavailable) {
      lastError = unavailable.getClass().getSimpleName();
      busy.set(false);
      return;
    }
    try {
      labeler.process(InputImage.fromMediaImage(cameraImage, rotationDegrees))
          .addOnSuccessListener(labels -> {
            float score = 0;
            for (com.google.mlkit.vision.label.ImageLabel label : labels)
              if (label.getIndex() == 88 || "Stairs".equalsIgnoreCase(label.getText()))
                score = Math.max(score, label.getConfidence());
            confidence = score;
            resultAt = SystemClock.elapsedRealtime();
            lastError = null;
          })
          .addOnFailureListener(error -> {
            confidence = 0;
            resultAt = 0;
            lastError = error.getClass().getSimpleName();
          })
          .addOnCompleteListener(done -> { cameraImage.close(); busy.set(false); });
    } catch (RuntimeException error) {
      lastError = error.getClass().getSimpleName();
      cameraImage.close();
      busy.set(false);
    }
  }

  boolean seesStairs() {
    return confidence >= STAIR_CONFIDENCE && SystemClock.elapsedRealtime() - resultAt <= 1800;
  }

  String debugSummary() {
    long age = SystemClock.elapsedRealtime() - resultAt;
    if (resultAt == 0 || age > 3000)
      return "계단 모델: 결과 대기" + (lastError == null ? "" : " (" + lastError + ")");
    return String.format(java.util.Locale.KOREA, "계단 모델: 점수 %.2f · %s",
        confidence, seesStairs() ? "인식" : "미인식");
  }

  @Override public void close() { labeler.close(); }
}
