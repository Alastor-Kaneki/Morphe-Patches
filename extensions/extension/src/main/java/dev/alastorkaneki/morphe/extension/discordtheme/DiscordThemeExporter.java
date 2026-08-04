package dev.alastorkaneki.morphe.extension.discordtheme;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Captures Discord's current theme colors and exports CSS plus a privacy-safe PNG. */
public final class DiscordThemeExporter {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int PREVIEW_WIDTH = 1600;
    private static final int PREVIEW_HEIGHT = 900;
    private static final String EXPORT_FOLDER = "DiscordThemes";

    private DiscordThemeExporter() {
    }

    public interface Callback {
        void onComplete(Result result);
    }

    public static final class Result {
        public final boolean success;
        public final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static Result success(String message) {
            return new Result(true, message);
        }

        public static Result failure(String message) {
            return new Result(false, message == null ? "unknown error" : message);
        }
    }

    public static void export(
            Activity activity,
            View overlayButton,
            Callback callback
    ) {
        int oldVisibility = overlayButton.getVisibility();
        overlayButton.setVisibility(View.INVISIBLE);

        View decor = activity.getWindow().getDecorView();
        decor.postDelayed(() -> {
            Bitmap capture;
            try {
                capture = captureDecor(decor);
            } catch (Throwable error) {
                overlayButton.setVisibility(oldVisibility);
                callback.onComplete(Result.failure(error.getMessage()));
                return;
            }

            overlayButton.setVisibility(oldVisibility);
            EXECUTOR.execute(() -> {
                Result result;
                try {
                    ThemePalette palette = extractPalette(capture);
                    capture.recycle();

                    String timestamp = new SimpleDateFormat(
                            "yyyyMMdd-HHmmss",
                            Locale.US
                    ).format(new Date());
                    String baseName = "discord-custom-theme-" + timestamp;

                    String css = buildCss(palette, timestamp);
                    Bitmap preview = createPreview(palette, timestamp);
                    byte[] png = encodePng(preview);
                    preview.recycle();

                    saveFile(
                            activity,
                            baseName + ".css",
                            "text/css",
                            css.getBytes(StandardCharsets.UTF_8)
                    );
                    saveFile(
                            activity,
                            baseName + ".png",
                            "image/png",
                            png
                    );
                    result = Result.success(baseName);
                } catch (Throwable error) {
                    if (!capture.isRecycled()) capture.recycle();
                    result = Result.failure(error.getMessage());
                }

                Result finalResult = result;
                activity.runOnUiThread(() -> callback.onComplete(finalResult));
            });
        }, 90L);
    }

    private static Bitmap captureDecor(View decor) {
        int width = decor.getWidth();
        int height = decor.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("Discord window is not ready");
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        decor.draw(canvas);
        return bitmap;
    }

    private static ThemePalette extractPalette(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        float scale = Math.min(1.0f, 180.0f / Math.max(width, height));
        int sampleWidth = Math.max(1, Math.round(width * scale));
        int sampleHeight = Math.max(1, Math.round(height * scale));

        Bitmap sample = Bitmap.createScaledBitmap(
                source,
                sampleWidth,
                sampleHeight,
                true
        );
        int[] pixels = new int[sampleWidth * sampleHeight];
        sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight);
        if (sample != source) sample.recycle();

        Map<Integer, Integer> histogram = new HashMap<>();
        for (int pixel : pixels) {
            if (Color.alpha(pixel) < 180) continue;
            int red = Color.red(pixel) >> 3;
            int green = Color.green(pixel) >> 3;
            int blue = Color.blue(pixel) >> 3;
            int key = (red << 10) | (green << 5) | blue;
            Integer count = histogram.get(key);
            histogram.put(key, count == null ? 1 : count + 1);
        }

