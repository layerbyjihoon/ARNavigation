package com.example.arnavigation.routing;

import com.example.arnavigation.model.OutdoorRoute;

/** Completion callback for one route request. */
public interface RouteCallback {
    void onSuccess(OutdoorRoute route);

    void onFailure(RouteError error);
}
