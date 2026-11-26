package com.example.arnavigation.ui.map;

import android.app.Dialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.example.arnavigation.ArNavigationApplication;
import com.example.arnavigation.R;
import com.example.arnavigation.model.Destination;
import com.example.arnavigation.navigation.NavigationAction;
import com.example.arnavigation.ui.MainActivity;

/** Searches above the live AR screen; dismissing leaves the active journey intact. */
public final class DestinationSearchDialog extends DialogFragment {
    private PlaceSearchController controller;
    private PlaceSuggestionAdapter adapter;
    private TextView status;
    private boolean selecting;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.dialog_destination_search, container, false);
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        EditText search = view.findViewById(R.id.route_search);
        status = view.findViewById(R.id.route_search_status);
        ListView results = view.findViewById(R.id.route_search_results);
        adapter = new PlaceSuggestionAdapter(requireContext()); results.setAdapter(adapter);
        view.findViewById(R.id.close_search).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.keep_route).setOnClickListener(v -> dismiss());
        ArNavigationApplication app = (ArNavigationApplication) requireActivity().getApplication();
        if (!app.getApiAvailability().isPlacesAvailable()) {
            search.setEnabled(false); status.setText(R.string.places_unavailable); return;
        }
        controller = new PlaceSearchController(requireContext());
        controller.onQueryChanged("", new PlaceSearchCallback() {
            public void onSuggestions(java.util.List<PlaceSuggestion> values) {
                adapter.clear(); adapter.addAll(values); status.setText("");
            }
            public void onError(PlaceSearchError error) { showError(error); }
        });
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(android.text.Editable s) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                selecting = false; results.setEnabled(true); adapter.setQuery(s.toString());
                status.setText(s.toString().trim().isEmpty() ? "목적지를 검색해주세요." : "검색 중…");
                controller.onQueryChanged(s.toString(), new PlaceSearchCallback() {
                    public void onSuggestions(java.util.List<PlaceSuggestion> values) {
                        if (status == null) return;
                        adapter.clear(); adapter.addAll(values);
                        status.setText(values.isEmpty() ? "검색 결과가 없습니다." : "");
                    }
                    public void onError(PlaceSearchError error) { showError(error); }
                });
            }
        });
        results.setOnItemClickListener((parent, row, position, id) -> {
            if (selecting) return;
            selecting = true; results.setEnabled(false); status.setText("목적지 확인 중…");
            controller.selectSuggestion(adapter.getItem(position), new PlaceSelectionCallback() {
                public void onDestination(Destination destination) {
                    if (!isAdded() || status == null) return;
                    MainActivity activity = (MainActivity) requireActivity();
                    dismiss(); activity.openMapTab();
                    activity.selectDestination(destination);
                }
                public void onError(PlaceSearchError error) {
                    selecting = false; results.setEnabled(true); showError(error);
                }
            });
        });
    }
    private void showError(PlaceSearchError error) {
        if (status != null) status.setText(getString(R.string.place_search_error, error.name()));
    }
    @Override public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.TOP);
            int height = Math.min((int)(486 * getResources().getDisplayMetrics().density),
                (int)(getResources().getDisplayMetrics().heightPixels * .72f));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes(); params.dimAmount = .32f;
            window.setAttributes(params);
        }
    }
    @Override public void onDestroyView() {
        if (controller != null) controller.close();
        controller = null; status = null; adapter = null; super.onDestroyView();
    }
}
