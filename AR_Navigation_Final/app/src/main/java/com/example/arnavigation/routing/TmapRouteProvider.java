package com.example.arnavigation.routing;

import android.os.Handler;
import android.os.Looper;
import com.example.arnavigation.BuildConfig;
import com.example.arnavigation.config.AppConfig;
import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.OutdoorRoute;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** TMAP pedestrian REST provider; callbacks and cancellation follow the shared route contract. */
public final class TmapRouteProvider implements RouteProvider {
    static final String ENDPOINT = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Gson gson;
    private final TmapRouteMapper mapper;
    private final HttpUrl endpoint;
    private final String apiKey;
    private final boolean proxyMode;
    private final RouteCallbackDispatcher callbackDispatcher;
    private final Object requestLock = new Object();
    private RouteRequestHandle activeRequest;

    public static TmapRouteProvider fromBuildConfig() {
        boolean proxy = !BuildConfig.TMAP_PROXY_BASE_URL.trim().isEmpty();
        String endpoint = proxy ? proxyEndpoint(BuildConfig.TMAP_PROXY_BASE_URL) : ENDPOINT;
        Handler mainHandler = new Handler(Looper.getMainLooper());
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(AppConfig.ROUTES_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(AppConfig.ROUTES_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        return new TmapRouteProvider(client, new Gson(), endpoint,
                proxy ? "" : BuildConfig.TMAP_APP_KEY, proxy, mainHandler::post);
    }

    TmapRouteProvider(OkHttpClient client, Gson gson, String endpoint, String apiKey,
            boolean proxyMode, RouteCallbackDispatcher callbackDispatcher) {
        this.client = Objects.requireNonNull(client);
        this.gson = Objects.requireNonNull(gson);
        this.mapper = new TmapRouteMapper();
        this.endpoint = Objects.requireNonNull(HttpUrl.parse(endpoint));
        this.apiKey = normalized(apiKey);
        this.proxyMode = proxyMode;
        this.callbackDispatcher = Objects.requireNonNull(callbackDispatcher);
        if (!proxyMode && this.apiKey.isEmpty()) {
            throw new IllegalArgumentException("TMAP appKey is required");
        }
    }

    @Override
    public Cancelable computeWalkingRoute(
            GeoPoint origin,
            Destination destination,
            RouteCallback callback) {
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(callback, "callback must not be null");

        Request request = buildRequest(origin, destination);
        Call call = client.newCall(request);
        RouteRequestHandle handle = new RouteRequestHandle(call, callback);
        RouteRequestHandle previous;
        synchronized (requestLock) {
            previous = activeRequest;
            activeRequest = handle;
        }
        if (previous != null) previous.cancel();

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call ignored, IOException error) {
                if (handle.isCanceled()) {
                    handle.completeFailure(RouteError.CANCELED);
                } else {
                    handle.completeFailure(RouteError.NETWORK);
                }
            }

            @Override
            public void onResponse(Call ignored, Response response) {
                try (response) {
                    if (!response.isSuccessful()) {
                        handle.completeFailure(classifyHttpStatus(response.code()));
                        return;
                    }
                    ResponseBody body = response.body();
                    if (body == null) {
                        handle.completeFailure(RouteError.MALFORMED_RESPONSE);
                        return;
                    }
                    try {
                        OutdoorRoute route = mapper.map(body.string(), destination);
                        handle.completeSuccess(route);
                    } catch (RouteMappingException error) {
                        handle.completeFailure(error.getError());
                    } catch (IOException error) {
                        handle.completeFailure(RouteError.NETWORK);
                    }
                }
            }
        });
        return handle;
    }

    private Request buildRequest(GeoPoint origin, Destination destination) {
        JsonObject body = new JsonObject();
        body.addProperty("startX", origin.getLongitude());
        body.addProperty("startY", origin.getLatitude());
        body.addProperty("endX", destination.getPoint().getLongitude());
        body.addProperty("endY", destination.getPoint().getLatitude());
        body.addProperty("startName", encodeName("출발"));
        body.addProperty("endName", encodeName(destination.getName()));
        body.addProperty("reqCoordType", "WGS84GEO");
        body.addProperty("resCoordType", "WGS84GEO");
        body.addProperty("searchOption", "0");
        body.addProperty("sort", "index");
        Request.Builder request = new Request.Builder().url(endpoint)
                .post(RequestBody.create(gson.toJson(body), JSON))
                .header("Accept", "application/json");
        if (!proxyMode) request.header("appKey", apiKey);
        return request.build();
    }

    private static String encodeName(String name) {
        // TMAP requires UTF-8 URL encoding even inside the JSON name fields.
        try {
            return URLEncoder.encode(name, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static RouteError classifyHttpStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) return RouteError.AUTHENTICATION;
        if (statusCode == 429) return RouteError.QUOTA;
        if (statusCode >= 500) return RouteError.NETWORK;
        return RouteError.MALFORMED_RESPONSE;
    }

    private static String proxyEndpoint(String baseUrl) {
        String normalized = normalized(baseUrl);
        if (normalized.contains("?")) return normalized;
        if (!normalized.endsWith("/")) normalized += "/";
        return normalized + "tmap/routes/pedestrian?version=1";
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private void clearIfActive(RouteRequestHandle handle) {
        synchronized (requestLock) {
            if (activeRequest == handle) activeRequest = null;
        }
    }

    private final class RouteRequestHandle implements Cancelable {
        private final Call call;
        private final RouteCallback callback;
        private final AtomicBoolean terminal = new AtomicBoolean();
        private volatile boolean canceled;

        RouteRequestHandle(Call call, RouteCallback callback) {
            this.call = call;
            this.callback = callback;
        }

        @Override
        public void cancel() {
            canceled = true;
            call.cancel();
            completeFailure(RouteError.CANCELED);
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        void completeSuccess(OutdoorRoute route) {
            if (!terminal.compareAndSet(false, true)) return;
            clearIfActive(this);
            callbackDispatcher.dispatch(() -> callback.onSuccess(route));
        }

        void completeFailure(RouteError error) {
            if (!terminal.compareAndSet(false, true)) return;
            clearIfActive(this);
            callbackDispatcher.dispatch(() -> callback.onFailure(error));
        }
    }
}
