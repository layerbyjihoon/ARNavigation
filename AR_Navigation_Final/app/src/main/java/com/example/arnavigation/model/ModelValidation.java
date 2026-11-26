package com.example.arnavigation.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ModelValidation {
    private ModelValidation() {}

    static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " must not be null");
        }
        return value;
    }

    static String requireText(String value, String name) {
        requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static String optionalText(String value, String name) {
        if (value != null && value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must be null or non-blank");
        }
        return value;
    }

    static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    static double requireNonNegative(double value, String name) {
        requireFinite(value, name);
        if (value < 0.0d) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    static long requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    static <T> List<T> immutableCopy(List<T> source, String name) {
        requireNonNull(source, name);
        ArrayList<T> copy = new ArrayList<>(source.size());
        for (T item : source) {
            copy.add(requireNonNull(item, name + " element"));
        }
        return Collections.unmodifiableList(copy);
    }

    static List<String> immutableTextCopy(List<String> source, String name) {
        requireNonNull(source, name);
        ArrayList<String> copy = new ArrayList<>(source.size());
        for (String item : source) {
            copy.add(requireText(item, name + " element"));
        }
        return Collections.unmodifiableList(copy);
    }
}
