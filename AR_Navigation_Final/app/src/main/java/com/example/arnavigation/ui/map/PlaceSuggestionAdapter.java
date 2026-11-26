package com.example.arnavigation.ui.map;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.example.arnavigation.R;
import java.util.ArrayList;

/** Native two-line search rows using the Figma building vector. */
public final class PlaceSuggestionAdapter extends ArrayAdapter<PlaceSuggestion> {
    private String query = "";
    public PlaceSuggestionAdapter(Context context) {
        super(context, R.layout.row_place_suggestion, new ArrayList<>());
    }
    public void setQuery(String value) { query = value.trim(); }
    @Override public View getView(int position, View recycled, ViewGroup parent) {
        View row = recycled == null ? LayoutInflater.from(getContext())
            .inflate(R.layout.row_place_suggestion, parent, false) : recycled;
        PlaceSuggestion item = getItem(position);
        android.text.SpannableString title = new android.text.SpannableString(item.getPrimaryText());
        int match = query.isEmpty() ? -1 : item.getPrimaryText().toLowerCase(java.util.Locale.ROOT)
            .indexOf(query.toLowerCase(java.util.Locale.ROOT));
        if (match >= 0) {
            title.setSpan(new android.text.style.ForegroundColorSpan(getContext().getColor(R.color.nav_accent)),
                match, match + query.length(), 0);
            title.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                match, match + query.length(), 0);
        }
        ((TextView) row.findViewById(R.id.place_primary)).setText(title);
        ((TextView) row.findViewById(R.id.place_secondary)).setText(item.getSecondaryText());
        return row;
    }
}
