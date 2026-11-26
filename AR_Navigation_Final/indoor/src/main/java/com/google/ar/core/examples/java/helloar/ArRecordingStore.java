package com.google.ar.core.examples.java.helloar;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** App-private MP4 datasets and durable sidecars. No external storage permissions. */
final class ArRecordingStore {
  static final long START_RESERVE_BYTES = 128L * 1024 * 1024;
  static final long STOP_RESERVE_BYTES = 32L * 1024 * 1024;
  final File directory;
  private final Gson gson = new Gson();

  static final class Entry {
    String id, fileName, path, status, error;
    RecordingStartPoint startPoint;
    String mapOrientationSource;
    Double mapCounterClockwiseDegrees;
    long startedAt, endedAt, durationMillis, sizeBytes;
    boolean playable() { return "COMPLETE".equals(status) && sizeBytes > 0; }
  }

  ArRecordingStore(File filesDir) throws IOException {
    directory = new File(filesDir, "ar_recordings");
    if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("녹화 폴더를 만들 수 없습니다.");
    File[] backups = directory.listFiles((dir, name) -> name.endsWith(".json.bak"));
    if (backups != null) for (File backup : backups) {
      File original = new File(directory, backup.getName().substring(0, backup.getName().length() - 4));
      if (!original.exists() && !backup.renameTo(original))
        throw new IOException("녹화 정보를 복구하지 못했습니다.");
    }
  }

  synchronized Entry create(long availableBytes) throws IOException {
    return create(availableBytes, null);
  }

  synchronized Entry create(long availableBytes, RecordingStartPoint startPoint) throws IOException {
    if (availableBytes < START_RESERVE_BYTES) throw new IOException("녹화하려면 저장 공간이 128MB 이상 필요합니다.");
    Entry entry = new Entry();
    entry.id = UUID.randomUUID().toString();
    entry.startPoint = startPoint;
    if (startPoint != null && MapOrientation.usesSensor(startPoint.mapAsset)) {
      entry.mapOrientationSource = MapOrientation.SENSOR_SOURCE;
      entry.mapCounterClockwiseDegrees = MapOrientation.MUNHWAGWAN_CCW_DEGREES;
    }
    entry.startedAt = System.currentTimeMillis();
    entry.fileName = "ar_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        .format(new Date(entry.startedAt)) + "_" + entry.id + ".mp4";
    entry.path = new File(directory, entry.fileName).getAbsolutePath();
    entry.status = "RECORDING";
    if (!file(entry).createNewFile()) throw new IOException("녹화 파일이 이미 있습니다.");
    try { save(entry); }
    catch (IOException e) { file(entry).delete(); throw e; }
    return entry;
  }

  synchronized void finish(Entry entry, long elapsedMillis, String error) throws IOException {
    entry.endedAt = System.currentTimeMillis();
    entry.durationMillis = Math.max(0, elapsedMillis);
    entry.sizeBytes = file(entry).length();
    entry.status = error == null && entry.sizeBytes > 0 ? "COMPLETE" : "FAILED";
    entry.error = error;
    save(entry);
  }

  synchronized List<Entry> list() {
    List<Entry> result = new ArrayList<>();
    File[] sidecars = directory.listFiles((dir, name) -> name.endsWith(".json"));
    if (sidecars == null) return result;
    for (File sidecar : sidecars) {
      try (Reader reader = new InputStreamReader(new FileInputStream(sidecar), StandardCharsets.UTF_8)) {
        Entry entry = gson.fromJson(reader, Entry.class);
        if (entry == null || entry.id == null || !sidecar.getName().equals(entry.id + ".json")) continue;
        File mp4 = file(entry); // Validate names before exposing or deleting any path.
        if (!mp4.isFile()) continue;
        entry.path = mp4.getAbsolutePath();
        entry.sizeBytes = mp4.length();
        result.add(entry);
      } catch (IOException | RuntimeException ignored) { /* Keep unreadable files for recovery. */ }
    }
    result.sort((a, b) -> Long.compare(b.startedAt, a.startedAt));
    return result;
  }

  synchronized void recoverInterrupted() throws IOException {
    for (Entry entry : list()) if ("RECORDING".equals(entry.status)) {
      entry.status = "INTERRUPTED";
      entry.error = "앱이 종료되어 정상 녹화 종료를 확인하지 못했습니다.";
      save(entry);
    }
  }

  synchronized void delete(Entry entry) throws IOException {
    File mp4 = file(entry), metadata = metadata(entry);
    if (mp4.exists() && !mp4.delete()) throw new IOException("녹화 파일을 삭제하지 못했습니다.");
    if (metadata.exists() && !metadata.delete()) throw new IOException("녹화 정보를 삭제하지 못했습니다.");
    File backup = new File(directory, entry.id + ".json.bak");
    if (backup.exists() && !backup.delete()) throw new IOException("녹화 정보 백업을 삭제하지 못했습니다.");
  }

  File file(Entry entry) throws IOException {
    if (entry == null || entry.id == null || !entry.id.matches("[a-f0-9-]{36}")
        || entry.fileName == null || !entry.fileName.matches("ar_[0-9]{8}_[0-9]{6}_[a-f0-9-]{36}\\.mp4"))
      throw new IOException("잘못된 녹화 정보입니다.");
    File file = new File(directory, entry.fileName);
    if (!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()))
      throw new IOException("잘못된 녹화 경로입니다.");
    return file;
  }

  private File metadata(Entry entry) throws IOException {
    file(entry);
    return new File(directory, entry.id + ".json");
  }

  private void save(Entry entry) throws IOException {
    File target = metadata(entry), temp = new File(directory, entry.id + ".tmp");
    try (FileOutputStream stream = new FileOutputStream(temp)) {
      stream.write(gson.toJson(entry).getBytes(StandardCharsets.UTF_8));
      stream.getFD().sync();
    }
    File backup = new File(directory, entry.id + ".json.bak");
    if (backup.exists() && !backup.delete()) throw new IOException("녹화 정보 백업을 갱신하지 못했습니다.");
    if (target.exists() && !target.renameTo(backup)) throw new IOException("녹화 정보를 보존하지 못했습니다.");
    if (!temp.renameTo(target)) {
      backup.renameTo(target);
      throw new IOException("녹화 정보를 저장하지 못했습니다.");
    }
    backup.delete();
  }
}
