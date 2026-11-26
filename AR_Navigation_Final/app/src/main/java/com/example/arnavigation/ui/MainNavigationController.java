package com.example.arnavigation.ui;

import androidx.annotation.IdRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import com.example.arnavigation.R;
import com.example.arnavigation.ui.ar.ArNavigationFragment;
import com.example.arnavigation.ui.map.MapFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Keeps both tab fragments alive while only the selected screen is resumed. */
final class MainNavigationController {
    static final String TAB_MAP = "map";
    static final String TAB_AR = "ar";
    private static final String TAG_MAP = "main.map";
    private static final String TAG_AR = "main.ar";

    private final FragmentManager fragmentManager;
    private final BottomNavigationView bottomNavigation;
    private final int containerId;
    private final BooleanSupplier arSelectionAllowed;
    private String selectedTab;
    private boolean internalSelection;

    MainNavigationController(
            FragmentManager fragmentManager,
            BottomNavigationView bottomNavigation,
            @IdRes int containerId,
            BooleanSupplier arSelectionAllowed) {
        this.fragmentManager = Objects.requireNonNull(fragmentManager);
        this.bottomNavigation = Objects.requireNonNull(bottomNavigation);
        this.containerId = containerId;
        this.arSelectionAllowed = Objects.requireNonNull(arSelectionAllowed);
        bottomNavigation.setOnItemSelectedListener(item -> {
            if (internalSelection) return true;
            if (item.getItemId() == R.id.navigation_map) {
                select(TAB_MAP);
                return true;
            }
            if (item.getItemId() == R.id.navigation_ar) {
                if (!this.arSelectionAllowed.getAsBoolean()) return false;
                select(TAB_AR);
                return true;
            }
            return false;
        });
    }

    void restore(String tab) {
        select(TAB_AR.equals(tab) && arSelectionAllowed.getAsBoolean() ? TAB_AR : TAB_MAP);
    }

    void openMap() {
        bottomNavigation.setSelectedItemId(R.id.navigation_map);
        if (!TAB_MAP.equals(selectedTab)) select(TAB_MAP);
    }

    void openAr() {
        bottomNavigation.setSelectedItemId(R.id.navigation_ar);
        if (!TAB_AR.equals(selectedTab)) select(TAB_AR);
    }

    String getSelectedTab() {
        return selectedTab == null ? TAB_MAP : selectedTab;
    }

    private void select(String tab) {
        if (tab.equals(selectedTab) && activeFragment(tab) != null) return;
        if (fragmentManager.isStateSaved()) return;

        Fragment map = fragmentManager.findFragmentByTag(TAG_MAP);
        Fragment ar = fragmentManager.findFragmentByTag(TAG_AR);
        FragmentTransaction transaction =
                fragmentManager.beginTransaction().setReorderingAllowed(true);

        if (TAB_MAP.equals(tab) && map == null) {
            map = new MapFragment();
            transaction.add(containerId, map, TAG_MAP);
        } else if (TAB_AR.equals(tab) && ar == null) {
            ar = new ArNavigationFragment();
            transaction.add(containerId, ar, TAG_AR);
        }

        if (map != null) {
            if (TAB_MAP.equals(tab)) {
                transaction.show(map).setMaxLifecycle(map, Lifecycle.State.RESUMED);
            } else {
                transaction.hide(map).setMaxLifecycle(map, Lifecycle.State.STARTED);
            }
        }
        if (ar != null) {
            if (TAB_AR.equals(tab)) {
                transaction.show(ar).setMaxLifecycle(ar, Lifecycle.State.RESUMED);
            } else {
                transaction.hide(ar).setMaxLifecycle(ar, Lifecycle.State.STARTED);
            }
        }
        Fragment active = TAB_MAP.equals(tab) ? map : ar;
        if (active != null) transaction.setPrimaryNavigationFragment(active);
        transaction.commit();
        selectedTab = tab;
        int itemId = TAB_MAP.equals(tab) ? R.id.navigation_map : R.id.navigation_ar;
        if (bottomNavigation.getSelectedItemId() != itemId) {
            internalSelection = true;
            try {
                bottomNavigation.setSelectedItemId(itemId);
            } finally {
                internalSelection = false;
            }
        }
    }

    private Fragment activeFragment(String tab) {
        return fragmentManager.findFragmentByTag(TAB_MAP.equals(tab) ? TAG_MAP : TAG_AR);
    }
}
