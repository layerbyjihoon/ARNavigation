package com.example.arnavigation.ui.map;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.example.arnavigation.R;
import com.example.arnavigation.navigation.NavigationStage;
import com.example.arnavigation.navigation.NavigationUiState;
import com.google.android.material.card.MaterialCardView;
import java.util.Locale;

/** Bottom card for candidate confirmation, route progress, errors, and AR start. */
public final class DestinationCardView extends MaterialCardView {
    private TextView title;
    private TextView status;
    private Button primaryButton;
    private Button retryButton;
    private Button changeButton;

    public DestinationCardView(Context context) { this(context, null); }

    public DestinationCardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.view_destination_card_content, this, true);
        title = findViewById(R.id.destination_title);
        status = findViewById(R.id.destination_status);
        primaryButton = findViewById(R.id.destination_primary_action);
        retryButton = findViewById(R.id.destination_retry_action);
        changeButton = findViewById(R.id.destination_change_action);
        setVisibility(GONE);
    }

    public void setOnConfirmClickListener(@Nullable OnClickListener listener) {
        primaryButton.setTag(R.id.confirm_listener_tag, listener);
    }

    public void setOnStartArClickListener(@Nullable OnClickListener listener) {
        primaryButton.setTag(R.id.start_ar_listener_tag, listener);
    }

    public void setOnRetryClickListener(@Nullable OnClickListener listener) {
        retryButton.setOnClickListener(listener);
    }

    public void setOnChangeClickListener(@Nullable OnClickListener listener) {
        changeButton.setOnClickListener(listener);
    }

    public void render(NavigationUiState state) {
        if (state.getDestination() == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        title.setText(state.getDestination().getName());
        retryButton.setVisibility(GONE);
        changeButton.setVisibility(VISIBLE);
        NavigationStage stage = state.getStage();
        if (stage == NavigationStage.DESTINATION_SELECTED) {
            status.setText(state.getRouteError() == null
                    ? getResources().getString(R.string.destination_candidate_status)
                    : routeErrorText(state));
            primaryButton.setText(R.string.confirm_destination);
            primaryButton.setEnabled(true);
            primaryButton.setOnClickListener((OnClickListener) primaryButton.getTag(R.id.confirm_listener_tag));
            retryButton.setVisibility(state.getRouteError() == null ? GONE : VISIBLE);
        } else if (stage == NavigationStage.ROUTE_LOADING) {
            status.setText(R.string.route_loading);
            primaryButton.setText(R.string.confirm_destination);
            primaryButton.setEnabled(false);
        } else if (state.getRoute() != null) {
            status.setText(String.format(
                    Locale.getDefault(),
                    getResources().getString(R.string.route_summary_format),
                    state.getRoute().getDistanceMeters(),
                    Math.max(1L, (state.getRoute().getDurationSeconds() + 59L) / 60L)));
            if (stage == NavigationStage.ROUTE_READY) {
                primaryButton.setText(R.string.start_ar_navigation);
                primaryButton.setEnabled(state.getLocationStatus().isMapUsable());
                primaryButton.setOnClickListener(
                        (OnClickListener) primaryButton.getTag(R.id.start_ar_listener_tag));
            } else {
                primaryButton.setText(stage == NavigationStage.ARRIVED
                        ? R.string.arrived : R.string.show_ar_guide);
                primaryButton.setEnabled(stage == NavigationStage.NAVIGATING);
                primaryButton.setOnClickListener((OnClickListener) primaryButton.getTag(R.id.start_ar_listener_tag));
            }
        }
    }

    public void configureActions(
            @Nullable OnClickListener confirm,
            @Nullable OnClickListener startAr,
            @Nullable OnClickListener retry,
            @Nullable OnClickListener change) {
        primaryButton.setTag(R.id.confirm_listener_tag, confirm);
        primaryButton.setTag(R.id.start_ar_listener_tag, startAr);
        retryButton.setOnClickListener(retry);
        changeButton.setOnClickListener(change);
    }

    private String routeErrorText(NavigationUiState state) {
        return getResources().getString(
                R.string.route_error_format,
                state.getRouteError().name());
    }
}
