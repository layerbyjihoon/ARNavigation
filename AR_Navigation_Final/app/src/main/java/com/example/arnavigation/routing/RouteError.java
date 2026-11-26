package com.example.arnavigation.routing;

/** Stable error categories exposed outside the HTTP implementation. */
public enum RouteError {
    NETWORK,
    AUTHENTICATION,
    QUOTA,
    NO_ROUTE,
    MALFORMED_RESPONSE,
    CANCELED
}
