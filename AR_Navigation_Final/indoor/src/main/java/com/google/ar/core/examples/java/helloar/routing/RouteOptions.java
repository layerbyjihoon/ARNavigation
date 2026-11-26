package com.google.ar.core.examples.java.helloar.routing;

public final class RouteOptions {
  public static final RouteOptions DEFAULT = new RouteOptions(false);
  public static final RouteOptions WHEELCHAIR = new RouteOptions(true);
  public final boolean wheelchairOnly;
  public RouteOptions(boolean wheelchairOnly) { this.wheelchairOnly = wheelchairOnly; }
}
