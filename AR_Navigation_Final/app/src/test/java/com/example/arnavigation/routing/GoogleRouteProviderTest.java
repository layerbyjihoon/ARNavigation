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

public final class GoogleRouteProviderTest {
    private static final Destination DESTINATION = new Destination(
            "destination", "Destination", new GeoPoint(43.252, -126.453),
            DestinationSource.MAP_TAP, null);

    @Test
    public void sendsWalkingRequestFieldMaskAndAndroidIdentityHeaders() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(fixture("routes_success.json")));
            GoogleRouteProvider provider = provider(
                    new OkHttpClient(), server.url("/directions/v2:computeRoutes").toString());
            RecordingCallback callback = new RecordingCallback();

            provider.computeWalkingRoute(new GeoPoint(38.5, -120.2), DESTINATION, callback);

            assertTrue(callback.await());
            assertTrue(callback.route != null);
            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            assertEquals("POST", request.getMethod());
            assertEquals(GoogleRouteProvider.FIELD_MASK, request.getHeader("X-Goog-FieldMask"));
            assertEquals("test-key", request.getHeader("X-Goog-Api-Key"));
            assertEquals("com.example.test", request.getHeader("X-Android-Package"));
            assertEquals("AABB", request.getHeader("X-Android-Cert"));
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("\"travelMode\":\"WALK\""));
            assertTrue(body.contains("\"languageCode\":\"ko\""));
            assertFalse(body.contains("routingPreference"));
        }
    }

    @Test
    public void classifiesDocumentedHttpFailures() {
        assertEquals(RouteError.MALFORMED_RESPONSE, GoogleRouteProvider.classifyHttpStatus(400));
        assertEquals(RouteError.AUTHENTICATION, GoogleRouteProvider.classifyHttpStatus(401));
        assertEquals(RouteError.AUTHENTICATION, GoogleRouteProvider.classifyHttpStatus(403));
        assertEquals(RouteError.QUOTA, GoogleRouteProvider.classifyHttpStatus(429));
        assertEquals(RouteError.NETWORK, GoogleRouteProvider.classifyHttpStatus(500));
        assertEquals(RouteError.NETWORK, GoogleRouteProvider.classifyHttpStatus(503));
    }

    @Test
    public void timeoutIsNetworkFailure() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    throw new SocketTimeoutException("test timeout");
                })
                .build();
        GoogleRouteProvider provider = provider(client, "https://example.test/route");
        RecordingCallback callback = new RecordingCallback();

        provider.computeWalkingRoute(new GeoPoint(38.5, -120.2), DESTINATION, callback);

        assertTrue(callback.await());
        assertEquals(RouteError.NETWORK, callback.error);
    }

    @Test
    public void cancelDeliversCanceledExactlyOnce() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            GoogleRouteProvider provider = provider(
                    new OkHttpClient(), server.url("/route").toString());
            RecordingCallback callback = new RecordingCallback();

            Cancelable cancelable = provider.computeWalkingRoute(
                    new GeoPoint(38.5, -120.2), DESTINATION, callback);
            cancelable.cancel();
            cancelable.cancel();

            assertTrue(callback.await());
            assertTrue(cancelable.isCanceled());
            assertEquals(RouteError.CANCELED, callback.error);
            assertEquals(1, callback.completionCount);
            assertNull(callback.route);
        }
    }

    private static GoogleRouteProvider provider(OkHttpClient client, String endpoint) {
        return new GoogleRouteProvider(
                client,
                new Gson(),
                endpoint,
                "test-key",
                "com.example.test",
                "AABB",
                false,
                Runnable::run);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream input = GoogleRouteProviderTest.class.getResourceAsStream(
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
