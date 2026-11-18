package com.example.arnavigation.entrance;

import android.graphics.Bitmap;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import java.nio.FloatBuffer;
import java.util.Collections;

/** CPU baseline. Own on a serial worker; no camera or Activity ownership. */
public final class SegformerSegmenter implements AutoCloseable {
    public static final int INPUT_SIZE = 512;
    private static final float[] MEAN = {.485f, .456f, .406f};
    private static final float[] STD = {.229f, .224f, .225f};
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;

    /** Model contract: one float32 pixel_values[1,3,512,512], logits[1,150,H,W]. */
    public SegformerSegmenter(byte[] model) throws OrtException {
        environment = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            session = environment.createSession(model, options);
        }
        try {
            if (session.getInputNames().size() != 1 || !session.getOutputNames().contains("logits"))
                throw new IllegalArgumentException("Expected SegFormer pixel_values/logits export");
            inputName = session.getInputNames().iterator().next();
            TensorInfo input = (TensorInfo) session.getInputInfo().get(inputName).getInfo();
            if (!java.util.Arrays.equals(input.getShape(), new long[]{1, 3, INPUT_SIZE, INPUT_SIZE}))
                throw new IllegalArgumentException("Expected fixed 512 RGB NCHW input");
        } catch (RuntimeException | OrtException error) {
            session.close();
            throw error;
        }
    }

    /** Caller supplies upright full-frame bitmap, using the same view policy as reference masks. */
    public SegmentationMask run(Bitmap bitmap) throws OrtException {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        int pixelsCount = INPUT_SIZE * INPUT_SIZE;
        int[] pixels = new int[pixelsCount];
        try { resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE); }
        finally { if (resized != bitmap) resized.recycle(); }
        float[] values = new float[3 * pixelsCount];
        for (int c = 0; c < 3; c++) for (int i = 0; i < pixelsCount; i++) {
            float channel = ((pixels[i] >> (16 - 8 * c)) & 255) / 255f;
            values[c * pixelsCount + i] = (channel - MEAN[c]) / STD[c];
        }
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(values),
                    new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
                OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            Object value = result.get("logits").orElseThrow(
                    () -> new IllegalArgumentException("Missing logits")).getValue();
            if (!(value instanceof float[][][][] output) || output.length != 1)
                throw new IllegalArgumentException("Expected float32 NCHW logits");
            return SegformerLogits.decode(output[0], 128, 128, .35f);
        }
    }

    @Override public void close() throws OrtException { session.close(); }
}
