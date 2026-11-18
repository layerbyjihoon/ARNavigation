package com.example.entrancetransition;

import com.example.arnavigation.entrance.SegmentationMask;
import com.example.arnavigation.entrance.StructureSignature;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression of the supplied photos, not an independent field-accuracy benchmark. */
public final class CulturalCenterReferenceTest {
    @Test public void realMasksAreValidAndIndoorScoreAloneDeterminesMode() throws Exception {
        JsonObject profile;
        try (FileReader reader = new FileReader(System.getProperty("culture.references"))) {
            profile = JsonParser.parseReader(reader).getAsJsonObject();
        }
        assertEquals("ssu-munhwagwan", profile.get("buildingId").getAsString());
        List<StructureSignature> inside = read(profile.getAsJsonArray("insideMasks"));
        List<StructureSignature> outside = read(profile.getAsJsonArray("outsideMasks"));
        assertEquals(4, inside.size());
        for (int i = 0; i < 4; i++) {
            assertEquals("gate" + (i + 1), profile.getAsJsonArray("insideMasks")
                    .get(i).getAsJsonObject().get("referenceId").getAsString());
        }
        assertEquals(2, outside.size());
        for (StructureSignature signature : inside) {
            EnvironmentTransitionController controller = replay(best(signature, inside));
            assertEquals(EnvironmentTransitionController.Mode.INDOOR, controller.mode());
        }
        for (StructureSignature signature : outside) {
            EnvironmentTransitionController controller = replay(best(signature, inside));
            assertEquals(EnvironmentTransitionController.Mode.OUTDOOR, controller.mode());
        }
    }

    private static EnvironmentTransitionController replay(double inside) {
        System.out.println("inside=" + inside);
        EnvironmentTransitionController controller = new EnvironmentTransitionController();
        controller.observe(inside, true, 0, 0);
        return controller;
    }

    private static List<StructureSignature> read(JsonArray array) {
        List<StructureSignature> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject mask = element.getAsJsonObject();
            JsonArray labels = mask.getAsJsonArray("labels");
            int[] data = new int[labels.size()];
            for (int i = 0; i < data.length; i++) data[i] = labels.get(i).getAsInt();
            StructureSignature signature = StructureSignature.from(new SegmentationMask(
                    mask.get("width").getAsInt(), mask.get("height").getAsInt(), data));
            assertTrue("Reference must meet actual structure-quality criteria", signature.isValid());
            result.add(signature);
        }
        return result;
    }

    private static double best(StructureSignature signature, List<StructureSignature> references) {
        double best = 0;
        for (StructureSignature reference : references) best = Math.max(best, signature.similarity(reference));
        return best;
    }
}
