package com.google.ar.core.examples.java.helloar.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public final class MapValidationResult {
  public final List<MapValidationIssue> issues;
  public MapValidationResult(List<MapValidationIssue> issues) {
    this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
  }
  public boolean hasErrors() {
    for (MapValidationIssue issue : issues)
      if (issue.severity == MapValidationIssue.Severity.ERROR) return true;
    return false;
  }
  public long warningCount() {
    return issues.stream().filter(i -> i.severity == MapValidationIssue.Severity.WARNING).count();
  }
}
