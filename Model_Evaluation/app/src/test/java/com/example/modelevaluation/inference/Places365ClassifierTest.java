package com.example.modelevaluation.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Places365ClassifierTest {
    @Test public void softmaxIsStableAndNormalized() {
        float[] result = Places365Classifier.softmax(new float[]{1001f, 1002f, 1003f});
        float sum = result[0] + result[1] + result[2];
        assertEquals(1f, sum, 0.0001f);
        assertTrue(result[2] > result[1]);
        assertTrue(result[1] > result[0]);
    }
}
