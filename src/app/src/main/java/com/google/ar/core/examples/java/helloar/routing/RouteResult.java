package com.google.ar.core.examples.java.helloar.routing;

public final class RouteResult {
  public enum Status { SUCCESS, NO_ROUTE, INVALID_ENDPOINT }
  public final Status status;
  public final NavigationRoute route;
  public final String message;
  RouteResult(Status status, NavigationRoute route, String message) {
    this.status = status; this.route = route; this.message = message;
  }
  public boolean isSuccess() { return status == Status.SUCCESS; }
}
