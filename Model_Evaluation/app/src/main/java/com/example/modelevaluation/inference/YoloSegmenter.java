package com.example.modelevaluation.inference;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;

import com.example.modelevaluation.model.Detection;
import com.example.modelevaluation.model.ModelReport;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public final class YoloSegmenter implements AutoCloseable {
    public static final String MODEL_ASSET = "doorway_seg_yolo26n.onnx";
    private static final int INPUT_SIZE = 640;
    private static final int MASK_COEFFICIENTS = 32;
    private static final String[] LABELS = {"doorway", "door", "people", "window", "mirror"};
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;

    public YoloSegmenter(Context context, OrtEnvironment environment) throws Exception {
        this.environment = environment;
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(Math.max(1, Math.min(4,
                Runtime.getRuntime().availableProcessors() - 1)));
        session = environment.createSession(AssetUtils.readBytes(context, MODEL_ASSET), options);
        inputName = session.getInputNames().iterator().next();
    }

    public ModelReport run(Bitmap bitmap, float confidenceThreshold) {
        long started = SystemClock.elapsedRealtime();
        try {
            BitmapPreprocessor.LetterboxResult prepared =
                    BitmapPreprocessor.yoloTensor(bitmap, INPUT_SIZE);
            try (OnnxTensor tensor = OnnxTensor.createTensor(environment,
                    FloatBuffer.wrap(prepared.tensor()), new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
                 OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
                Object value = result.get(0).getValue();
                if (!(value instanceof float[][][] output)) {
                    return ModelReport.error("지원하지 않는 YOLO 출력 타입: " + value.getClass());
                }
                List<Detection> detections = parseOutput(output[0], prepared, confidenceThreshold);
                return ModelReport.detections(detections, SystemClock.elapsedRealtime() - started);
            }
        } catch (Exception exception) {
            return ModelReport.error("YOLO 추론 실패: " + concise(exception));
        }
    }

    static List<Detection> parseOutput(float[][] matrix,
                                       BitmapPreprocessor.LetterboxResult letterbox,
                                       float threshold) {
        if (matrix.length == 0 || matrix[0].length == 0) return List.of();
        boolean transposed = matrix.length < matrix[0].length;
        int rows = transposed ? matrix[0].length : matrix.length;
        int attributes = transposed ? matrix.length : matrix[0].length;
        List<Detection> candidates = new ArrayList<>();

        // YOLO26 NMS-free export: [N, 6 + mask coefficients], xyxy/conf/class.
        boolean endToEnd = attributes == 6 + MASK_COEFFICIENTS || attributes == 6;
        int rawClasses = Math.max(0, attributes - 4 - MASK_COEFFICIENTS);
        for (int row = 0; row < rows; row++) {
            float confidence;
            int classId;
            float x1;
            float y1;
            float x2;
            float y2;
            if (endToEnd) {
                confidence = at(matrix, transposed, row, 4);
                classId = Math.round(at(matrix, transposed, row, 5));
                x1 = at(matrix, transposed, row, 0);
                y1 = at(matrix, transposed, row, 1);
                x2 = at(matrix, transposed, row, 2);
                y2 = at(matrix, transposed, row, 3);
            } else if (rawClasses > 0) {
                classId = 0;
                confidence = at(matrix, transposed, row, 4);
                for (int classIndex = 1; classIndex < rawClasses; classIndex++) {
                    float score = at(matrix, transposed, row, 4 + classIndex);
                    if (score > confidence) {
                        confidence = score;
                        classId = classIndex;
                    }
                }
                float centerX = at(matrix, transposed, row, 0);
                float centerY = at(matrix, transposed, row, 1);
                float width = at(matrix, transposed, row, 2);
                float height = at(matrix, transposed, row, 3);
                x1 = centerX - width / 2f;
                y1 = centerY - height / 2f;
                x2 = centerX + width / 2f;
                y2 = centerY + height / 2f;
            } else {
                continue;
            }
            if (classId < 0 || classId >= LABELS.length) continue;
            // Mirror uses a deliberately low threshold because it is an internal suppressor.
            if (confidence < (classId == 4 ? 0.10f : threshold)) continue;
            RectF normalized = unletterbox(x1, y1, x2, y2, letterbox);
            if (normalized.width() <= 0f || normalized.height() <= 0f) continue;
            candidates.add(new Detection(classId, LABELS[classId], confidence, normalized));
        }
        candidates.sort(Comparator.comparing(Detection::confidence).reversed());
        return nonMaxSuppression(finalizeDetections(candidates), 0.45f, 30);
    }

    private static float at(float[][] matrix, boolean transposed, int row, int attribute) {
        return transposed ? matrix[attribute][row] : matrix[row][attribute];
    }

    private static RectF unletterbox(float x1, float y1, float x2, float y2,
                                     BitmapPreprocessor.LetterboxResult value) {
        float left = (x1 - value.padX()) / value.scale() / value.sourceWidth();
        float top = (y1 - value.padY()) / value.scale() / value.sourceHeight();
        float right = (x2 - value.padX()) / value.scale() / value.sourceWidth();
        float bottom = (y2 - value.padY()) / value.scale() / value.sourceHeight();
        return new RectF(clamp(left), clamp(top), clamp(right), clamp(bottom));
    }

    static List<Detection> nonMaxSuppression(List<Detection> sorted, float iouThreshold, int limit) {
        List<Detection> kept = new ArrayList<>();
        for (Detection candidate : sorted) {
            boolean overlaps = false;
            for (Detection existing : kept) {
                if (candidate.classId() == existing.classId()
                        && intersectionOverUnion(candidate.normalizedBox(), existing.normalizedBox()) > iouThreshold) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) kept.add(candidate);
            if (kept.size() >= limit) break;
        }
        return kept;
    }

    // Port of the model author's mirror_suppress.finalize() post-processing contract.
    private static List<Detection> finalizeDetections(List<Detection> candidates) {
        List<Detection> mirrors = new ArrayList<>();
        for (Detection candidate : candidates) {
            if (candidate.classId() == 4) mirrors.add(candidate);
        }
        List<Detection> visible = new ArrayList<>();
        for (Detection candidate : candidates) {
            if (candidate.classId() == 4) continue;
            boolean structural = candidate.classId() == 0 || candidate.classId() == 1
                    || candidate.classId() == 3;
            boolean reflected = false;
            if (structural) {
                for (Detection mirror : mirrors) {
                    if (containment(candidate.normalizedBox(), mirror.normalizedBox()) >= 0.5f) {
                        reflected = true;
                        break;
                    }
                }
            }
            if (!reflected) visible.add(candidate);
        }

        List<Detection> resolved = new ArrayList<>();
        for (Detection candidate : visible) {
            if (candidate.classId() != 0 && candidate.classId() != 1) {
                resolved.add(candidate);
                continue;
            }
            boolean losesPortalConflict = false;
            for (Detection other : visible) {
                boolean oppositePortal = candidate.classId() + other.classId() == 1;
                boolean otherWins = other.confidence() > candidate.confidence()
                        || (other.confidence() == candidate.confidence() && candidate.classId() == 0);
                boolean overlaps = intersectionOverUnion(candidate.normalizedBox(), other.normalizedBox()) >= 0.4f
                        || containment(candidate.normalizedBox(), other.normalizedBox()) >= 0.6f
                        || containment(other.normalizedBox(), candidate.normalizedBox()) >= 0.6f;
                if (other != candidate && oppositePortal && otherWins && overlaps) {
                    losesPortalConflict = true;
                    break;
                }
            }
            if (!losesPortalConflict) resolved.add(candidate);
        }
        return resolved;
    }

    private static float containment(RectF box, RectF container) {
        float intersection = Math.max(0f, Math.min(box.right, container.right) - Math.max(box.left, container.left))
                * Math.max(0f, Math.min(box.bottom, container.bottom) - Math.max(box.top, container.top));
        float area = box.width() * box.height();
        return area <= 0f ? 0f : intersection / area;
    }

    static float intersectionOverUnion(RectF first, RectF second) {
        float intersection = Math.max(0f, Math.min(first.right, second.right) - Math.max(first.left, second.left))
                * Math.max(0f, Math.min(first.bottom, second.bottom) - Math.max(first.top, second.top));
        float union = first.width() * first.height() + second.width() * second.height() - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static String concise(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    @Override public void close() throws Exception { session.close(); }
}
