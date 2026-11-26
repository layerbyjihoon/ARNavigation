package com.example.arnavigation.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.example.arnavigation.model.GeoPoint;
import java.util.List;
import org.junit.Test;

public final class PolylineDecoderTest {
    @Test
    public void decodesGoogleReferenceVector() {
        List<GeoPoint> points = new PolylineDecoder().decode(
                "_p~iF~ps|U_ulLnnqC_mqNvxq`@");

        assertEquals(3, points.size());
        assertEquals(new GeoPoint(38.5, -120.2), points.get(0));
        assertEquals(new GeoPoint(40.7, -120.95), points.get(1));
        assertEquals(new GeoPoint(43.252, -126.453), points.get(2));
    }

    @Test
    public void rejectsTruncatedEncoding() {
        assertThrows(IllegalArgumentException.class, () -> new PolylineDecoder().decode("_p~iF~"));
    }
}
