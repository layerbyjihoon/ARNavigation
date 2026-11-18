import com.example.arnavigation.entrance.SegmentationMask;
import com.example.arnavigation.entrance.StructureSignature;
import com.example.entrancetransition.EnvironmentTransitionController;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Offline adapter: executes the unchanged app matcher and controller, using the app indoor threshold. */
public final class ReplayTransition {
    public static void main(String[] args) throws Exception {
        JsonObject profile;
        try (FileReader reader = new FileReader(args[0])) {
            profile = JsonParser.parseReader(reader).getAsJsonObject();
        }
        List<StructureSignature> inside = references(profile.getAsJsonArray("insideMasks"));
        EnvironmentTransitionController controller = new EnvironmentTransitionController();
        Gson gson = new Gson();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonObject input = JsonParser.parseString(line).getAsJsonObject();
                long time = input.get("timestampMs").getAsLong();
                StructureSignature frame = StructureSignature.from(mask(input));
                double in = best(frame, inside);
                controller.observe(in, frame.isValid(), time, time);
                JsonObject result = new JsonObject();
                result.addProperty("timestampMs", time);
                result.addProperty("insideScore", in);
                String matchedGate = "unknown";
                double bestScore = -1;
                for (int i = 0; i < inside.size(); i++) {
                    String referenceId = profile.getAsJsonArray("insideMasks").get(i)
                            .getAsJsonObject().get("referenceId").getAsString();
                    double score = frame.similarity(inside.get(i));
                    result.addProperty(referenceId + "Score", score);
                    if (frame.isValid() && score > bestScore) {
                        bestScore = score;
                        matchedGate = referenceId;
                    }
                }
                result.addProperty("matchedGate", matchedGate);
                result.addProperty("validStructure", frame.isValid());
                result.addProperty("mode", controller.mode().name());
                result.addProperty("ambiguous", controller.isAmbiguous());
                System.out.println(gson.toJson(result));
                System.out.flush();
            }
        }
    }

    private static SegmentationMask mask(JsonObject input) {
        JsonArray array = input.getAsJsonArray("labels");
        int[] labels = new int[array.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = array.get(i).getAsInt();
        return new SegmentationMask(input.get("width").getAsInt(), input.get("height").getAsInt(), labels);
    }

    private static List<StructureSignature> references(JsonArray array) {
        List<StructureSignature> result = new ArrayList<>();
        for (JsonElement element : array) {
            StructureSignature signature = StructureSignature.from(mask(element.getAsJsonObject()));
            if (!signature.isValid()) throw new IllegalArgumentException("Invalid reference");
            result.add(signature);
        }
        return result;
    }

    private static double best(StructureSignature frame, List<StructureSignature> references) {
        double score = 0;
        for (StructureSignature reference : references) score = Math.max(score, frame.similarity(reference));
        return score;
    }
}
