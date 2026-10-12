package com.example.modelevaluation.inference;

import android.content.Context;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public final class AssetUtils {
    private AssetUtils() {}

    public static byte[] readBytes(Context context, String assetName) throws IOException {
        try (InputStream input = context.getAssets().open(assetName)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    public static boolean exists(Context context, String assetName) {
        try (InputStream ignored = context.getAssets().open(assetName)) {
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    public static List<String> readLabels(Context context, String assetName) throws IOException {
        List<String> labels = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(assetName)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String clean = line.trim();
                if (clean.isEmpty()) continue;
                clean = clean.replaceFirst("^/[^/]+/", "");
                clean = clean.replaceFirst("\\s+\\d+$", "");
                labels.add(clean.replace('_', ' '));
            }
        }
        return labels;
    }
}
