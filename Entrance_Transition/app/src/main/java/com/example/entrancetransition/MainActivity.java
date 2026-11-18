package com.example.entrancetransition;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.camera.view.transform.OutputTransform;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Standalone environment display for Cultural Center. Never launches navigation. */
@androidx.annotation.OptIn(markerClass = androidx.camera.view.TransformExperimental.class)
public final class MainActivity extends AppCompatActivity {
    private final EnvironmentTransitionController controller = new EnvironmentTransitionController();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TransitionCameraSession camera;
    private TextView mode;
    private TextView message;
    private Button permission;
    private PreviewView preview;
    private SegmentationOverlay segmentation;
    private boolean foreground;
    private boolean ready;
    private boolean failed;
    private boolean denied;
    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                denied = !granted;
                if (foreground) startSession();
            });
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!foreground) return;
            segmentation.clearIfExpired(SystemClock.elapsedRealtime());
            if (ready && !failed) render();
            handler.postDelayed(this, 250);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        mode = findViewById(R.id.mode);
        message = findViewById(R.id.message);
        permission = findViewById(R.id.permission);
        preview = findViewById(R.id.preview);
        segmentation = findViewById(R.id.segmentation);
        findViewById(R.id.rescan).setOnClickListener(view -> startSession());
        permission.setOnClickListener(view -> {
            if (denied && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } else cameraPermission.launch(Manifest.permission.CAMERA);
        });
    }

    @Override protected void onStart() {
        super.onStart();
        foreground = true;
        startSession();
        handler.post(ticker);
    }

    private void startSession() {
        stopSession();
        if (!foreground) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            message.setText(R.string.permission_hint);
            permission.setVisibility(View.VISIBLE);
            permission.setText(denied && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                    ? R.string.open_settings : R.string.permission_button);
            return;
        }
        permission.setVisibility(View.GONE);
        message.setText(R.string.loading);
        camera = new TransitionCameraSession(this, this, preview, new TransitionCameraSession.Listener() {
            @Override public void onReady() {
                ready = true;
                message.setText(R.string.scan_hint);
            }
            @Override public void onResult(MunhwaClassifier.Result result, long capturedMs,
                    OutputTransform source, int frameWidth, int frameHeight, RectF sourceViewport) {
                failed = false;
                segmentation.show(result.mask, source, preview, frameWidth, frameHeight,
                        sourceViewport, capturedMs);
                controller.observe(result.inside, result.valid,
                        capturedMs, SystemClock.elapsedRealtime());
                render();
            }
            @Override public void onError(int messageResource) {
                failed = true;
                segmentation.clear();
                controller.reset();
                mode.setText(R.string.mode_unknown);
                message.setText(messageResource);
            }
        });
        camera.start();
    }

    private void render() {
        long now = SystemClock.elapsedRealtime();
        controller.tick(now);
        switch (controller.mode()) {
            case INDOOR -> mode.setText(R.string.mode_indoor);
            case OUTDOOR -> mode.setText(R.string.mode_outdoor);
            default -> mode.setText(R.string.mode_unknown);
        }
        if (controller.isAmbiguous()) message.setText(R.string.ambiguous_hint);
        else if (controller.mode() == EnvironmentTransitionController.Mode.INDOOR)
            message.setText(R.string.indoor_hint);
        else if (controller.mode() == EnvironmentTransitionController.Mode.OUTDOOR)
            message.setText(R.string.outdoor_hint);
        else message.setText(R.string.checking_hint);
    }

    private void stopSession() {
        if (camera != null) camera.close();
        camera = null;
        ready = false;
        failed = false;
        controller.reset();
        segmentation.clear();
        mode.setText(R.string.mode_unknown);
    }

    @Override protected void onStop() {
        foreground = false;
        handler.removeCallbacks(ticker);
        stopSession();
        super.onStop();
    }
}
