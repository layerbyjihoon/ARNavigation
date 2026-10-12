package com.example.modelevaluation.inference;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import com.example.modelevaluation.model.Classification;
import com.example.modelevaluation.model.ModelReport;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public final class Places365Classifier implements AutoCloseable {
    public static final String MODEL_ASSET = "resnet18_places365.onnx";
    private static final int INPUT_SIZE = 224;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;
    private final List<String> labels;

    public Places365Classifier(Context context, OrtEnvironment environment) throws Exception {
        this.environment = environment;
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(Math.max(1, Math.min(4,
                Runtime.getRuntime().availableProcessors() - 1)));
        session = environment.createSession(AssetUtils.readBytes(context, MODEL_ASSET), options);
        inputName = session.getInputNames().iterator().next();
        labels = AssetUtils.readLabels(context, "categories_places365.txt");
        if (labels.size() != 365) {
            throw new IllegalStateException("Places365 label count must be 365, got " + labels.size());
        }
    }

    public ModelReport run(Bitmap bitmap) {
        long started = SystemClock.elapsedRealtime();
        try {
            float[] values = BitmapPreprocessor.placesTensor(bitmap, INPUT_SIZE);
            try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(values),
                    new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
                 OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
                float[] logits = ((float[][]) result.get(0).getValue())[0];
                float[] probabilities = softmax(logits);
                List<Classification> ranked = new ArrayList<>(probabilities.length);
                for (int i = 0; i < probabilities.length && i < labels.size(); i++) {
                    ranked.add(new Classification(labels.get(i), probabilities[i]));
                }
                ranked.sort(Comparator.comparing(Classification::confidence).reversed());
                return ModelReport.classifications(ranked.subList(0, Math.min(5, ranked.size())),
                        SystemClock.elapsedRealtime() - started);
            }
        } catch (Exception exception) {
            return ModelReport.error("Places365 추론 실패: " + concise(exception));
        }
    }

    static float[] softmax(float[] logits) {
        float max = -Float.MAX_VALUE;
        for (float value : logits) max = Math.max(max, value);
        double sum = 0d;
        float[] probabilities = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probabilities[i] = (float) Math.exp(logits[i] - max);
            sum += probabilities[i];
        }
        for (int i = 0; i < probabilities.length; i++) probabilities[i] /= (float) sum;
        return probabilities;
    }

    private static String concise(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    @Override public void close() throws Exception { session.close(); }
}
