package com.example.arnavigation.config;

import androidx.annotation.NonNull;

import com.example.arnavigation.BuildConfig;

/** Immutable result of validating API configuration supplied at build time. */
public final class ApiAvailability {
    private final boolean mapsAvailable;
    private final boolean placesAvailable;
    private final boolean routesAvailable;
    private final boolean routesUseProxy;

    private ApiAvailability(
            boolean mapsAvailable,
            boolean placesAvailable,
            boolean routesAvailable,
            boolean routesUseProxy) {
        this.mapsAvailable = mapsAvailable;
        this.placesAvailable = placesAvailable;
        this.routesAvailable = routesAvailable;
        this.routesUseProxy = routesUseProxy;
    }

    public static ApiAvailability fromBuildConfig() {
        return evaluate(
                BuildConfig.MAPS_API_KEY,
                BuildConfig.PLACES_API_KEY,
                BuildConfig.TMAP_APP_KEY,
                BuildConfig.TMAP_PROXY_BASE_URL);
    }

    public static ApiAvailability evaluate(
            String mapsApiKey,
            String placesApiKey,
            String tmapAppKey,
            String tmapProxyBaseUrl) {
        boolean hasProxy = isHttpUrl(tmapProxyBaseUrl);
        boolean hasDirectRoutesKey = isConfigured(tmapAppKey);
        return new ApiAvailability(
                isConfigured(mapsApiKey),
                isConfigured(placesApiKey),
                hasProxy || hasDirectRoutesKey,
                hasProxy);
    }

    private static boolean isConfigured(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean isHttpUrl(String value) {
        if (!isConfigured(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("https://") || BuildConfig.DEBUG && normalized.startsWith("http://");
    }

    public boolean isMapsAvailable() {
        return mapsAvailable;
    }

    public boolean isPlacesAvailable() {
        return placesAvailable;
    }

    public boolean isRoutesAvailable() {
        return routesAvailable;
    }

    public boolean isRoutesUsingProxy() {
        return routesUseProxy;
    }

    @NonNull
    public String getUserFacingSummary() {
        if (mapsAvailable && placesAvailable && routesAvailable) {
            return "지도·검색 및 TMAP 경로 설정이 준비되었습니다.";
        }
        StringBuilder summary = new StringBuilder("필요한 API 설정:");
        if (!mapsAvailable) {
            summary.append(" Maps");
        }
        if (!placesAvailable) {
            summary.append(" Places");
        }
        if (!routesAvailable) {
            summary.append(" TMAP_APP_KEY 또는 TMAP_PROXY_BASE_URL");
        }
        return summary.toString();
    }
}

