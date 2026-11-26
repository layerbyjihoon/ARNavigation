package com.example.arnavigation.routing;

final class RouteMappingException extends Exception {
    private final RouteError error;

    RouteMappingException(RouteError error, String message) {
        super(message);
        this.error = error;
    }

    RouteMappingException(RouteError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    RouteError getError() {
        return error;
    }
}
