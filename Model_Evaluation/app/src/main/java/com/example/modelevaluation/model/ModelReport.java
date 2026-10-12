package com.example.modelevaluation.model;

import java.util.Collections;
import java.util.List;

public final class ModelReport {
    private final List<Classification> classifications;
    private final List<Detection> detections;
    private final long latencyMs;
    private final String error;

    private ModelReport(List<Classification> classifications, List<Detection> detections,
                        long latencyMs, String error) {
        this.classifications = classifications;
        this.detections = detections;
        this.latencyMs = latencyMs;
        this.error = error;
    }

    public static ModelReport classifications(List<Classification> values, long latencyMs) {
        return new ModelReport(List.copyOf(values), Collections.emptyList(), latencyMs, null);
    }

    public static ModelReport detections(List<Detection> values, long latencyMs) {
        return new ModelReport(Collections.emptyList(), List.copyOf(values), latencyMs, null);
    }

    public static ModelReport error(String message) {
        return new ModelReport(Collections.emptyList(), Collections.emptyList(), 0L, message);
    }

    public List<Classification> classifications() { return classifications; }
    public List<Detection> detections() { return detections; }
    public long latencyMs() { return latencyMs; }
    public String error() { return error; }
    public boolean isError() { return error != null; }
}
