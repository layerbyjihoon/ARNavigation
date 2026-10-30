package com.google.ar.core.examples.java.helloar;

import android.content.Context;
import android.text.format.Formatter;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Small developer-only recording library; no route or location responsibilities. */
final class ArRecordingDialogs {
  interface Playback { void open(ArRecordingStore.Entry entry); }
  static void show(Context context, ArRecordingStore store, ArDatasetController datasets, Playback playback) {
    List<ArRecordingStore.Entry> entries = store.list();
    LinearLayout rows = new LinearLayout(context);
    rows.setOrientation(LinearLayout.VERTICAL);
    int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
    rows.setPadding(padding, padding, padding, padding);
    if (entries.isEmpty()) {
      TextView empty = new TextView(context); empty.setText("저장된 AR 녹화가 없습니다."); rows.addView(empty);
    }
    AlertDialog dialog = new AlertDialog.Builder(context).setTitle("AR 녹화 데이터")
        .setNegativeButton("닫기", null).create();
    for (ArRecordingStore.Entry entry : entries) {
      TextView details = new TextView(context);
      DateFormat date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
      details.setText(entry.fileName + "\n시작: " + date.format(new Date(entry.startedAt))
          + "\n출발 지점: " + RecordingStartPoint.describe(entry.startPoint)
          + (entry.startPoint == null ? "" : "\n" + entry.startPoint.coordinates())
          + (entry.startPoint == null ? "" : "\n녹화 기준: " + (MapOrientation.matchesRecording(entry)
              ? entry.startPoint.directionDescription()
              : entry.startPoint.direction == null ? "기존 수동 정렬" : entry.startPoint.direction.description()))
          + (entry.endedAt == 0 ? "" : "\n종료: " + date.format(new Date(entry.endedAt)))
          + String.format(java.util.Locale.getDefault(), "\n녹화 시간: %.1f초 · %s",
              entry.durationMillis / 1000.0, Formatter.formatFileSize(context, entry.sizeBytes))
          + "\n상태: " + statusLabel(entry.status)
          + (entry.error == null ? "" : "\n" + entry.error));
      details.setPadding(0, padding, 0, 0); rows.addView(details);
      LinearLayout buttons = new LinearLayout(context);
      Button play = new Button(context); play.setText("재생");
      boolean inUse = datasets.active() != null && datasets.active().id.equals(entry.id);
      play.setEnabled(entry.playable() && datasets.mode() == ArDatasetController.Mode.LIVE);
      play.setOnClickListener(v -> { dialog.dismiss(); playback.open(entry); });
      Button delete = new Button(context); delete.setText("삭제"); delete.setEnabled(!inUse);
      delete.setOnClickListener(v -> new AlertDialog.Builder(context)
          .setTitle("녹화 파일 삭제").setMessage(entry.fileName + " 파일을 삭제할까요?")
          .setNegativeButton("취소", null).setPositiveButton("삭제", (d, which) -> {
            // Recheck state after the confirmation dialog; recording may have changed.
            if (datasets.active() != null && datasets.active().id.equals(entry.id)) return;
            try { store.delete(entry); rows.removeView(details); rows.removeView(buttons); }
            catch (IOException e) { Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show(); }
          }).show());
      buttons.addView(play); buttons.addView(delete); rows.addView(buttons);
    }
    ScrollView scroll = new ScrollView(context); scroll.addView(rows); dialog.setView(scroll); dialog.show();
  }

  private static String statusLabel(String status) {
    if ("COMPLETE".equals(status)) return "저장 완료";
    if ("RECORDING".equals(status)) return "녹화 중";
    if ("INTERRUPTED".equals(status)) return "비정상 종료 · 재생 불가";
    return "저장 실패 · 재생 불가";
  }
}
