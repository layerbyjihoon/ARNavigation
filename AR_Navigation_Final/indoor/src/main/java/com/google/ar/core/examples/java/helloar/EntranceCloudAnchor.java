package com.google.ar.core.examples.java.helloar;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import com.google.ar.core.Pose;
import com.google.ar.core.examples.java.helloar.coordinates.LocalMapCoordinateTransformer;
import com.google.ar.core.examples.java.helloar.map.IndoorMap;
import com.google.ar.core.examples.java.helloar.map.MapNode;
import com.google.ar.core.examples.java.helloar.map.MapPosition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Surveyed correspondence between the hosted floor anchor and the indoor map. */
final class EntranceCloudAnchor {
  static final String EXTRA = "entrance_cloud_anchor";
  private static final String ASSET = "entrance_cloud_anchor.json";
  private static final String BUILDING_ID = "ssu-munhwagwan";
  private static final String ENTRANCE_ID = "F1_ENTRY_MAIN";
  private static final String FLOOR_ID = "F1";
  private static final String TAG = "EntranceCloudAnchor";

  final String cloudId;
  private final double x, y, z, yaw;

  private EntranceCloudAnchor(String cloudId, double x, double y, double z, double yaw) {
    if (cloudId == null || cloudId.trim().isEmpty() || cloudId.length() > 512
        || !Float.isFinite((float) x) || !Float.isFinite((float) y)
        || !Float.isFinite((float) z) || !Double.isFinite(yaw)) {
      throw new IllegalArgumentException("Invalid Cloud Anchor ID or pose");
    }
    this.cloudId = cloudId.trim();
    this.x = x;
    this.y = y;
    this.z = z;
    this.yaw = yaw;
  }

  static EntranceCloudAnchor load(AssetManager assets, IndoorMap map) {
    try (InputStreamReader reader = new InputStreamReader(assets.open(ASSET),
        StandardCharsets.UTF_8)) {
      return read(JsonParser.parseReader(reader).getAsJsonObject(), map);
    } catch (FileNotFoundException absent) {
      return null;
    } catch (IOException | RuntimeException invalid) {
      Log.w(TAG, "Invalid entrance Cloud Anchor configuration");
      return null;
    }
  }

  static EntranceCloudAnchor read(JsonObject json, IndoorMap map) {
    if (json.get("schemaVersion").getAsInt() != 1
        || !BUILDING_ID.equals(json.get("buildingId").getAsString())
        || !ENTRANCE_ID.equals(json.get("entranceId").getAsString())
        || !FLOOR_ID.equals(json.get("floorId").getAsString())
        || !map.metadata.id.equals(json.get("mapId").getAsString())
        || !map.version.equals(json.get("mapVersion").getAsString())) {
      throw new IllegalArgumentException("Anchor belongs to a different entrance or map");
    }
    JsonObject pose = json.getAsJsonObject("mapPose");
    double yawDegrees = pose.get("yawDegrees").getAsDouble();
    if (Math.abs(yawDegrees) > 180) throw new IllegalArgumentException("Invalid map yaw");
    return new EntranceCloudAnchor(json.get("cloudAnchorId").getAsString(),
        pose.get("x").getAsDouble(), pose.get("y").getAsDouble(), pose.get("z").getAsDouble(),
        yawDegrees);
  }

  EntranceCloudAnchor forRoute(IndoorMap map, MapNode start, double planYawDegrees) {
    if (!FLOOR_ID.equals(start.floorId) || !MapRouteActivity.MUNHWAGWAN_START_ID.equals(start.id))
      return null;
    MapPosition local = new LocalMapCoordinateTransformer(map.coordinateSystem, start.position,
        new MapPosition(0, -0.5, 0), planYawDegrees).mapToAr(new MapPosition(x, y, z));
    return new EntranceCloudAnchor(cloudId, local.x, local.y, local.z,
        Math.toRadians(yaw + planYawDegrees));
  }

  Bundle toRouteBundle() {
    Bundle result = new Bundle();
    result.putString("id", cloudId);
    result.putDoubleArray("pose", new double[] {x, y, z, yaw});
    return result;
  }

  static EntranceCloudAnchor fromRoute(Bundle route) {
    if (route == null) return null;
    double[] pose = route.getDoubleArray("pose");
    if (pose == null || pose.length != 4) return null;
    try {
      return new EntranceCloudAnchor(route.getString("id"), pose[0], pose[1], pose[2], pose[3]);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  Pose routeOrigin(Pose resolved) {
    float[] axis = resolved.getZAxis();
    if (resolved.getYAxis()[1] < 0.95f) return null;
    double rotation = Math.atan2(axis[0], axis[2]) - yaw;
    double cos = Math.cos(rotation);
    double sin = Math.sin(rotation);
    return new Pose(new float[] {
        (float) (resolved.tx() - (cos * x + sin * z)),
        (float) (resolved.ty() - y),
        (float) (resolved.tz() - (-sin * x + cos * z))},
        new float[] {0, (float) Math.sin(rotation / 2), 0, (float) Math.cos(rotation / 2)});
  }
}
