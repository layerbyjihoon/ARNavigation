package com.example.modelevaluation.inference;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import androidx.camera.core.ImageProxy;

public final class BitmapPreprocessor {
    private BitmapPreprocessor() {}

    public static Bitmap fromRgba8888(ImageProxy image) {
        Bitmap bitmap = image.toBitmap();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int rotation = image.getImageInfo().getRotationDegrees();
        if (rotation == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    public static float[] placesTensor(Bitmap source, int size) {
        float sourceRatio = source.getWidth() / (float) source.getHeight();
        int cropWidth = source.getWidth();
        int cropHeight = source.getHeight();
        if (sourceRatio > 1f) cropWidth = source.getHeight();
        else cropHeight = source.getWidth();
        int left = (source.getWidth() - cropWidth) / 2;
        int top = (source.getHeight() - cropHeight) / 2;
        Bitmap cropped = Bitmap.createBitmap(source, left, top, cropWidth, cropHeight);
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, size, size, true);
        int[] pixels = new int[size * size];
        scaled.getPixels(pixels, 0, size, 0, 0, size, size);
        float[] tensor = new float[3 * size * size];
        float[] mean = {0.485f, 0.456f, 0.406f};
        float[] std = {0.229f, 0.224f, 0.225f};
        int area = size * size;
        for (int i = 0; i < area; i++) {
            int pixel = pixels[i];
            tensor[i] = (Color.red(pixel) / 255f - mean[0]) / std[0];
            tensor[area + i] = (Color.green(pixel) / 255f - mean[1]) / std[1];
            tensor[2 * area + i] = (Color.blue(pixel) / 255f - mean[2]) / std[2];
        }
        if (scaled != cropped) scaled.recycle();
        if (cropped != source) cropped.recycle();
        return tensor;
    }

    public static LetterboxResult yoloTensor(Bitmap source, int size) {
        float scale = Math.min(size / (float) source.getWidth(), size / (float) source.getHeight());
        int resizedWidth = Math.max(1, Math.round(source.getWidth() * scale));
        int resizedHeight = Math.max(1, Math.round(source.getHeight() * scale));
        int padX = (size - resizedWidth) / 2;
        int padY = (size - resizedHeight) / 2;
        Bitmap canvasBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBitmap);
        canvas.drawColor(Color.rgb(114, 114, 114));
        canvas.drawBitmap(source, null,
                new android.graphics.Rect(padX, padY, padX + resizedWidth, padY + resizedHeight),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        int[] pixels = new int[size * size];
        canvasBitmap.getPixels(pixels, 0, size, 0, 0, size, size);
        float[] tensor = new float[3 * size * size];
        int area = size * size;
        for (int i = 0; i < area; i++) {
            int pixel = pixels[i];
            tensor[i] = Color.red(pixel) / 255f;
            tensor[area + i] = Color.green(pixel) / 255f;
            tensor[2 * area + i] = Color.blue(pixel) / 255f;
        }
        canvasBitmap.recycle();
        return new LetterboxResult(tensor, scale, padX, padY, source.getWidth(), source.getHeight());
    }

    public record LetterboxResult(float[] tensor, float scale, int padX, int padY,
                                  int sourceWidth, int sourceHeight) {}
}
