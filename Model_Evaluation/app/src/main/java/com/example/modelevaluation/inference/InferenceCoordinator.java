package com.example.modelevaluation.inference;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.modelevaluation.model.ModelReport;

import ai.onnxruntime.OrtEnvironment;

public final class InferenceCoordinator implements AutoCloseable {
    private final OrtEnvironment environment;
    private Places365Classifier places;
    private YoloSegmenter yolo;
    private String placesLoadError;
    private String yoloLoadError;

    public InferenceCoordinator(Context context) {
        environment = OrtEnvironment.getEnvironment();
        if (AssetUtils.exists(context, Places365Classifier.MODEL_ASSET)) {
            try { places = new Places365Classifier(context, environment); }
            catch (Exception exception) { placesLoadError = message(exception); }
        } else {
            placesLoadError = "모델 없음 · tools/prepare_models.py를 실행하세요";
        }
        if (AssetUtils.exists(context, YoloSegmenter.MODEL_ASSET)) {
            try { yolo = new YoloSegmenter(context, environment); }
            catch (Exception exception) { yoloLoadError = message(exception); }
        } else {
            yoloLoadError = "모델 없음 · tools/prepare_models.py를 실행하세요";
        }
    }

    public ModelReport runPlaces(Bitmap bitmap) {
        return places == null ? ModelReport.error(placesLoadError) : places.run(bitmap);
    }

    public ModelReport runYolo(Bitmap bitmap, float confidence) {
        return yolo == null ? ModelReport.error(yoloLoadError) : yolo.run(bitmap, confidence);
    }

    public boolean isPlacesReady() { return places != null; }
    public boolean isYoloReady() { return yolo != null; }

    private static String message(Exception exception) {
        String value = exception.getMessage();
        return "모델 로드 실패 · " + (value == null ? exception.getClass().getSimpleName() : value);
    }

    @Override public void close() {
        try { if (places != null) places.close(); } catch (Exception ignored) { }
        try { if (yolo != null) yolo.close(); } catch (Exception ignored) { }
    }
}
