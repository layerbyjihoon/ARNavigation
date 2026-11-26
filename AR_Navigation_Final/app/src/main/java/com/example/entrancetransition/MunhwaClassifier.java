package com.example.entrancetransition;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import com.example.arnavigation.entrance.SegformerSegmenter;
import com.example.arnavigation.entrance.SegmentationMask;
import com.example.arnavigation.entrance.StructureSignature;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Fixed Cultural Center reference set. No GPS, map, route or navigation dependencies. */
public final class MunhwaClassifier implements AutoCloseable {
    static final String BUILDING_ID = "ssu-munhwagwan";
    private final SegformerSegmenter segmenter;
    private final List<StructureSignature> inside;

    public static final class Result {
        public final double inside;
        public final boolean valid;
        final SegmentationMask mask;
        Result(double inside, boolean valid, SegmentationMask mask) {
            this.inside = inside;
            this.valid = valid;
            this.mask = mask;
        }
    }

    public MunhwaClassifier(AssetManager assets) throws Exception {
        JsonObject profile;
        try (InputStreamReader reader = new InputStreamReader(
                assets.open("munhwagwan.json"), StandardCharsets.UTF_8)) {
            profile = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (profile.get("schemaVersion").getAsInt() != 1
                || !BUILDING_ID.equals(profile.get("buildingId").getAsString())
                || !"mmseg-segformer-b0-ade20k-512-v1".equals(profile.get("modelId").getAsString())
                || !"upright-full-frame".equals(profile.get("viewPolicy").getAsString())) {
            throw new IllegalArgumentException("Incompatible Cultural Center references");
        }
        inside = signatures(profile.getAsJsonArray("insideMasks"));
        byte[] model;
        try (InputStream stream = assets.open("segformer-b0-ade20k.onnx")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            model = bytes.toByteArray();
        }
        byte[] expected = hex(profile.get("modelSha256").getAsString());
        if (!MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(model))) {
            throw new IllegalArgumentException("Model/reference checksum mismatch");
        }
        segmenter = new SegformerSegmenter(model);
    }

    public Result classify(Bitmap uprightFullFrame) throws Exception {
        SegmentationMask mask = segmenter.run(uprightFullFrame);
        StructureSignature frame = StructureSignature.from(mask);
        return new Result(best(frame, inside), frame.isValid(), mask);
    }

    private static List<StructureSignature> signatures(JsonArray masks) {
        if (masks == null || masks.isEmpty()) throw new IllegalArgumentException("Missing references");
        List<StructureSignature> result = new ArrayList<>();
        for (JsonElement element : masks) {
            JsonObject mask = element.getAsJsonObject();
            JsonArray data = mask.getAsJsonArray("labels");
            int[] labels = new int[data.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = data.get(i).getAsInt();
            StructureSignature signature = StructureSignature.from(new SegmentationMask(
                    mask.get("width").getAsInt(), mask.get("height").getAsInt(), labels));
            if (!signature.isValid()) throw new IllegalArgumentException("Invalid reference structure");
            result.add(signature);
        }
        return result;
    }

    private static double best(StructureSignature frame, List<StructureSignature> references) {
        double best = 0;
        for (StructureSignature reference : references) best = Math.max(best, frame.similarity(reference));
        return best;
    }

    private static byte[] hex(String text) {
        if (text.length() != 64) throw new IllegalArgumentException("Invalid SHA-256");
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(text.charAt(i * 2), 16);
            int low = Character.digit(text.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid SHA-256");
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    @Override public void close() throws Exception { segmenter.close(); }
}
