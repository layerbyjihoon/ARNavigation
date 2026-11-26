package com.example.arnavigation.routing;

import com.example.arnavigation.model.GeoPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Decodes Google's encoded polyline format using 1e-5 degree precision. */
public final class PolylineDecoder {
    public List<GeoPoint> decode(String encodedPolyline) {
        if (encodedPolyline == null || encodedPolyline.isEmpty()) {
            throw new IllegalArgumentException("encodedPolyline must not be empty");
        }

        ArrayList<GeoPoint> points = new ArrayList<>();
        int latitude = 0;
        int longitude = 0;
        int index = 0;
        while (index < encodedPolyline.length()) {
            DecodeResult latitudeDelta = decodeValue(encodedPolyline, index);
            index = latitudeDelta.nextIndex;
            DecodeResult longitudeDelta = decodeValue(encodedPolyline, index);
            index = longitudeDelta.nextIndex;
            try {
                latitude = Math.addExact(latitude, latitudeDelta.value);
                longitude = Math.addExact(longitude, longitudeDelta.value);
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException("encodedPolyline coordinate overflow", error);
            }
            points.add(new GeoPoint(latitude / 1.0e5d, longitude / 1.0e5d));
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("encodedPolyline contains no points");
        }
        return Collections.unmodifiableList(points);
    }

    private static DecodeResult decodeValue(String encoded, int startIndex) {
        int result = 0;
        int shift = 0;
        int index = startIndex;
        while (true) {
            if (index >= encoded.length() || shift > 30) {
                throw new IllegalArgumentException("malformed encodedPolyline");
            }
            int value = encoded.charAt(index++) - 63;
            if (value < 0 || value > 63) {
                throw new IllegalArgumentException("malformed encodedPolyline character");
            }
            result |= (value & 0x1f) << shift;
            shift += 5;
            if (value < 0x20) break;
        }
        int delta = (result & 1) != 0 ? ~(result >>> 1) : result >>> 1;
        return new DecodeResult(delta, index);
    }

    private static final class DecodeResult {
        final int value;
        final int nextIndex;

        DecodeResult(int value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }
}
