package com.example.arnavigation.ui.ar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.arnavigation.R;
import com.example.arnavigation.camera.CameraPreviewController;
import com.example.arnavigation.camera.CameraStatus;
import com.example.arnavigation.navigation.NavigationAction;
import com.example.arnavigation.navigation.NavigationStage;
import com.example.arnavigation.navigation.NavigationUiState;
import com.example.arnavigation.navigation.NavigationViewModel;
import com.example.arnavigation.ui.PermissionAware;
import com.example.arnavigation.ui.PermissionState;

/** CameraX preview with a lifecycle-safe navigation direction overlay. */
public final class ArNavigationFragment extends Fragment implements PermissionAware {
    private static final long MIN_RENDER_INTERVAL_MILLIS = 100L;

    private final Handler renderHandler = new Handler(Looper.getMainLooper());
    private ArNavigationHost host;
    private NavigationViewModel viewModel;
    private CameraPreviewController cameraController;
    private ArUiRenderer renderer;
    private PreviewView cameraPreview;
    private Button cameraPermissionButton;
    private NavigationUiState latestState;
    private NavigationUiState pendingRenderState;
    private boolean automaticCameraRequestMade;
    private boolean viewResumed;
    private boolean handingOff;
    private com.example.arnavigation.journey.EntryVisionWorker vision;
    private TextView transitionStatus;
    private com.example.arnavigation.ui.MainActivity activityHost() {
        return (com.example.arnavigation.ui.MainActivity) requireActivity();
    }
    private long lastRenderElapsedMillis = Long.MIN_VALUE;

