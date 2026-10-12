package com.example.modelevaluation.model;

import android.graphics.RectF;

public record Detection(int classId, String label, float confidence, RectF normalizedBox) {}
