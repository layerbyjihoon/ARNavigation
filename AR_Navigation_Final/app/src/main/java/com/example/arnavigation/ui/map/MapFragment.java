package com.example.arnavigation.ui.map;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.arnavigation.ArNavigationApplication;
import com.example.arnavigation.R;
import com.example.arnavigation.databinding.FragmentMapBinding;
import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.DestinationSource;
import com.example.arnavigation.model.GeoPoint;
import com.example.arnavigation.navigation.NavigationAction;
import com.example.arnavigation.navigation.NavigationViewModel;
import com.example.arnavigation.ui.PermissionAware;
import com.example.arnavigation.ui.PermissionState;
import com.google.android.gms.maps.SupportMapFragment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Places search, map-tap destination selection, and route-preview screen. */
public final class MapFragment extends Fragment implements PermissionAware {
    private FragmentMapBinding binding;
    private MapNavigationHost host;
    private NavigationViewModel viewModel;
    private final MapRenderer mapRenderer = new MapRenderer();
    private PlaceSearchController placeSearchController;
    private final ArrayList<PlaceSuggestion> suggestions = new ArrayList<>();
    private PlaceSuggestionAdapter suggestionAdapter;
    private boolean suppressSearch;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof MapNavigationHost)) {
            throw new IllegalStateException("Host activity must implement MapNavigationHost");
        }
        host = (MapNavigationHost) context;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Restored fragments attach during Activity.super.onCreate, before the host session exists.
        viewModel = host.getNavigationViewModel();
        setupMap();
        setupSearch();
        setupActions();
        view.findViewById(R.id.final_choose_room).setOnClickListener(button ->
            ((com.example.arnavigation.ui.MainActivity) requireActivity()).chooseCultureRoom());
        updateLocationPermission();
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            mapRenderer.render(state);
            binding.destinationCard.render(state);
            binding.currentLocation.setVisibility(state.getDestination() == null ? View.VISIBLE : View.GONE);
            if (binding.searchHeading.getVisibility() == View.VISIBLE) binding.destinationCard.setVisibility(View.GONE);
        });
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentByTag("map");
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.map_container, mapFragment, "map")
                    .commitNow();
        }
        mapFragment.getMapAsync(map -> {
            if (binding == null) return;
            mapRenderer.attach(map);
            map.getUiSettings().setMyLocationButtonEnabled(false);
            updateLocationPermission();
            map.getUiSettings().setCompassEnabled(true);
            map.getUiSettings().setMapToolbarEnabled(false);
            map.setOnMapClickListener(point -> selectCandidate(new Destination(
                    String.format(Locale.ROOT, "map:%.6f,%.6f", point.latitude, point.longitude),
                    getString(R.string.coordinate_destination_name, point.latitude, point.longitude),
                    new GeoPoint(point.latitude, point.longitude),
                    DestinationSource.MAP_TAP,
                    null)));
        });
    }

    private void setupSearch() {
        suggestionAdapter = new PlaceSuggestionAdapter(requireContext());
        binding.placeSearch.setOnFocusChangeListener((v, focused) -> { if (focused) showSearch(true); });
        binding.searchBack.setOnClickListener(v -> {
            if (binding.searchHeading.getVisibility() == View.VISIBLE) {
                binding.placeSearch.clearFocus(); showSearch(false);
            } else {
                var activity = (com.example.arnavigation.ui.MainActivity) requireActivity();
                android.widget.PopupMenu menu = new android.widget.PopupMenu(requireContext(), v);
                if (activity.isPickingOutdoorDestination()) {
                    menu.getMenu().add(android.R.string.cancel).setOnMenuItemClickListener(item -> {
                        activity.finish(); return true;
                    });
                } else {
                    menu.getMenu().add("문화관 강의실").setOnMenuItemClickListener(item -> {
                        activity.chooseCultureRoom(); return true;
                    });
                    menu.getMenu().add(R.string.indoor_departure).setOnMenuItemClickListener(item -> {
                        activity.startIndoorDeparture(); return true;
                    });
                }
                menu.show();
            }
        });
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
            new androidx.activity.OnBackPressedCallback(true) {
                @Override public void handleOnBackPressed() {
                    if (binding.searchHeading.getVisibility() == View.VISIBLE) {
                        binding.placeSearch.clearFocus(); showSearch(false);
                    } else { setEnabled(false); requireActivity().getOnBackPressedDispatcher().onBackPressed(); }
                }
            });
        binding.placeSuggestions.setAdapter(suggestionAdapter);
        binding.placeSuggestions.setOnItemClickListener((parent, row, position, id) -> {
            if (position < 0 || position >= suggestions.size()) return;
            binding.searchProgress.setVisibility(View.VISIBLE);
            placeSearchController.selectSuggestion(
                    suggestions.get(position), new PlaceSelectionCallback() {
                        @Override
                        public void onDestination(Destination destination) {
                            if (binding == null) return;
                            binding.searchProgress.setVisibility(View.GONE);
                            binding.placeSuggestions.setVisibility(View.GONE);
                            suppressSearch = true;
                            binding.placeSearch.setText(destination.getName());
                            suppressSearch = false;
                            binding.placeSearch.clearFocus();
                            showSearch(false);
                            selectCandidate(destination);
                        }

                        @Override
                        public void onError(PlaceSearchError error) {
                            showSearchError(error);
                        }
                    });
        });

        ArNavigationApplication application =
                (ArNavigationApplication) requireActivity().getApplication();
        if (application.getApiAvailability().isPlacesAvailable()) {
            placeSearchController = new PlaceSearchController(requireContext());
            binding.placeSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!suppressSearch) search(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        } else {
            binding.placeSearch.setEnabled(false);
            binding.placeSearch.setHint(R.string.places_unavailable);
        }
    }

    private void search(String query) {
        suggestionAdapter.setQuery(query);
        placeSearchController.onQueryChanged(query, new PlaceSearchCallback() {
            @Override
            public void onSuggestions(List<PlaceSuggestion> results) {
                if (binding == null) return;
                suggestions.clear();
                suggestions.addAll(results);
                suggestionAdapter.clear();
                for (PlaceSuggestion result : results) {
                    suggestionAdapter.add(result);
                }
                suggestionAdapter.notifyDataSetChanged();
                binding.placeSuggestions.setVisibility(results.isEmpty() ? View.GONE : View.VISIBLE);
                binding.searchProgress.setVisibility(View.GONE);
            }

            @Override
            public void onError(PlaceSearchError error) {
                showSearchError(error);
            }
        });
        binding.searchProgress.setVisibility(query.trim().isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setupActions() {
        binding.currentLocation.setOnClickListener(v -> {
            mapRenderer.centerOnCurrentLocation();
            PermissionState permission = host.getLocationPermissionState();
            if (permission != PermissionState.GRANTED && permission != PermissionState.COARSE_ONLY)
                host.requestLocationPermission();
        });
        binding.destinationCard.configureActions(
                button -> {
                    mapRenderer.onDestinationConfirmed();
                    viewModel.dispatch(new NavigationAction.ConfirmDestination());
                },
                button -> host.openArTab(),
                button -> viewModel.dispatch(new NavigationAction.RetryRoute()),
                button -> {
                    binding.placeSearch.requestFocus();
                });
        binding.locationPermissionButton.setOnClickListener(button ->
                host.requestLocationPermission());
    }

    private void showSearch(boolean open) {
        ViewGroup.LayoutParams params = binding.searchPanel.getLayoutParams();
        params.height = open ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.searchPanel.setLayoutParams(params);
        binding.searchPanel.setBackgroundColor(open ? requireContext().getColor(R.color.nav_surface) : android.graphics.Color.TRANSPARENT);
        binding.searchHeading.setVisibility(open ? View.VISIBLE : View.GONE);
        binding.searchHelper.setVisibility(open ? View.VISIBLE : View.GONE);
        binding.searchBack.setText(open ? "‹" : "☰");
        binding.searchBack.setContentDescription(open ? "검색 닫기" : "메뉴 열기");
        binding.finalChooseRoom.setVisibility(View.GONE);
        com.example.arnavigation.navigation.NavigationUiState state = viewModel.getState().getValue();
        if (state != null && !open) binding.destinationCard.render(state);
        if (open && placeSearchController != null) search(binding.placeSearch.getText().toString());
        if (open) { binding.destinationCard.setVisibility(View.GONE); binding.currentLocation.setVisibility(View.GONE); binding.locationPermissionButton.setVisibility(View.GONE); }
        else {
            updateLocationPermission();
            binding.placeSuggestions.setVisibility(View.GONE);
            binding.currentLocation.setVisibility(state == null || state.getDestination() == null ? View.VISIBLE : View.GONE);
            android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            ime.hideSoftInputFromWindow(binding.placeSearch.getWindowToken(), 0);
        }
    }

    private void selectCandidate(Destination destination) {
        ((com.example.arnavigation.ui.MainActivity) requireActivity()).selectDestination(destination);
    }

    private void updateLocationPermission() {
        if (host == null) return;
        PermissionState state = host.getLocationPermissionState();
        boolean granted = state == PermissionState.GRANTED
                || state == PermissionState.COARSE_ONLY;
        mapRenderer.setLocationPermissionGranted(granted);
        if (binding != null) {
            binding.locationPermissionButton.setVisibility(granted || binding.searchHeading.getVisibility() == View.VISIBLE
                    ? View.GONE : View.VISIBLE);
            binding.locationPermissionButton.setText(state == PermissionState.PERMANENTLY_DENIED
                    ? R.string.open_location_settings : R.string.enable_location);
        }
    }

    @Override
    public void onPermissionStateChanged() {
        updateLocationPermission();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLocationPermission();
        mapRenderer.onMapOpened();
    }

    private void showSearchError(PlaceSearchError error) {
        if (binding == null) return;
        binding.searchProgress.setVisibility(View.GONE);
        Toast.makeText(
                requireContext(),
                getString(R.string.place_search_error, error.name()),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        if (placeSearchController != null) placeSearchController.close();
        mapRenderer.detach();
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        host = null;
        viewModel = null;
        super.onDetach();
    }
}
