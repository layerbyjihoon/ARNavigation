package com.example.modelevaluation;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.example.modelevaluation.databinding.ActivityMainBinding;
import com.example.modelevaluation.inference.BitmapPreprocessor;
import com.example.modelevaluation.inference.InferenceCoordinator;
import com.example.modelevaluation.model.Classification;
import com.example.modelevaluation.model.Detection;
import com.example.modelevaluation.model.ModelReport;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();
    private InferenceCoordinator coordinator;
    private volatile boolean placesEnabled = true;
    private volatile boolean yoloEnabled = true;
    private volatile boolean paused;
    private volatile float confidenceThreshold = 0.35f;
    private long frameCount;
    private long fpsWindowStarted;
    private long fpsWindowFrames;
    private final Map<String, Double> smoothedFps = new HashMap<>();

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                showPermissionPanel(!granted);
                if (granted) initializeAndStart();
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        configureControls();
        if (hasCameraPermission()) initializeAndStart();
        else showPermissionPanel(true);
    }

    private void configureControls() {
        binding.placesSwitch.setOnCheckedChangeListener((button, checked) -> {
            placesEnabled = checked;
            if (!checked) binding.placesResult.setText("비활성화됨");
        });
        binding.yoloSwitch.setOnCheckedChangeListener((button, checked) -> {
            yoloEnabled = checked;
            if (!checked) {
                binding.yoloResult.setText("비활성화됨");
                binding.overlay.clear();
            }
        });
        binding.confidenceSlider.addOnChangeListener((slider, value, fromUser) -> {
            confidenceThreshold = value;
            binding.confidenceLabel.setText(String.format(Locale.KOREA, "신뢰도 기준 %.0f%%", value * 100f));
        });
        binding.pauseButton.setOnClickListener(view -> {
            paused = !paused;
            binding.pauseButton.setText(paused ? R.string.resume : R.string.pause);
            if (paused) binding.overlay.clear();
        });
        binding.permissionButton.setOnClickListener(view ->
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA));
    }

    private void initializeAndStart() {
        showPermissionPanel(false);
        binding.placesResult.setText(R.string.waiting);
        binding.yoloResult.setText(R.string.waiting);
        inferenceExecutor.execute(() -> {
            coordinator = new InferenceCoordinator(getApplicationContext());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!coordinator.isPlacesReady()) {
                    ModelReport error = coordinator.runPlaces(null);
                    binding.placesResult.setText(error.error());
                    binding.placesSwitch.setChecked(false);
                    binding.placesSwitch.setEnabled(false);
                }
                if (!coordinator.isYoloReady()) {
                    ModelReport error = coordinator.runYolo(null, confidenceThreshold);
                    binding.yoloResult.setText(error.error());
                    binding.yoloSwitch.setChecked(false);
                    binding.yoloSwitch.setEnabled(false);
                }
                startCamera();
            });
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.preview.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                analysis.setAnalyzer(inferenceExecutor, this::analyzeFrame);
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                fpsWindowStarted = SystemClock.elapsedRealtime();
            } catch (Exception exception) {
                binding.sessionMetrics.setText(getString(R.string.camera_start_failed,
                        safeMessage(exception)));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(@NonNull ImageProxy image) {
        Bitmap bitmap = null;
        try {
            updateCameraFps();
            if (paused || coordinator == null) return;
            bitmap = BitmapPreprocessor.fromRgba8888(image);
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (placesEnabled && coordinator.isPlacesReady()) {
                ModelReport report = coordinator.runPlaces(bitmap);
                runOnUiThread(() -> showPlaces(report));
            }
            if (yoloEnabled && coordinator.isYoloReady()) {
                ModelReport report = coordinator.runYolo(bitmap, confidenceThreshold);
                runOnUiThread(() -> showYolo(report, width, height));
            }
        } catch (Exception exception) {
            String error = "프레임 처리 실패: " + safeMessage(exception);
            runOnUiThread(() -> binding.sessionMetrics.setText(error));
        } finally {
            if (bitmap != null) bitmap.recycle();
            image.close();
        }
    }

    private void updateCameraFps() {
        frameCount++;
        fpsWindowFrames++;
        long now = SystemClock.elapsedRealtime();
        long elapsed = now - fpsWindowStarted;
        if (elapsed >= 1000L) {
            double fps = fpsWindowFrames * 1000d / elapsed;
            fpsWindowStarted = now;
            fpsWindowFrames = 0L;
            runOnUiThread(() -> binding.sessionMetrics.setText(String.format(Locale.US,
                    "Camera %.1f FPS · Frame %,d · CPU", fps, frameCount)));
        }
    }

    private void showPlaces(ModelReport report) {
        if (report.isError()) {
            binding.placesResult.setText(report.error());
            return;
        }
        List<Classification> values = report.classifications();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(3, values.size()); i++) {
            Classification value = values.get(i);
            if (i > 0) text.append("  ·  ");
            text.append(value.label()).append(' ')
                    .append(String.format(Locale.US, "%.0f%%", value.confidence() * 100f));
        }
        binding.placesResult.setText(text.length() == 0 ? getString(R.string.no_result) : text);
        binding.placesMetrics.setText(formatMetrics("places", report.latencyMs()));
    }

    private void showYolo(ModelReport report, int width, int height) {
        if (report.isError()) {
            binding.yoloResult.setText(report.error());
            binding.overlay.clear();
            return;
        }
        List<Detection> values = report.detections();
        Map<String, Integer> counts = new HashMap<>();
        for (Detection value : values) counts.merge(value.label(), 1, Integer::sum);
        if (counts.isEmpty()) binding.yoloResult.setText("감지 객체 없음");
        else {
            String text = counts.entrySet().stream()
                    .map(entry -> entry.getKey() + " " + entry.getValue())
                    .reduce((first, second) -> first + "  ·  " + second).orElse("");
            binding.yoloResult.setText(text);
        }
        binding.yoloMetrics.setText(formatMetrics("yolo", report.latencyMs()));
        binding.overlay.setDetections(values, width, height);
    }

    private String formatMetrics(String key, long latencyMs) {
        double instantFps = latencyMs <= 0 ? 0d : 1000d / latencyMs;
        double fps = smoothedFps.compute(key,
                (ignored, old) -> old == null ? instantFps : old * 0.8d + instantFps * 0.2d);
        return String.format(Locale.US, "Latency %d ms · %.1f FPS", latencyMs, fps);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionPanel(boolean show) {
        binding.permissionPanel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    @Override protected void onDestroy() {
        InferenceCoordinator current = coordinator;
        coordinator = null;
        if (current != null) inferenceExecutor.execute(current::close);
        inferenceExecutor.shutdown();
        super.onDestroy();
    }
}
