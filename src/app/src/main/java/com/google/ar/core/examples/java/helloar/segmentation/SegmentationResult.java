package com.google.ar.core.examples.java.helloar.segmentation;

public final class SegmentationResult {
  public final FrameSnapshot frame;
  public final int width,height;
  public final byte[] labels;
  public final float[] confidence;
  public final String[] names;
  public final double inferenceMillis;
  public SegmentationResult(FrameSnapshot frame,int width,int height,byte[] labels,float[] confidence,String[] names,double ms) {
    this.frame=frame;this.width=width;this.height=height;this.labels=labels;this.confidence=confidence;
    this.names=names;inferenceMillis=ms;
  }
}
