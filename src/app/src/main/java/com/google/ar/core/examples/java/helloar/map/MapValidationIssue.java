package com.google.ar.core.examples.java.helloar.map;

public final class MapValidationIssue {
  public enum Severity { ERROR, WARNING }
  public final Severity severity;
  public final String path, message;
  public MapValidationIssue(Severity severity, String path, String message) {
    this.severity = severity; this.path = path; this.message = message;
  }
  @Override public String toString() { return severity + " " + path + ": " + message; }
}
