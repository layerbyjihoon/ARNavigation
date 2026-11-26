package com.example.arnavigation.ui;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.arnavigation.ArNavigationApplication;
import com.example.arnavigation.R;
import com.example.arnavigation.databinding.ActivityMainBinding;
import com.example.arnavigation.navigation.NavigationAction;
import com.example.arnavigation.navigation.NavigationViewModel;
import com.example.arnavigation.navigation.NavigationStage;
import com.example.arnavigation.model.Destination;
import com.example.arnavigation.model.DestinationSource;
import com.example.arnavigation.model.GeoPoint;
import com.google.ar.core.examples.java.helloar.HelloArActivity;
import com.example.arnavigation.ui.ar.ArNavigationHost;
import com.example.arnavigation.ui.map.MapNavigationHost;

/** Activity-scoped navigation session host for the map and AR tabs. */
public final class MainActivity extends AppCompatActivity
        implements MapNavigationHost, ArNavigationHost {
    private static final String KEY_SELECTED_TAB = "selected_tab";
    private static final String PICK_OUTDOOR_DESTINATION = "pick_outdoor_destination";

    private ActivityMainBinding binding;
    private NavigationViewModel navigationViewModel;
    private PermissionCoordinator permissionCoordinator;
    private MainNavigationController navigationController;
    private static final int PICK_ROOM = 610, INDOOR_GUIDE = 611;
    private com.example.arnavigation.journey.JourneyState journey;
    private com.example.arnavigation.model.Destination pendingEntrance;
    private boolean activityResumed;
    private String outdoorContinuationId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        ArNavigationApplication application = (ArNavigationApplication) getApplication();
        navigationViewModel = new ViewModelProvider(
                this,
                new NavigationViewModelFactory(application.getNavigationDependencies()))
                .get(NavigationViewModel.class);
        journey = new ViewModelProvider(this).get(com.example.arnavigation.journey.JourneyState.class);
        navigationViewModel.setIndoorTransferPending(journey.route() != null);
        if (savedInstanceState != null) {
            outdoorContinuationId = savedInstanceState.getString("outdoor_continuation");
            if (savedInstanceState.containsKey("entrance_lat")) pendingEntrance = new com.example.arnavigation.model.Destination(
                "pending-culture", "문화관 출입구", new com.example.arnavigation.model.GeoPoint(
                savedInstanceState.getDouble("entrance_lat"), savedInstanceState.getDouble("entrance_lng")),
                com.example.arnavigation.model.DestinationSource.MAP_TAP, null);
        }
        permissionCoordinator = new PermissionCoordinator(this, this::notifyPermissionChanged);
        navigationController = new MainNavigationController(
                getSupportFragmentManager(),
                binding.mainBottomNavigation,
                R.id.main_fragment_container,
                this::allowArSelection);

        if (application.getApiAvailability().isMapsAvailable()
                && application.getApiAvailability().isPlacesAvailable()
                && application.getApiAvailability().isRoutesAvailable()) {
            binding.configurationStatus.setVisibility(View.GONE);
        } else {
            binding.configurationStatus.setText(
                    application.getApiAvailability().getUserFacingSummary());
            binding.configurationStatus.setVisibility(View.VISIBLE);
        }
        journey.launchReady().observe(this, ready -> {
            if (Boolean.TRUE.equals(ready) && activityResumed) startIndoorGuide();
        });
        navigationViewModel.getState().observe(this, state -> {
            if (!journey.gate.isHandingOff() && !journey.isLaunchReady() && journey.destinationId() != null && (state.getDestination() == null
                    || !journey.destinationId().equals(state.getDestination().getId()))) {
                journey.clear(); navigationViewModel.setIndoorTransferPending(false);
            }
            continueOutdoorArIfReady();
        });
        navigationController.restore(savedInstanceState == null
                ? MainNavigationController.TAB_MAP
                : savedInstanceState.getString(
                        KEY_SELECTED_TAB, MainNavigationController.TAB_MAP));
    }

    public com.example.arnavigation.journey.JourneyState getJourney() { return journey; }

    /** Culture Hall requests a room before replacing the current journey. Canceling keeps it intact. */
    public void selectDestination(com.example.arnavigation.model.Destination destination) {
        if (isPickingOutdoorDestination()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(destination.getName())
                .setMessage(R.string.outdoor_destination_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.outdoor_destination_confirm, (dialog, which) -> {
                    Bundle selected = new Bundle();
                    selected.putString("id", destination.getId());
                    selected.putString("name", destination.getName());
                    selected.putDouble("latitude", destination.getPoint().getLatitude());
                    selected.putDouble("longitude", destination.getPoint().getLongitude());
                    selected.putString("source", destination.getSource().name());
                    selected.putString("place_id", destination.getPlaceId());
                    setResult(RESULT_OK, new Intent().putExtra(HelloArActivity.EXTRA_OUTDOOR_DESTINATION, selected));
                    finish();
                }).show();
        } else if ("문화관".equals(destination.getName())) {
            chooseCultureRoom(destination);
        } else {
            navigationViewModel.dispatch(new NavigationAction.SelectDestination(destination));
        }
    }

    public boolean isPickingOutdoorDestination() {
        return getIntent().getBooleanExtra(PICK_OUTDOOR_DESTINATION, false);
    }

    public void startIndoorDeparture() {
        if (isPickingOutdoorDestination() || isFinishing()) return;
        navigationViewModel.dispatch(new NavigationAction.StopNavigation());
        navigationController.prepareIndoorScreen();
        startActivityForResult(indoorIntent().putExtra(HelloArActivity.EXTRA_BEGIN_OUTDOOR_DEPARTURE, true),
            INDOOR_GUIDE);
    }

    public void chooseCultureRoom() {
        com.example.arnavigation.navigation.NavigationUiState state = navigationViewModel.getState().getValue();
        com.example.arnavigation.model.Destination destination = state == null ? null : state.getDestination();
        boolean cultureRoom = destination != null && destination.getId().startsWith("culture-room-");
        if (destination == null || !("문화관".equals(destination.getName()) || cultureRoom)) {
            Toast.makeText(this, R.string.final_choose_culture_first, Toast.LENGTH_LONG).show(); return;
        }
        chooseCultureRoom(destination);
    }

    private void chooseCultureRoom(com.example.arnavigation.model.Destination entrance) {
        if (pendingEntrance != null) return;
        pendingEntrance = entrance;
        startActivityForResult(new android.content.Intent(this,
                com.google.ar.core.examples.java.helloar.MapRouteActivity.class)
                .putExtra("final_pick_room", true).putExtra("map_index", 0), PICK_ROOM);
    }

    @Override protected void onActivityResult(int request, int result, @Nullable android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_ROOM) {
            if (result == RESULT_OK && data != null && pendingEntrance != null) {
                String id = "culture-room-" + java.util.UUID.randomUUID();
                com.example.arnavigation.model.Destination destination = new com.example.arnavigation.model.Destination(
                    id, getString(R.string.final_destination_format, data.getStringExtra("final_room_name")),
                    com.example.arnavigation.entrance.CultureHallEntrance.FRONT_POINT,
                    com.example.arnavigation.model.DestinationSource.MAP_TAP, null);
                // Select first: the state observer must not invalidate the new route against the old destination.
                navigationViewModel.dispatch(new NavigationAction.SelectDestination(destination));
                journey.set(id, data.getExtras()); navigationViewModel.setIndoorTransferPending(true);
                openMapTab();
                navigationViewModel.dispatch(new NavigationAction.ConfirmDestination());
            }
            pendingEntrance = null;
        } else if (request == INDOOR_GUIDE) {
            journey.clear(); navigationViewModel.setIndoorTransferPending(false); openMapTab();
            Bundle selected = result == RESULT_OK && data != null
                ? data.getBundleExtra(HelloArActivity.EXTRA_OUTDOOR_DESTINATION) : null;
            if (selected != null) {
                Destination destination = new Destination(selected.getString("id"), selected.getString("name"),
                    new GeoPoint(selected.getDouble("latitude"), selected.getDouble("longitude")),
                    DestinationSource.valueOf(selected.getString("source")), selected.getString("place_id"));
                outdoorContinuationId = destination.getId();
                navigationViewModel.dispatch(new NavigationAction.SelectDestination(destination));
                navigationViewModel.dispatch(new NavigationAction.ConfirmDestination());
            }
        }
    }

    private void continueOutdoorArIfReady() {
        if (outdoorContinuationId == null || !activityResumed) return;
        var state = navigationViewModel.getState().getValue();
        if (state == null) return;
        if (state.getDestination() == null || !outdoorContinuationId.equals(state.getDestination().getId())) {
            outdoorContinuationId = null;
        } else if (state.getStage() == NavigationStage.ROUTE_READY
                && permissionCoordinator.getLocationState() == PermissionState.GRANTED) {
            outdoorContinuationId = null;
            openArTab();
        }
    }

    public void completeIndoorHandoff(String destinationId, boolean success) {
        if (!java.util.Objects.equals(destinationId, journey.destinationId())) return;
        if (success) {
            journey.setLaunchReady(true);

        } else {
            journey.clear(); navigationViewModel.setIndoorTransferPending(false);
            if (!isDestroyed()) {
                Toast.makeText(this, R.string.final_camera_release_error, Toast.LENGTH_LONG).show();
                openMapTab();
            }
        }
    }

    public void startIndoorGuide() {
        if (journey.route() == null || !journey.isLaunchReady() || isFinishing()) return;
        Intent intent = indoorIntent();
        intent.putExtras(journey.route());
        intent.putExtra(com.google.ar.core.examples.java.helloar.HelloArActivity.EXTRA_AUTO_START_ON_FLOOR, true);
        journey.setLaunchReady(false);
        navigationController.prepareIndoorScreen();
        startActivityForResult(intent, INDOOR_GUIDE);
    }

    private Intent indoorIntent() {
        return new Intent(this, HelloArActivity.class).putExtra(HelloArActivity.EXTRA_OUTDOOR_PICKER,
            new Intent(this, MainActivity.class).putExtra(PICK_OUTDOOR_DESTINATION, true));
    }

    @NonNull
    @Override
    public NavigationViewModel getNavigationViewModel() {
        return navigationViewModel;
    }

    @Override
    public void openArTab() {
        if (allowArSelection()) navigationController.openAr();
    }

    @Override
    public void openMapTab() {
        navigationController.openMap();
    }

    @Override
    public PermissionState getLocationPermissionState() {
        return permissionCoordinator.getLocationState();
    }

    @Override
    public void requestLocationPermission() {
        permissionCoordinator.requestLocation();
    }

    @Override
    public PermissionState getCameraPermissionState() {
        return permissionCoordinator.getCameraState();
    }

    @Override
    public void requestCameraPermission() {
        permissionCoordinator.requestCamera();
    }

    private boolean allowArSelection() {
        if (isPickingOutdoorDestination()) return false;
        if (permissionCoordinator.getLocationState() == PermissionState.GRANTED) return true;
        Toast.makeText(this, R.string.precise_location_required, Toast.LENGTH_SHORT).show();
        permissionCoordinator.requestLocation();
        return false;
    }

    private void notifyPermissionChanged() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof PermissionAware) {
                ((PermissionAware) fragment).onPermissionStateChanged();
            }
        }
        continueOutdoorArIfReady();
    }

    @Override
    protected void onStart() {
        super.onStart();
        navigationViewModel.dispatch(new NavigationAction.ResumeNavigation());
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        notifyPermissionChanged();
        if (journey.isLaunchReady()) startIndoorGuide();
    }

    @Override protected void onPause() {
        activityResumed = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        navigationViewModel.dispatch(new NavigationAction.PauseNavigation());
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (pendingEntrance != null) {
            outState.putDouble("entrance_lat", pendingEntrance.getPoint().getLatitude());
            outState.putDouble("entrance_lng", pendingEntrance.getPoint().getLongitude());
        }
        outState.putString(KEY_SELECTED_TAB, navigationController.getSelectedTab());
        outState.putString("outdoor_continuation", outdoorContinuationId);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
