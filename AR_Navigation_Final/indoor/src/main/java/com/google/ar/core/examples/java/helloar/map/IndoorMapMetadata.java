package com.google.ar.core.examples.java.helloar.map;

public final class IndoorMapMetadata {
  public final String id, name, description, createdAt, updatedAt, author, source;
  public IndoorMapMetadata(String id, String name, String description, String createdAt,
      String updatedAt, String author, String source) {
    this.id = id; this.name = name; this.description = description; this.createdAt = createdAt;
    this.updatedAt = updatedAt; this.author = author; this.source = source;
  }
}
