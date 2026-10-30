package com.google.ar.core.examples.java.helloar;

import static org.junit.Assert.*;
import java.io.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ArRecordingStoreTest {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  @Test public void uniqueRecordingsSurviveStoreRecreationAndDelete() throws Exception {
    ArRecordingStore store = new ArRecordingStore(temp.getRoot());
    ArRecordingStore.Entry first = store.create(Long.MAX_VALUE);
    ArRecordingStore.Entry second = store.create(Long.MAX_VALUE);
    assertNotEquals(first.id, second.id); assertNotEquals(first.fileName, second.fileName);
    try (FileOutputStream out = new FileOutputStream(store.file(first))) { out.write(new byte[128]); }
    store.finish(first, 1234, null);
    ArRecordingStore reopened = new ArRecordingStore(temp.getRoot());
    ArRecordingStore.Entry saved = reopened.list().stream().filter(e -> e.id.equals(first.id)).findFirst().get();
    assertTrue(saved.playable()); assertEquals(128, saved.sizeBytes);
    assertEquals(1234, saved.durationMillis); assertTrue(saved.endedAt >= saved.startedAt);
    assertEquals(store.file(first).getAbsolutePath(), saved.path);
    reopened.delete(saved);
    assertFalse(store.file(first).exists()); assertEquals(1, reopened.list().size());
  }

  @Test public void insufficientSpaceRejectsBeforeCreatingFiles() throws Exception {
    ArRecordingStore store = new ArRecordingStore(temp.getRoot());
    try { store.create(0); fail(); } catch (IOException expected) { }
    assertEquals(0, store.directory.list().length);
  }

  @Test public void interruptedAndFailedRecordingsAreNotPlayable() throws Exception {
    ArRecordingStore store = new ArRecordingStore(temp.getRoot());
    ArRecordingStore.Entry interrupted = store.create(Long.MAX_VALUE);
    new ArRecordingStore(temp.getRoot()).recoverInterrupted();
    assertEquals("INTERRUPTED", store.list().get(0).status);
    assertFalse(store.list().get(0).playable());
    ArRecordingStore.Entry failed = store.create(Long.MAX_VALUE);
    store.finish(failed, 100, "disk error");
    assertEquals("FAILED", failed.status); assertFalse(failed.playable());
    assertEquals("disk error", store.list().stream().filter(e -> e.id.equals(failed.id)).findFirst().get().error);
  }

  @Test public void invalidPathsCannotEscapeRecordingDirectory() throws Exception {
    ArRecordingStore store = new ArRecordingStore(temp.getRoot());
    ArRecordingStore.Entry entry = store.create(Long.MAX_VALUE);
    File safe = store.file(entry);
    entry.fileName = "../outside.mp4";
    try { store.delete(entry); fail(); } catch (IOException expected) { }
    assertTrue(safe.exists());
  }

  @Test public void missingDirectoryAndMalformedMetadataDoNotCrashLibrary() throws Exception {
    File parentFile = temp.newFile();
    try { new ArRecordingStore(parentFile); fail(); } catch (IOException expected) { }
    ArRecordingStore store = new ArRecordingStore(temp.getRoot());
    try (FileWriter writer = new FileWriter(new File(store.directory, "broken.json"))) { writer.write("{broken"); }
    assertTrue(store.list().isEmpty());
  }

  @Test public void failedMetadataWritePreservesPreviousInformation() throws Exception {
    ArRecordingStore store = new ArRecordingStore(temp.getRoot());
    ArRecordingStore.Entry entry = store.create(Long.MAX_VALUE);
    assertTrue(new File(store.directory, entry.id + ".tmp").mkdir());
    try { store.finish(entry, 200, null); fail(); } catch (IOException expected) { }
    assertEquals("RECORDING", store.list().get(0).status);
    assertTrue(store.file(entry).isFile());
  }

  @Test public void interruptedMetadataReplacementRecoversBackup() throws Exception {
    ArRecordingStore store = new ArRecordingStore(temp.getRoot());
    ArRecordingStore.Entry entry = store.create(Long.MAX_VALUE);
    File metadata = new File(store.directory, entry.id + ".json");
    assertTrue(metadata.renameTo(new File(store.directory, entry.id + ".json.bak")));
    ArRecordingStore reopened = new ArRecordingStore(temp.getRoot());
    assertEquals(entry.id, reopened.list().get(0).id);
    assertTrue(metadata.isFile());
  }
}
