package com.example.arnavigation.ui.map;

import java.util.Objects;

/** SDK-independent autocomplete row. */
public final class PlaceSuggestion {
    private final String placeId;
    private final String primaryText;
    private final String secondaryText;

    public PlaceSuggestion(String placeId, String primaryText, String secondaryText) {
        this.placeId = requireText(placeId, "placeId");
        this.primaryText = requireText(primaryText, "primaryText");
        this.secondaryText = secondaryText == null ? "" : secondaryText.trim();
    }

    public String getPlaceId() { return placeId; }
    public String getPrimaryText() { return primaryText; }
    public String getSecondaryText() { return secondaryText; }

    public String getDisplayText() {
        return secondaryText.isEmpty() ? primaryText : primaryText + "\n" + secondaryText;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.trim().isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
