package com.example.arnavigation.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.DestinationSource;
import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.model.OutdoorRoute;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.Test;

public final class TmapRouteProviderTest {
    private static final Destination DESTINATION = new Destination(
            "destination", "문화관 입구", new GeoPoint(37.491, 126.952),
            DestinationSource.MAP_TAP, null);

    @Test
    public void sendsTmapPedestrianRequestWithEncodedNamesAndWgs84() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(fixture("tmap_success.json")));
            TmapRouteProvider provider = provider(
                    new OkHttpClient(), server.url("/tmap/routes/pedestrian?version=1").toString());
            RecordingCallback callback = new RecordingCallback();

            provider.computeWalkingRoute(new GeoPoint(37.49, 126.95), DESTINATION, callback);

            assertTrue(callback.await());
            assertTrue(callback.route != null);
            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            assertEquals("POST", request.getMethod());
            assertEquals("/tmap/routes/pedestrian?version=1", request.getPath());
            assertEquals("test-key", request.getHeader("appKey"));
            assertNull(request.getHeader("X-Goog-Api-Key"));
            com.google.gson.JsonObject body = com.google.gson.JsonParser.parseString(
                    request.getBody().readUtf8()).getAsJsonObject();
            assertEquals(126.95, body.get("startX").getAsDouble(), 0);
            assertEquals(37.49, body.get("startY").getAsDouble(), 0);
            assertEquals(126.952, body.get("endX").getAsDouble(), 0);
            assertEquals("WGS84GEO", body.get("resCoordType").getAsString());
            assertEquals("WGS84GEO", body.get("reqCoordType").getAsString());
            assertEquals("index", body.get("sort").getAsString());
            assertEquals("문화관 입구", java.net.URLDecoder.decode(
                    body.get("endName").getAsString(), "UTF-8"));
            assertFalse(body.has("travelMode"));
        }
    }

    @Test
    public void classifiesDocumentedHttpFailures() {
        assertEquals(RouteError.MALFORMED_RESPONSE, TmapRouteProvider.classifyHttpStatus(400));
        assertEquals(RouteError.AUTHENTICATION, TmapRouteProvider.classifyHttpStatus(401));
        assertEquals(RouteError.AUTHENTICATION, TmapRouteProvider.classifyHttpStatus(403));
        assertEquals(RouteError.QUOTA, TmapRouteProvider.classifyHttpStatus(429));
        assertEquals(RouteError.NETWORK, TmapRouteProvider.classifyHttpStatus(500));
        assertEquals(RouteError.NETWORK, TmapRouteProvider.classifyHttpStatus(503));
    }

    @Test
    public void timeoutIsNetworkFailure() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    throw new SocketTimeoutException("test timeout");
                })
                .build();
        TmapRouteProvider provider = provider(client, "https://example.test/route");
        RecordingCallback callback = new RecordingCallback();

        provider.computeWalkingRoute(new GeoPoint(37.49, 126.95), DESTINATION, callback);

        assertTrue(callback.await());
        assertEquals(RouteError.NETWORK, callback.error);
    }

    @Test
    public void cancelDeliversCanceledExactlyOnce() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            TmapRouteProvider provider = provider(
                    new OkHttpClient(), server.url("/route").toString());
            RecordingCallback callback = new RecordingCallback();

            Cancelable cancelable = provider.computeWalkingRoute(
                    new GeoPoint(37.49, 126.95), DESTINATION, callback);
            cancelable.cancel();
            cancelable.cancel();

            assertTrue(callback.await());
            assertTrue(cancelable.isCanceled());
            assertEquals(RouteError.CANCELED, callback.error);
            assertEquals(1, callback.completionCount);
            assertNull(callback.route);
        }
    }

    @Test
    public void proxyDoesNotSendAppKeyAndPropagatesHttpErrors() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(403).setBody("{}"));
            TmapRouteProvider provider = new TmapRouteProvider(new OkHttpClient(), new Gson(),
                    server.url("/tmap/routes/pedestrian?version=1").toString(),
                    "must-not-leak", true, Runnable::run);
            RecordingCallback callback = new RecordingCallback();
            provider.computeWalkingRoute(new GeoPoint(37.49, 126.95), DESTINATION, callback);
            assertTrue(callback.await());
            assertEquals(RouteError.AUTHENTICATION, callback.error);
            assertNull(server.takeRequest(1, TimeUnit.SECONDS).getHeader("appKey"));
        }
    }

    @Test
    public void newerRequestCancelsOlderWithoutDeliveringStaleSuccess() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(fixture("tmap_success.json"))
                    .setBodyDelay(1, TimeUnit.SECONDS));
            server.enqueue(new MockResponse().setBody(fixture("tmap_success.json")));
            TmapRouteProvider provider = provider(new OkHttpClient(), server.url("/route").toString());
            RecordingCallback older = new RecordingCallback();
            RecordingCallback newer = new RecordingCallback();
            Cancelable handle = provider.computeWalkingRoute(
                    new GeoPoint(37.49, 126.95), DESTINATION, older);
            assertTrue(server.takeRequest(1, TimeUnit.SECONDS) != null);
            provider.computeWalkingRoute(new GeoPoint(37.49, 126.95), DESTINATION, newer);
            assertTrue(older.await());
            assertTrue(newer.await());
            assertTrue(handle.isCanceled());
            assertEquals(RouteError.CANCELED, older.error);
            assertEquals(1, older.completionCount);
            assertNull(older.route);
            assertTrue(newer.route != null);
        }
    }

    private static TmapRouteProvider provider(OkHttpClient client, String endpoint) {
        return new TmapRouteProvider(
                client,
                new Gson(),
                endpoint,
                "test-key",
                false,
                Runnable::run);
    }

    static String fixture(String name) throws IOException {
        try (InputStream input = TmapRouteProviderTest.class.getResourceAsStream(
                "/fixtures/" + name)) {
            if (input == null) throw new IOException("missing fixture " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class RecordingCallback implements RouteCallback {
        private final CountDownLatch latch = new CountDownLatch(1);
        volatile OutdoorRoute route;
        volatile RouteError error;
        volatile int completionCount;

        @Override
        public void onSuccess(OutdoorRoute route) {
            this.route = route;
            completionCount++;
            latch.countDown();
        }

        @Override
        public void onFailure(RouteError error) {
            this.error = error;
            completionCount++;
            latch.countDown();
        }

        boolean await() throws InterruptedException {
            return latch.await(2, TimeUnit.SECONDS);
        }
    }
}