    private final Runnable pendingRender = () -> {
        if (renderer == null || pendingRenderState == null) return;
        NavigationUiState state = pendingRenderState;
        pendingRenderState = null;
        lastRenderElapsedMillis = SystemClock.elapsedRealtime();
        renderRouteState(state);
    };

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof ArNavigationHost)) {
            throw new IllegalStateException("Host activity must implement ArNavigationHost");
        }
        host = (ArNavigationHost) context;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ar_navigation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Restored fragments attach during Activity.super.onCreate, before the host session exists.
        viewModel = host.getNavigationViewModel();
        cameraPreview = view.findViewById(R.id.camera_preview);
        cameraPermissionButton = view.findViewById(R.id.camera_permission_button);
        transitionStatus = view.findViewById(R.id.final_transition_status);
        vision = new com.example.arnavigation.journey.EntryVisionWorker(requireContext(),
            new com.example.arnavigation.journey.EntryVisionWorker.Listener() {
                public void onResult(String session, double inside, double outside, boolean valid, long capturedMs) {
                    if (!viewResumed || handingOff || !isAdded() || transitionStatus == null) return;
                    com.example.arnavigation.journey.JourneyState journey = activityHost().getJourney();
                    if (session == null || !session.equals(journey.destinationId()) || !journey.gate.isChecking()) return;
                    long now = SystemClock.elapsedRealtime();
                    long age = now - capturedMs;
                    if (age < 0 || age > 2000) return;
                    transitionStatus.setText(valid ? getString(R.string.final_checking, inside)
                        : getString(R.string.final_check_unknown));
                    if (journey.gate.observe(inside, outside, valid, capturedMs, now)) handoff();
                }
                public void onError() {
                    if (viewResumed && !handingOff && transitionStatus != null) {
                        activityHost().getJourney().gate.resetObservations();
                        transitionStatus.setText(R.string.final_check_error);
                    }
                }
            });
        transitionStatus.setOnClickListener(button -> {
            if (!handingOff && vision != null && activityHost().getJourney().gate.isChecking()) vision.retry();
        });
        cameraController = new CameraPreviewController(requireContext(), vision, vision.executor);
        renderer = new ArUiRenderer(
                new ViewRenderTarget(view), new NavigationInstructionFormatter(15.0d));

        view.findViewById(R.id.show_map_button).setOnClickListener(button -> host.openMapTab());
        view.findViewById(R.id.end_navigation_button).setOnClickListener(button -> {
            viewModel.dispatch(new NavigationAction.StopNavigation());
            host.openMapTab();
        });
        view.findViewById(R.id.change_route_button).setOnClickListener(button -> {
            if (getParentFragmentManager().findFragmentByTag("change_route") == null)
                new com.example.arnavigation.ui.map.DestinationSearchDialog()
                    .show(getParentFragmentManager(), "change_route");
        });
        cameraPermissionButton.setOnClickListener(button -> requestCameraOrOpenSettings());
        viewModel.getState().observe(getViewLifecycleOwner(), this::handleNavigationState);
    }

    private void handleNavigationState(NavigationUiState state) {
        latestState = state;
        if (state.getStage() == NavigationStage.ROUTE_READY && viewResumed && !isHidden()) {
            viewModel.dispatch(new NavigationAction.StartNavigation());
            return;
        }
        updateJourney(state);
        renderThrottled(state);
        syncCamera(state);
    }

    private void updateJourney(NavigationUiState state) {
        if (handingOff || transitionStatus == null || !isAdded()) return;
        com.example.arnavigation.journey.JourneyState journey = activityHost().getJourney();
        boolean eligible = journey.route() != null && state.getDestination() != null
            && state.getDestination().getId().equals(journey.destinationId());
        transitionStatus.setVisibility(eligible ? View.VISIBLE : View.GONE);
        if (!eligible) { vision.suspend(); return; }
        if (!journey.gate.isChecking()) transitionStatus.setText(R.string.final_check_wait);
        com.example.arnavigation.model.LocationSample sample = state.getLocation();
        if (viewResumed && state.getStage() == NavigationStage.NAVIGATING && sample != null
                && state.getLocationStatus() == com.example.arnavigation.location.LocationStatus.AVAILABLE) {
            double lat1 = Math.toRadians(sample.getPoint().getLatitude());
            double lat2 = Math.toRadians(state.getDestination().getPoint().getLatitude());
            double dlat = lat2-lat1;
            double dlon = Math.toRadians(state.getDestination().getPoint().getLongitude()
                - sample.getPoint().getLongitude());
            double a = Math.sin(dlat/2)*Math.sin(dlat/2)
                + Math.cos(lat1)*Math.cos(lat2)*Math.sin(dlon/2)*Math.sin(dlon/2);
            double distance = 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
            journey.gate.updateGps(distance, sample.getAccuracyMeters(),
                (SystemClock.elapsedRealtimeNanos() - sample.getElapsedRealtimeNanos()) / 1000000);
        }
        if (viewResumed && journey.gate.isChecking()) vision.enable(journey.destinationId());
    }

    private void handoff() {
        handingOff = true;
        transitionStatus.setText(R.string.final_handoff);
        renderHandler.removeCallbacks(pendingRender);
        pendingRenderState = null;
        viewModel.dispatch(new NavigationAction.StopNavigation());
        CameraPreviewController closingCamera = cameraController;
        // Remove the analyzer first; then drain inference; then await actual hardware camera closure.
        closingCamera.unbind();
        com.example.arnavigation.ui.MainActivity activity = activityHost();
        String destinationId = activity.getJourney().destinationId();
        vision.close(() -> closingCamera.release(success ->
            activity.completeIndoorHandoff(destinationId, success)));

    }

    private void renderThrottled(NavigationUiState state) {
        if (renderer == null) return;
        long now = SystemClock.elapsedRealtime();
        long elapsed = lastRenderElapsedMillis == Long.MIN_VALUE
                ? Long.MAX_VALUE : now - lastRenderElapsedMillis;
        if (elapsed >= MIN_RENDER_INTERVAL_MILLIS) {
            renderHandler.removeCallbacks(pendingRender);
            pendingRenderState = null;
            lastRenderElapsedMillis = now;
            renderRouteState(state);
        } else {
            pendingRenderState = state;
            renderHandler.removeCallbacks(pendingRender);
            renderHandler.postDelayed(pendingRender, MIN_RENDER_INTERVAL_MILLIS - elapsed);
        }
    }

    private void renderRouteState(NavigationUiState state) {
        if (getView() != null) {
            ArrowOverlayView routeOverlay = getView().findViewById(R.id.direction_arrow);
            routeOverlay.setRoute(state);
        }
        renderer.render(state);
    }

    private void syncCamera(NavigationUiState state) {
        if (state == null || host == null || handingOff) return;
        boolean needsCamera = state.getStage() == NavigationStage.NAVIGATING;
        if (!viewResumed || !needsCamera) {
            unbindCamera();
            updatePermissionAction(false);
            return;
        }
        PermissionState permission = host.getCameraPermissionState();
        if (permission == PermissionState.GRANTED) {
            updatePermissionAction(false);
            cameraController.bind(
                    getViewLifecycleOwner(),
                    cameraPreview,
                    cameraState -> {
                        if (renderer == null || viewModel == null) return;
                        renderer.setCameraStatus(cameraState.getStatus());
                        viewModel.dispatch(new NavigationAction.SetCameraAvailable(
                                cameraState.isPreviewAvailable()));
                    });
        } else {
            unbindCamera();
            if (permission == PermissionState.REQUESTABLE && !automaticCameraRequestMade) {
                automaticCameraRequestMade = true;
                host.requestCameraPermission();
            } else {
                renderer.setCameraStatus(CameraStatus.PERMISSION_DENIED);
                viewModel.dispatch(new NavigationAction.SetCameraAvailable(false));
                updatePermissionAction(true);
            }
        }
    }

    @Override
    public void onPermissionStateChanged() {
        if (renderer == null) return;
        if (host.getCameraPermissionState() == PermissionState.GRANTED) {
            updatePermissionAction(false);
            syncCamera(latestState);
        } else {
            renderer.setCameraStatus(CameraStatus.PERMISSION_DENIED);
            viewModel.dispatch(new NavigationAction.SetCameraAvailable(false));
            updatePermissionAction(true);
        }
    }

    private void requestCameraOrOpenSettings() {
        host.requestCameraPermission();
    }

    private void updatePermissionAction(boolean visible) {
        if (cameraPermissionButton == null) return;
        cameraPermissionButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            cameraPermissionButton.setText(
                    host.getCameraPermissionState() == PermissionState.PERMANENTLY_DENIED
                    ? R.string.open_camera_settings : R.string.allow_camera);
        }
    }

    private void unbindCamera() {
        if (cameraController != null) cameraController.unbind();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewResumed = true;
        if (latestState != null) handleNavigationState(latestState);
    }

    @Override
    public void onPause() {
        viewResumed = false;
        if (vision != null) vision.suspend();
        activityHost().getJourney().gate.resetObservations();
        unbindCamera();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        viewResumed = false;
        renderHandler.removeCallbacks(pendingRender);
        pendingRenderState = null;
        unbindCamera();
        if (vision != null) { vision.close(() -> {}); vision = null; }
        transitionStatus = null;
        renderer = null;
        cameraController = null;
        cameraPreview = null;
        cameraPermissionButton = null;
        latestState = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        host = null;
        viewModel = null;
        super.onDetach();
    }

    private static final class ViewRenderTarget implements ArRenderTarget {
        private final TextView destination;
        private final TextView status;
        private final TextView instruction;
        private final TextView distance;
        private final ArrowOverlayView arrow;
        private final Button mapAction;
        private final Button endAction;
        private final ColorStateList normalMapTint;

        ViewRenderTarget(View root) {
            destination = root.findViewById(R.id.ar_destination);
            status = root.findViewById(R.id.ar_status);
            instruction = root.findViewById(R.id.ar_instruction);
            distance = root.findViewById(R.id.ar_distance);
            arrow = root.findViewById(R.id.direction_arrow);
            mapAction = root.findViewById(R.id.show_map_button);
            endAction = root.findViewById(R.id.end_navigation_button);
            normalMapTint = mapAction.getBackgroundTintList();
        }

        @Override public void setDestination(String text) { destination.setText(text); }
        @Override public void setStatus(String text) { status.setText(text); }
        @Override public void setInstruction(String text) { instruction.setText(text); }
        @Override public void setDistance(String text) { distance.setText(text); }

        @Override
        public void setArrow(boolean visible, double angleDegrees, String description) {
            arrow.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) arrow.setDirection(angleDegrees, description);
        }

        @Override
        public void setMapActionEmphasized(boolean emphasized) {
            mapAction.setBackgroundTintList(emphasized
                    ? ColorStateList.valueOf(ContextCompat.getColor(
                            mapAction.getContext(), R.color.nav_accent)) : normalMapTint);
            mapAction.setTextColor(ContextCompat.getColor(mapAction.getContext(),
                    emphasized ? android.R.color.white : R.color.nav_ink));
        }

        @Override
        public void setEndActionVisible(boolean visible) {
            endAction.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
}
