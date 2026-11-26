package com.example.arnavigation.routing;

import android.content.Context;
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

/** Routes API v2 walking provider with direct Android-key and proxy modes. */
public final class GoogleRouteProvider implements RouteProvider {
    static final String GOOGLE_ROUTES_ENDPOINT =
            "https://routes.googleapis.com/directions/v2:computeRoutes";
    static final String FIELD_MASK = String.join(",",
            "routes.distanceMeters",
            "routes.duration",
            "routes.polyline.encodedPolyline",
            "routes.legs.steps.distanceMeters",
            "routes.legs.steps.startLocation",
            "routes.legs.steps.endLocation",
            "routes.legs.steps.polyline.encodedPolyline",
            "routes.legs.steps.navigationInstruction");
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Gson gson;
    private final GoogleRouteMapper mapper;
    private final HttpUrl endpoint;
    private final String apiKey;
    private final String androidPackage;
    private final String androidCertificate;
    private final boolean proxyMode;
    private final RouteCallbackDispatcher callbackDispatcher;
    private final Object requestLock = new Object();
    private RouteRequestHandle activeRequest;

    public static GoogleRouteProvider fromBuildConfig(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        Context applicationContext = context.getApplicationContext();
        boolean proxyMode = BuildConfig.ROUTES_PROXY_BASE_URL != null
                && !BuildConfig.ROUTES_PROXY_BASE_URL.trim().isEmpty();
        String endpoint = proxyMode
                ? proxyEndpoint(BuildConfig.ROUTES_PROXY_BASE_URL)
                : GOOGLE_ROUTES_ENDPOINT;
        Handler mainHandler = new Handler(Looper.getMainLooper());
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(AppConfig.ROUTES_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(AppConfig.ROUTES_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        return new GoogleRouteProvider(
                client,
                new Gson(),
                endpoint,
                proxyMode ? "" : BuildConfig.ROUTES_API_KEY,
                proxyMode ? "" : AndroidAppIdentity.packageName(applicationContext),
                proxyMode ? "" : AndroidAppIdentity.sha1CertificateFingerprint(applicationContext),
                proxyMode,
                mainHandler::post);
    }

    GoogleRouteProvider(
            OkHttpClient client,
            Gson gson,
            String endpoint,
            String apiKey,
            String androidPackage,
            String androidCertificate,
            boolean proxyMode,
            RouteCallbackDispatcher callbackDispatcher) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.gson = Objects.requireNonNull(gson, "gson must not be null");
        this.mapper = new GoogleRouteMapper(
                gson,
                new PolylineDecoder(),
                new WaypointBuilder(AppConfig.MIN_WAYPOINT_SPACING_METERS));
        this.endpoint = Objects.requireNonNull(
                HttpUrl.parse(Objects.requireNonNull(endpoint, "endpoint must not be null")),
                "endpoint must be a valid HTTP URL");
        this.proxyMode = proxyMode;
        this.apiKey = normalized(apiKey);
        this.androidPackage = normalized(androidPackage);
        this.androidCertificate = normalized(androidCertificate);
        this.callbackDispatcher = Objects.requireNonNull(
                callbackDispatcher, "callbackDispatcher must not be null");
        if (!proxyMode && (this.apiKey.isEmpty()
                || this.androidPackage.isEmpty() || this.androidCertificate.isEmpty())) {
            throw new IllegalArgumentException(
                    "direct Routes calls require API key, Android package, and SHA-1 certificate");
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

        Request request = buildRequest(origin, destination.getPoint());
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

    private Request buildRequest(GeoPoint origin, GeoPoint destination) {
        Request.Builder request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(gson.toJson(requestJson(origin, destination)), JSON))
                .header("X-Goog-FieldMask", FIELD_MASK);
        if (!proxyMode) {
            request.header("X-Goog-Api-Key", apiKey)
                    .header("X-Android-Package", androidPackage)
                    .header("X-Android-Cert", androidCertificate);
        }
        return request.build();
    }

    private static JsonObject requestJson(GeoPoint origin, GeoPoint destination) {
        JsonObject body = new JsonObject();
        body.add("origin", waypointJson(origin));
        body.add("destination", waypointJson(destination));
        body.addProperty("travelMode", "WALK");
        body.addProperty("polylineQuality", "HIGH_QUALITY");
        body.addProperty("polylineEncoding", "ENCODED_POLYLINE");
        body.addProperty("languageCode", "ko");
        body.addProperty("units", "METRIC");
        return body;
    }

    private static JsonObject waypointJson(GeoPoint point) {
        JsonObject latLng = new JsonObject();
        latLng.addProperty("latitude", point.getLatitude());
        latLng.addProperty("longitude", point.getLongitude());
        JsonObject location = new JsonObject();
        location.add("latLng", latLng);
        JsonObject waypoint = new JsonObject();
        waypoint.add("location", location);
        return waypoint;
    }

    static RouteError classifyHttpStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) return RouteError.AUTHENTICATION;
        if (statusCode == 429) return RouteError.QUOTA;
        if (statusCode >= 500) return RouteError.NETWORK;
        return RouteError.MALFORMED_RESPONSE;
    }

    private static String proxyEndpoint(String baseUrl) {
        String normalized = normalized(baseUrl);
        if (normalized.endsWith(":computeRoutes")) return normalized;
        if (!normalized.endsWith("/")) normalized += "/";
        return normalized + "directions/v2:computeRoutes";
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
