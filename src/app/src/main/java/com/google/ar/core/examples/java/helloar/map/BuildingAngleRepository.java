package com.google.ar.core.examples.java.helloar.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Configured map rotation by building ID. */
public final class BuildingAngleRepository {
  private static final String ASSET_PATH = "maps/building_angle.json";

  public double load(IndoorMapRepository.AssetSource assets, String buildingId) throws IOException {
    try (InputStream input = assets.open(ASSET_PATH);
        InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      JsonElement root = JsonParser.parseReader(reader);
      if (!root.isJsonObject()) throw new IllegalArgumentException("Building angles must be an object");
      JsonObject angles = root.getAsJsonObject();
      JsonElement value = angles.get(buildingId);
      if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
        throw new IllegalArgumentException("No valid angle for building " + buildingId);
      double angle = value.getAsDouble();
      if (!Double.isFinite(angle) || angle < 0 || angle >= 360)
        throw new IllegalArgumentException("Angle must be in [0, 360)");
      return angle;
    }
  }
}
