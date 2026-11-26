package com.google.ar.core.examples.java.helloar;

import com.google.ar.core.examples.java.helloar.segmentation.SegmentationResult;

/** Confirms outdoor ground near the camera, with outdoor context farther away. */
final class OutdoorExitGate {
  private static final int REQUIRED_FRAMES = 3;
  private static final long MAX_FRAME_AGE_NS = 2_000_000_000L;
  private static final long MAX_FRAME_GAP_NS = 2_000_000_000L;
  private static final float MIN_CLASS_CONFIDENCE = 0.35f;
  private long lastTimestamp;
  private int consecutive;

  boolean observe(SegmentationResult observation, long now) {
    long timestamp = observation.frame.timestamp;
    if (timestamp <= lastTimestamp || timestamp > now || now - timestamp > MAX_FRAME_AGE_NS)
      return false;
    if (timestamp - lastTimestamp > MAX_FRAME_GAP_NS) consecutive = 0;
    lastTimestamp = timestamp;
    consecutive = isOutdoor(observation) ? consecutive + 1 : 0;
    return consecutive == REQUIRED_FRAMES;
  }

  void reset() {
    lastTimestamp = 0;
    consecutive = 0;
  }

  private static boolean isOutdoor(SegmentationResult result) {
    int w = result.width, h = result.height;
    int foregroundStart = h * 2 / 3;
    int left = w / 8, right = w * 7 / 8;
    int foregroundArea = (h - foregroundStart) * (right - left);
    int contextArea = foregroundStart * w;
    int ground = 0, indoorFloor = 0, outdoorContext = 0;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int index = y * w + x;
        if (result.confidence[index] < MIN_CLASS_CONFIDENCE) continue;
        int label = result.labels[index] & 0xff;
        if (y < foregroundStart) {
          if (label == 1 || label == 2 || label == 4 || label == 9) outdoorContext++;
        } else if (x >= left && x < right) {
          switch (label) {
            case 6, 9, 11, 13, 46, 94 -> ground++; // Road, grass, sidewalk, earth, sand, land.
            case 3, 28 -> indoorFloor++; // Floor or rug.
            default -> { }
          }
        }
      }
    }
    return foregroundArea > 0 && contextArea > 0 && ground >= foregroundArea * .45
        && indoorFloor <= foregroundArea * .35 && outdoorContext >= contextArea * .15;
  }
}
