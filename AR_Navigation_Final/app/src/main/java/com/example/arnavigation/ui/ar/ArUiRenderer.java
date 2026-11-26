package com.example.arnavigation.ui.ar;

import com.example.arnavigation.camera.CameraStatus;
import com.example.arnavigation.heading.HeadingStatus;
import com.example.arnavigation.location.LocationStatus;
import com.example.arnavigation.navigation.NavigationStage;
import com.example.arnavigation.navigation.NavigationUiState;
import java.util.Locale;
import java.util.Objects;

/** Renders NavigationUiState without performing navigation or sensor calculations. */
public final class ArUiRenderer {
    private final ArRenderTarget target;
    private final RouteGuidanceFormatter guidanceFormatter = new RouteGuidanceFormatter();
    private CameraStatus cameraStatus = CameraStatus.IDLE;
    private NavigationUiState lastState;

    public ArUiRenderer(
            ArRenderTarget target, NavigationInstructionFormatter formatter) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(formatter, "formatter must not be null");
    }

    public void setCameraStatus(CameraStatus cameraStatus) {
        this.cameraStatus = Objects.requireNonNull(cameraStatus, "cameraStatus must not be null");
        if (lastState != null) render(lastState);
    }

    public void render(NavigationUiState state) {
        lastState = Objects.requireNonNull(state, "state must not be null");
        boolean hasDestination = state.getDestination() != null;
        target.setDestination(hasDestination ? state.getDestination().getName() : "목적지 없음");
        target.setEndActionVisible(hasDestination);
        target.setMapActionEmphasized(false);
        target.setArrow(false, 0.0d, "");

        if (!hasDestination || state.getRoute() == null) {
            target.setStatus(state.getRouteError() == null
                    ? "지도에서 목적지를 먼저 선택하세요"
                    : "경로를 불러오지 못했습니다: " + state.getRouteError().name());
            target.setInstruction("2D 지도에서 목적지를 확인하세요");
            target.setDistance("");
            target.setMapActionEmphasized(true);
            return;
        }
        if (state.getStage() == NavigationStage.ARRIVED) {
            target.setStatus("목적지에 도착했습니다");
            target.setInstruction("안내를 종료하거나 지도를 확인하세요");
            target.setDistance("");
            return;
        }

        RouteGuidanceFormatter.Guidance guidance = guidanceFormatter.format(state);
        target.setInstruction(guidance.text);
        target.setDistance(distanceText(state, guidance.distanceMeters));
        if (state.getLocationStatus() != LocationStatus.AVAILABLE) {
            target.setStatus(locationMessage(state.getLocationStatus()));
            target.setMapActionEmphasized(true);
            return;
        }
        if (state.getHeadingStatus() != HeadingStatus.AVAILABLE) {
            target.setStatus("방향 확인 중");
            return;
        }
        if (state.isOffRoute()) {
            target.setStatus(state.isRerouting() ? "경로를 다시 찾는 중입니다" : "경로를 벗어났습니다");
            target.setInstruction("안전한 곳에서 2D 지도를 확인하세요");
            target.setMapActionEmphasized(true);
            return;
        }
        if (cameraStatus != CameraStatus.BOUND) {
            target.setStatus(cameraMessage(cameraStatus));
            target.setMapActionEmphasized(true);
            return;
        }

        Double angle = state.getRelativeAngleDegrees();
        target.setStatus("AR 안내 중");
        if (angle != null) target.setArrow(state.isArrowVisible(), angle, guidance.text);
    }

    private static String distanceText(NavigationUiState state, Double distance) {
        String next = distance == null ? "" : String.format(Locale.getDefault(), "다음 안내 약 %.0f m · ", distance);
        return String.format(Locale.getDefault(), "%s전체 약 %.0f m",next,state.getRoute().getDistanceMeters());
    }

    private static String locationMessage(LocationStatus status) {
        switch (status) {
            case PERMISSION_DENIED: return "위치 권한이 필요합니다";
            case DISABLED: return "기기의 위치 서비스를 켜 주세요";
            case STALE: return "현재 위치가 오래되었습니다";
            case LOW_ACCURACY: return "GPS 정확도가 낮습니다";
            case COARSE_ONLY: return "정확한 위치 권한이 필요합니다";
            default: return "현재 위치를 확인할 수 없습니다";
        }
    }

    private static String cameraMessage(CameraStatus status) {
        switch (status) {
            case BINDING: return "카메라를 준비하는 중입니다";
            case PERMISSION_DENIED: return "카메라 권한이 필요합니다";
            case NO_BACK_CAMERA: return "후면 카메라를 찾을 수 없습니다";
            case PROVIDER_UNAVAILABLE: return "카메라를 초기화하지 못했습니다";
            case BIND_FAILED: return "카메라가 사용 중이거나 열 수 없습니다";
            default: return "카메라 미리보기를 사용할 수 없습니다";
        }
    }
}
