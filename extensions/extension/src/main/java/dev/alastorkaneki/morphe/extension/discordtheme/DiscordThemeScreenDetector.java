package dev.alastorkaneki.morphe.extension.discordtheme;

import android.app.Activity;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Locale;

/** Detects Discord's Appearance/custom-theme screens without version-specific hooks. */
public final class DiscordThemeScreenDetector {
    private static final int MAX_VIEWS = 900;

    private DiscordThemeScreenDetector() {
    }

    public static boolean isThemeExportScreen(Activity activity) {
        View root = activity.getWindow().getDecorView();
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);

        int inspected = 0;
        int score = 0;
        boolean customSignal = false;

        while (!queue.isEmpty() && inspected++ < MAX_VIEWS) {
            View view = queue.removeFirst();

            String text = collectText(view);
            if (!text.isEmpty()) {
                int result = scoreText(text);
                score += result & 0xFF;
                customSignal |= (result & 0x100) != 0;
            }

            String resourceName = resourceEntryName(view);
            if (!resourceName.isEmpty()) {
                int result = scoreText(resourceName.replace('_', ' '));
                score += result & 0xFF;
                customSignal |= (result & 0x100) != 0;
            }

            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int index = 0; index < group.getChildCount(); index++) {
                    queue.addLast(group.getChildAt(index));
                }
            }
        }

        return customSignal && score >= 5;
    }

    private static String collectText(View view) {
        StringBuilder result = new StringBuilder();
        append(result, view.getContentDescription());
        append(result, view.getTag());
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            append(result, textView.getText());
            append(result, textView.getHint());
        }
        return result.toString().toLowerCase(Locale.US);
    }

    private static void append(StringBuilder builder, Object value) {
        if (value == null) return;
        String text = value.toString().trim();
        if (text.isEmpty()) return;
        if (builder.length() > 0) builder.append(' ');
        builder.append(text);
    }

    private static String resourceEntryName(View view) {
        int id = view.getId();
        if (id == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(id).toLowerCase(Locale.US);
        } catch (Resources.NotFoundException ignored) {
            return "";
        }
    }

    /** Low byte is score; bit 8 marks a custom-theme/Nitro-theme signal. */
    private static int scoreText(String text) {
        String normalized = text.toLowerCase(Locale.US);
        int score = 0;
        boolean custom = false;

        if (normalized.contains("custom theme") ||
                normalized.contains("customize your theme") ||
                normalized.contains("custom_theme")) {
            score += 6;
            custom = true;
        }
        if (normalized.contains("color themes") || normalized.contains("color theme")) {
            score += 4;
            custom = true;
        }
        if (normalized.contains("theme intensity") ||
                normalized.contains("color intensity") ||
                normalized.contains("surprise me")) {
            score += 4;
            custom = true;
        }
        if (normalized.contains("preview theme") ||
                normalized.contains("apply theme") ||
                normalized.contains("sync across clients")) {
            score += 3;
            custom = true;
        }
        if (normalized.contains("appearance")) score += 2;
        if (normalized.contains("theme")) score += 1;
        if (normalized.contains("nitro")) score += 1;

        return Math.min(score, 0xFF) | (custom ? 0x100 : 0);
    }
}
