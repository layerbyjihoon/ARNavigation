package com.google.ar.core.examples.java.helloar.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/** Deep snapshots for application-defined JSON properties. */
final class ImmutableValues {
  static Map<String, Object> object(Map<String, Object> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    source.forEach((key, value) -> result.put(key, copy(value)));
    return Collections.unmodifiableMap(result);
  }
  private static Object copy(Object value) {
    if (value instanceof Map) {
      Map<String, Object> result = new LinkedHashMap<>();
      ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), copy(item)));
      return Collections.unmodifiableMap(result);
    }
    if (value instanceof List) {
      List<Object> result = new ArrayList<>();
      for (Object item : (List<?>) value) result.add(copy(item));
      return Collections.unmodifiableList(result);
    }
    return value;
  }
}
