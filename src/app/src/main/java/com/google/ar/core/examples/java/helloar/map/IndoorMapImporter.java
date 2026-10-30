package com.google.ar.core.examples.java.helloar.map;

import java.io.InputStream;
/** External adapters must convert geographic/standard-specific data to the canonical meter graph. */
public interface IndoorMapImporter {
  String format();
  MapLoadResult importMap(InputStream input);
}