        List<ColorCandidate> candidates = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
            int key = entry.getKey();
            int color = Color.rgb(
                    (((key >> 10) & 31) << 3) + 4,
                    (((key >> 5) & 31) << 3) + 4,
                    ((key & 31) << 3) + 4
            );
            float saturation = saturation(color);
            double weight = entry.getValue() * (0.38 + saturation * 1.75);
            candidates.add(new ColorCandidate(color, weight, saturation));
        }

        Collections.sort(candidates, (first, second) ->
                Double.compare(second.weight, first.weight));

        List<Integer> selected = new ArrayList<>();
        for (ColorCandidate candidate : candidates) {
            if (candidate.saturation < 0.10f && selected.size() < 3) continue;
            if (isDistinct(candidate.color, selected)) {
                selected.add(candidate.color);
                if (selected.size() == 5) break;
            }
        }
        for (ColorCandidate candidate : candidates) {
            if (selected.size() == 5) break;
            if (isDistinct(candidate.color, selected)) selected.add(candidate.color);
        }

        int background = averageCorners(source);
        int[] fallbacks = {
                0xFF5865F2,
                0xFF9B59FF,
                0xFFEB459E,
                0xFF57F287,
                0xFFFEE75C
        };
        for (int fallback : fallbacks) {
            if (selected.size() == 5) break;
            if (isDistinct(fallback, selected)) selected.add(fallback);
        }

        int[] colors = new int[5];
        for (int index = 0; index < colors.length; index++) {
            colors[index] = selected.get(index);
        }

        int accent = colors[0];
        float strongestSaturation = -1.0f;
        for (int color : colors) {
            float candidate = saturation(color);
            if (candidate > strongestSaturation) {
                strongestSaturation = candidate;
                accent = color;
            }
        }

        boolean dark = luminance(background) < 0.46;
        int text = dark ? Color.WHITE : Color.rgb(32, 34, 37);
        int secondary = blend(background, accent, dark ? 0.11f : 0.07f);
        int tertiary = blend(background, dark ? Color.BLACK : Color.WHITE, 0.14f);
        int floating = blend(background, dark ? Color.BLACK : Color.WHITE, 0.22f);
        int muted = blend(text, background, 0.48f);

        return new ThemePalette(
                colors,
                background,
                secondary,
                tertiary,
                floating,
                accent,
                text,
                muted,
                dark
        );
    }

    private static Bitmap createPreview(ThemePalette palette, String timestamp) {
        Bitmap bitmap = Bitmap.createBitmap(
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT,
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        int[] gradientColors = Arrays.copyOf(palette.colors, palette.colors.length);
        float[] positions = {0.0f, 0.24f, 0.50f, 0.76f, 1.0f};
        paint.setShader(new LinearGradient(
                0,
                0,
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT,
                gradientColors,
                positions,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, paint);
        paint.setShader(null);

        paint.setColor(palette.dark ? 0xAA0E0F12 : 0x99FFFFFF);
        canvas.drawRoundRect(new RectF(52, 52, 1548, 848), 42, 42, paint);

        paint.setColor(withAlpha(palette.tertiary, 235));
        canvas.drawRoundRect(new RectF(88, 88, 348, 812), 30, 30, paint);

        paint.setColor(withAlpha(palette.secondary, 238));
        canvas.drawRoundRect(new RectF(382, 88, 1512, 812), 30, 30, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(58);
        paint.setColor(palette.text);
        canvas.drawText("Discord Custom Theme", 440, 186, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(28);
        paint.setColor(palette.muted);
        canvas.drawText("CSS + PNG export  •  " + timestamp, 442, 232, paint);

        paint.setColor(withAlpha(palette.floating, 230));
        canvas.drawRoundRect(new RectF(432, 278, 1462, 580), 28, 28, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(34);
        paint.setColor(palette.text);
        canvas.drawText("Theme palette", 482, 348, paint);

        int swatchLeft = 482;
        for (int index = 0; index < palette.colors.length; index++) {
            int left = swatchLeft + index * 182;
            paint.setColor(palette.colors[index]);
            canvas.drawRoundRect(new RectF(left, 390, left + 142, 506), 22, 22, paint);
            paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            paint.setTextSize(22);
            paint.setColor(palette.text);
            canvas.drawText(hex(palette.colors[index]), left, 548, paint);
        }

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(30);
        paint.setColor(palette.text);
        canvas.drawText("PREVIEW", 126, 154, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(25);
        int[] lineWidths = {150, 190, 126, 174, 142, 205, 116};
        for (int index = 0; index < lineWidths.length; index++) {
            paint.setColor(index == 2 ? palette.accent : palette.muted);
            canvas.drawRoundRect(
                    new RectF(126, 220 + index * 72, 126 + lineWidths[index], 242 + index * 72),
                    11,
                    11,
                    paint
            );
        }

        paint.setColor(palette.accent);
        canvas.drawRoundRect(new RectF(432, 648, 810, 738), 26, 26, paint);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(30);
        paint.setColor(bestTextColor(palette.accent));
        canvas.drawText("CUSTOM THEME", 492, 705, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(25);
        paint.setColor(palette.muted);
        canvas.drawText(
                "Generated locally without saving chat or account content",
                850,
                704,
                paint
        );

        return bitmap;
    }

    private static String buildCss(ThemePalette palette, String timestamp) {
        StringBuilder css = new StringBuilder();
        css.append("/**\n")
                .append(" * Discord custom theme color export\n")
                .append(" * Generated: ").append(timestamp).append("\n")
                .append(" * This file does not unlock Nitro or contain account/chat data.\n")
                .append(" * Discord's internal CSS variable names can change between releases.\n")
                .append(" */\n\n");

        css.append(":root {\n");
        for (int index = 0; index < palette.colors.length; index++) {
            css.append("  --discord-custom-color-")
                    .append(index + 1)
                    .append(": ")
                    .append(hex(palette.colors[index]))
                    .append(";\n");
        }
        css.append("  --discord-custom-gradient: linear-gradient(135deg, ")
                .append(hex(palette.colors[0])).append(" 0%, ")
                .append(hex(palette.colors[1])).append(" 24%, ")
                .append(hex(palette.colors[2])).append(" 50%, ")
                .append(hex(palette.colors[3])).append(" 76%, ")
                .append(hex(palette.colors[4])).append(" 100%);\n\n")
                .append("  --background-primary: ").append(hex(palette.background)).append(";\n")
                .append("  --background-secondary: ").append(hex(palette.secondary)).append(";\n")
                .append("  --background-secondary-alt: ").append(hex(palette.tertiary)).append(";\n")
                .append("  --background-tertiary: ").append(hex(palette.tertiary)).append(";\n")
                .append("  --background-floating: ").append(hex(palette.floating)).append(";\n")
                .append("  --background-base-low: ").append(hex(palette.background)).append(";\n")
                .append("  --background-base-lower: ").append(hex(palette.secondary)).append(";\n")
                .append("  --background-base-lowest: ").append(hex(palette.tertiary)).append(";\n")
                .append("  --text-normal: ").append(hex(palette.text)).append(";\n")
                .append("  --text-primary: ").append(hex(palette.text)).append(";\n")
                .append("  --text-muted: ").append(hex(palette.muted)).append(";\n")
                .append("  --interactive-normal: ").append(hex(palette.muted)).append(";\n")
                .append("  --interactive-hover: ").append(hex(palette.text)).append(";\n")
                .append("  --interactive-active: ").append(hex(palette.text)).append(";\n")
                .append("  --brand-experiment: ").append(hex(palette.accent)).append(";\n")
                .append("  --brand-500: ").append(hex(palette.accent)).append(";\n")
                .append("  --accent-color: ").append(hex(palette.accent)).append(";\n")
                .append("  --background-modifier-hover: ")
                .append(rgba(palette.text, palette.dark ? 0.08f : 0.06f)).append(";\n")
                .append("  --background-modifier-active: ")
                .append(rgba(palette.text, palette.dark ? 0.12f : 0.10f)).append(";\n")
                .append("}\n\n")
                .append(".discord-custom-theme-preview {\n")
                .append("  color: var(--text-normal);\n")
                .append("  background: var(--discord-custom-gradient);\n")
                .append("  border: 1px solid var(--accent-color);\n")
                .append("  border-radius: 16px;\n")
                .append("}\n");
        return css.toString();
    }

    private static byte[] encodePng(Bitmap bitmap) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            throw new IOException("PNG encoder failed");
        }
        return output.toByteArray();
    }

    private static void saveFile(
            Context context,
            String fileName,
            String mimeType,
            byte[] data
    ) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + EXPORT_FOLDER
            );
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Files.getContentUri("external"), values);
            if (uri == null) throw new IOException("Could not create " + fileName);

            try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("Could not open " + fileName);
                output.write(data);
            } catch (Throwable error) {
                resolver.delete(uri, null, null);
                if (error instanceof IOException) throw (IOException) error;
                throw new IOException(error);
            }

            ContentValues complete = new ContentValues();
            complete.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, complete, null, null);
            return;
        }

        File downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
        );
        File folder = new File(downloads, EXPORT_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create Downloads/" + EXPORT_FOLDER);
        }
        File file = new File(folder, fileName);
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(data);
        }
        MediaScannerConnection.scanFile(
                context,
                new String[]{file.getAbsolutePath()},
                new String[]{mimeType},
                null
        );
    }

    private static int averageCorners(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int insetX = Math.max(1, width / 30);
        int insetY = Math.max(1, height / 30);
        int[] points = {
                source.getPixel(insetX, insetY),
                source.getPixel(width - insetX - 1, insetY),
                source.getPixel(insetX, height - insetY - 1),
                source.getPixel(width - insetX - 1, height - insetY - 1)
        };
        int red = 0;
        int green = 0;
        int blue = 0;
        for (int color : points) {
            red += Color.red(color);
            green += Color.green(color);
            blue += Color.blue(color);
        }
        return Color.rgb(red / points.length, green / points.length, blue / points.length);
    }

    private static boolean isDistinct(int candidate, List<Integer> selected) {
        for (int color : selected) {
            int red = Color.red(candidate) - Color.red(color);
            int green = Color.green(candidate) - Color.green(color);
            int blue = Color.blue(candidate) - Color.blue(color);
            if (red * red + green * green + blue * blue < 2300) return false;
        }
        return true;
    }

    private static float saturation(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return hsv[1];
    }

    private static int blend(int first, int second, float amount) {
        float inverse = 1.0f - amount;
        return Color.rgb(
                Math.round(Color.red(first) * inverse + Color.red(second) * amount),
                Math.round(Color.green(first) * inverse + Color.green(second) * amount),
                Math.round(Color.blue(first) * inverse + Color.blue(second) * amount)
        );
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int bestTextColor(int background) {
        return contrast(Color.WHITE, background) >= contrast(Color.BLACK, background)
                ? Color.WHITE
                : Color.BLACK;
    }

    private static double contrast(int first, int second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(int color) {
        double red = linear(Color.red(color) / 255.0);
        double green = linear(Color.green(color) / 255.0);
        double blue = linear(Color.blue(color) / 255.0);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linear(double value) {
        return value <= 0.04045
                ? value / 12.92
                : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static String hex(int color) {
        return String.format(
                Locale.US,
                "#%02X%02X%02X",
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    private static String rgba(int color, float alpha) {
        return String.format(
                Locale.US,
                "rgba(%d, %d, %d, %.3f)",
                Color.red(color),
                Color.green(color),
                Color.blue(color),
                alpha
        );
    }

    private static final class ColorCandidate {
        final int color;
        final double weight;
        final float saturation;

        ColorCandidate(int color, double weight, float saturation) {
            this.color = color;
            this.weight = weight;
            this.saturation = saturation;
        }
    }

    private static final class ThemePalette {
        final int[] colors;
        final int background;
        final int secondary;
        final int tertiary;
        final int floating;
        final int accent;
        final int text;
        final int muted;
        final boolean dark;

        ThemePalette(
                int[] colors,
                int background,
                int secondary,
                int tertiary,
                int floating,
                int accent,
                int text,
                int muted,
                boolean dark
        ) {
            this.colors = colors;
            this.background = background;
            this.secondary = secondary;
            this.tertiary = tertiary;
            this.floating = floating;
            this.accent = accent;
            this.text = text;
            this.muted = muted;
            this.dark = dark;
        }
    }
}
