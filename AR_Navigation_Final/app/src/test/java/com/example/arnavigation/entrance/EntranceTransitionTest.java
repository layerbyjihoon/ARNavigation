package com.example.arnavigation.entrance;

import static org.junit.Assert.*;
import com.google.gson.Gson;
import java.io.FileReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class EntranceTransitionTest {
    static SegmentationMask inside() {
        int[] a = new int[64];
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            a[y * 8 + x] = y < 2 ? 5 : y >= 6 ? 3 : 0;
        return new SegmentationMask(8, 8, a);
    }
    static SegmentationMask outside() {
        int[] a = new int[64];
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            a[y * 8 + x] = y < 2 ? 2 : y >= 6 ? 11 : 1;
        return new SegmentationMask(8, 8, a);
    }
    static EntranceProfile profile() {
        return new EntranceProfile("entry", "building", "map", "node", "F1", 37, 127,
                Collections.singletonList(inside()), Collections.singletonList(outside()));
    }
    static EntranceTransitionController scanning() {
        EntranceTransitionController c = new EntranceTransitionController(
                EntranceTransitionController.Config.defaults());
        c.select(profile()); c.startManualScan(0); return c;
    }
    static void stable(EntranceTransitionController c) {
        for (long t : new long[]{1000, 1334, 1668, 2002}) c.mask(c.generation(), t, t, inside());
    }

    @Test public void noMasksMeansNoScanAndNoAutomaticTransition() throws Exception {
        EntranceTransitionController c = scanning();
        try (FileReader reader = new FileReader("src/main/assets/entrances/pending-profile.json")) {
            c.select(EntranceProfile.read(reader));
        }
        c.startManualScan(0); c.location(0, 0, 1, 0, 0); c.location(0, 0, 1, 1000, 1000);
        assertEquals(EntranceTransitionController.State.UNCONFIGURED, c.state());
        assertFalse(c.shouldRunVision());
    }

    @Test public void activatesAfterTwoFreshAccurateNearLocationsAndRetainsScanOnGpsLoss() {
        EntranceTransitionController c = scanning(); c.select(profile());
        c.location(37, 127, 50, 0, 0); // Too inaccurate.
        c.location(37, 127, 5, 100, 10000); // Stale.
        assertFalse(c.shouldRunVision());
        c.location(37, 127, 5, 11000, 11000);
        assertFalse(c.shouldRunVision());
        c.location(37, 127, 5, 12000, 12000);
        assertTrue(c.shouldRunVision());
        c.location(38, 127, 100, 13000, 13000);
        assertTrue(c.shouldRunVision());
    }

    @Test public void staleNearLocationCannotCombineWithMuchLaterLocation() {
        EntranceTransitionController c = scanning(); c.select(profile());
        c.location(37, 127, 5, 0, 0);
        c.location(37, 127, 5, 10000, 10000);
        assertFalse(c.shouldRunVision());
        c.location(37, 127, 5, 11000, 11000);
        assertTrue(c.shouldRunVision());
    }

    @Test public void farSamplesNeedFiveSecondsAndDetectedIndoorIgnoresGpsJumps() {
        EntranceTransitionController c = scanning();
        c.location(38, 127, 5, 1000, 1000);
        c.location(38, 127, 5, 5000, 5000);
        assertTrue(c.shouldRunVision());
        c.location(38, 127, 5, 6000, 6000);
        assertEquals(EntranceTransitionController.State.OUTDOOR, c.state());
        c.select(profile()); c.startManualScan(0); stable(c);
        c.confirmEntry(c.generation(), 2002, 2002);
        c.location(38, 127, 5, 10000, 10000);
        assertEquals(EntranceTransitionController.State.INDOOR_DETECTED, c.state());
    }

    @Test public void matchingStructureNeedsEntryEvidenceAndArNeedsLocalizationAndTracking() {
        EntranceTransitionController c = scanning(); stable(c);
        assertEquals(EntranceTransitionController.State.INSIDE_CANDIDATE, c.state());
        long generation = c.generation();
        assertFalse(c.startIndoorNavigation(generation, true, true));
        c.confirmEntry(generation, 2002, 2002);
        assertEquals(EntranceTransitionController.State.INDOOR_DETECTED, c.state());
        assertFalse(c.startIndoorNavigation(generation, false, true));
        assertFalse(c.startIndoorNavigation(generation, true, false));
        assertTrue(c.startIndoorNavigation(generation, true, true));
    }

    @Test public void duplicateFramesAndBurstDoNotSatisfyTimeRequirement() {
        EntranceTransitionController c = scanning();
        for (int i = 0; i < 10; i++) c.mask(c.generation(), 1000, 1000, inside());
        for (long t : new long[]{1100, 1200, 1300}) c.mask(c.generation(), t, t, inside());
        c.confirmEntry(c.generation(), 1300, 1300);
        assertEquals(EntranceTransitionController.State.SCANNING, c.state());
    }

    @Test public void staleResultSessionChangeAndTimeoutCannotTransition() {
        EntranceTransitionController c = scanning();
        long old = c.generation(); c.select(profile()); c.startManualScan(0);
        c.mask(old, 1000, 1000, inside());
        c.mask(c.generation(), 1000, 4000, inside());
        assertEquals(0, c.insideScore(), 0);
        c.tick(90000);
        c.mask(c.generation(), 90000, 90000, inside());
        assertEquals(EntranceTransitionController.State.TIMED_OUT, c.state());
        c.startManualScan(90001);
        assertTrue(c.shouldRunVision());
    }

    @Test public void negativeOrOccludedFramesBreakCandidateAndOldEvidenceExpires() {
        EntranceTransitionController c = scanning(); stable(c);
        c.mask(c.generation(), 2100, 2100, outside());
        c.confirmEntry(c.generation(), 2100, 2100);
        assertEquals(EntranceTransitionController.State.SCANNING, c.state());
        c = scanning(); stable(c); c.tick(5000);
        c.confirmEntry(c.generation(), 5000, 5000);
        assertEquals(EntranceTransitionController.State.SCANNING, c.state());
        int[] ignored = new int[64]; Arrays.fill(ignored, 12); // People.
        assertFalse(StructureSignature.from(new SegmentationMask(8, 8, ignored)).isValid());
    }

    @Test public void indistinguishableNegativeBlocksCandidate() {
        EntranceTransitionController c = scanning();
        c.select(new EntranceProfile("entry", "building", "map", "node", "F1", 37, 127,
                Collections.singletonList(inside()), Collections.singletonList(inside())));
        c.startManualScan(0); stable(c);
        assertEquals(EntranceTransitionController.State.SCANNING, c.state());
    }

    @Test public void structureComparesSpatialLayoutNotOnlyClassTotals() {
        StructureSignature a = StructureSignature.from(inside());
        int[] inverted = new int[64];
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            inverted[y * 8 + x] = inside().labelAt(x, 7 - y);
        assertEquals(1, a.similarity(a), 1e-9);
        assertTrue(a.similarity(StructureSignature.from(new SegmentationMask(8, 8, inverted))) < 1);
        assertTrue(a.similarity(StructureSignature.from(outside())) < .8);
    }

    @Test public void profileJsonCanLoadRealMasksLater() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1); root.put("modelId", EntranceProfile.MODEL_ID);
        root.put("viewPolicy", "upright-full-frame"); root.put("entranceId", "entry");
        root.put("buildingId", "building"); root.put("indoorMapId", "map");
        root.put("startNodeId", "node"); root.put("floorId", "F1");
        root.put("latitude", 37); root.put("longitude", 127);
        root.put("insideMasks", Collections.singletonList(maskJson(inside())));
        root.put("negativeMasks", Collections.singletonList(maskJson(outside())));
        EntranceProfile loaded = EntranceProfile.read(new StringReader(new Gson().toJson(root)));
        assertTrue(loaded.isReady()); assertEquals("entry", loaded.entranceId);
    }

    private static Map<String, Object> maskJson(SegmentationMask mask) {
        int[] labels = new int[mask.width * mask.height];
        for (int y = 0; y < mask.height; y++) for (int x = 0; x < mask.width; x++)
            labels[y * mask.width + x] = mask.labelAt(x, y);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("width", mask.width); result.put("height", mask.height); result.put("labels", labels);
        return result;
    }

    @Test public void decoderUsesOutputIdsAndIgnoresUncertainLogits() {
        float[][][] logits = new float[150][2][2];
        for (float[] row : logits[3]) Arrays.fill(row, 20);
        assertEquals(3, SegformerLogits.decode(logits, 4, 4, .35f).labelAt(0, 0));
        logits = new float[150][2][2];
        assertEquals(-1, SegformerLogits.decode(logits, 4, 4, .35f).labelAt(0, 0));
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsNanLogits() {
        float[][][] logits = new float[150][2][2]; logits[0][0][0] = Float.NaN;
        SegformerLogits.decode(logits, 4, 4, .35f);
    }

    @Test public void interpolatesLogitsBeforeChoosingClass() {
        float[][][] logits = new float[150][2][2];
        for (float[][] channel : logits) for (float[] row : channel) Arrays.fill(row, -20);
        for (int y = 0; y < 2; y++) {
            logits[0][y][0] = 2; logits[0][y][1] = -8;
            logits[1][y][0] = 0; logits[1][y][1] = 0;
        }
        SegmentationMask decoded = SegformerLogits.decode(logits, 4, 4, .35f);
        assertEquals(0, decoded.labelAt(0, 0));
        assertEquals(1, decoded.labelAt(1, 0)); // Nearest-resized argmax mask would incorrectly be 0.
    }
}
