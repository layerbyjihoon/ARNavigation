package com.example.arnavigation.entrance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Later field masks can be registered without rebuilding the matching algorithm. */
public final class EntranceProfile {
    public static final String MODEL_ID = "segformer-b0-ade20k-512-v1";
    public final String entranceId, buildingId, indoorMapId, startNodeId, floorId;
    public final double latitude, longitude;
    public final List<StructureSignature> inside, negatives;

    public EntranceProfile(String entranceId, String buildingId, String indoorMapId,
            String startNodeId, String floorId, double latitude, double longitude,
            List<SegmentationMask> inside, List<SegmentationMask> negatives) {
        this.entranceId = required(entranceId);
        this.buildingId = required(buildingId);
        this.indoorMapId = required(indoorMapId);
        this.startNodeId = required(startNodeId);
        this.floorId = required(floorId);
        if (!Double.isFinite(latitude) || Math.abs(latitude) > 90
                || !Double.isFinite(longitude) || Math.abs(longitude) > 180)
            throw new IllegalArgumentException("Invalid entrance coordinates");
        this.latitude = latitude;
        this.longitude = longitude;
        this.inside = signatures(inside);
        this.negatives = signatures(negatives);
    }

    public boolean isReady() { return !inside.isEmpty() && !negatives.isEmpty(); }

    public static EntranceProfile read(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if (root.get("schemaVersion").getAsInt() != 1
                || !MODEL_ID.equals(root.get("modelId").getAsString())
                || !"upright-full-frame".equals(root.get("viewPolicy").getAsString()))
            throw new IllegalArgumentException("Unsupported entrance profile contract");
        return new EntranceProfile(root.get("entranceId").getAsString(),
                root.get("buildingId").getAsString(), root.get("indoorMapId").getAsString(),
                root.get("startNodeId").getAsString(), root.get("floorId").getAsString(),
                root.get("latitude").getAsDouble(), root.get("longitude").getAsDouble(),
                masks(root.getAsJsonArray("insideMasks")), masks(root.getAsJsonArray("negativeMasks")));
    }

    private static List<SegmentationMask> masks(JsonArray array) {
        List<SegmentationMask> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject m = element.getAsJsonObject();
            JsonArray data = m.getAsJsonArray("labels");
            int[] labels = new int[data.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = data.get(i).getAsBigDecimal().intValueExact();
            result.add(new SegmentationMask(m.get("width").getAsBigDecimal().intValueExact(),
                    m.get("height").getAsBigDecimal().intValueExact(), labels));
        }
        return result;
    }

    private static List<StructureSignature> signatures(List<SegmentationMask> masks) {
        List<StructureSignature> result = new ArrayList<>();
        for (SegmentationMask mask : masks) {
            StructureSignature signature = StructureSignature.from(mask);
            if (!signature.isValid()) throw new IllegalArgumentException("Insufficient reference structure");
            result.add(signature);
        }
        return Collections.unmodifiableList(result);
    }

    private static String required(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Missing profile ID");
        return value;
    }
}
