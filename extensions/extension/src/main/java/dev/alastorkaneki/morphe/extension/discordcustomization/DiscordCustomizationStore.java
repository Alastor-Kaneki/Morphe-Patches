package dev.alastorkaneki.morphe.extension.discordcustomization;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/** Stores only local customization choices for the patched Discord installation. */
public final class DiscordCustomizationStore {
    private static final String PREFS =
            "dev.alastorkaneki.morphe.discord.customization";
    private static final String KEY_IMAGE_URI = "image_uri";
    private static final String KEY_FONT_URI = "font_uri";
    private static final String KEY_IMAGE_OPACITY = "image_opacity";
    private static final int DEFAULT_OPACITY = 22;

    private DiscordCustomizationStore() {
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Uri getImageUri(Context context) {
        String value = preferences(context).getString(KEY_IMAGE_URI, null);
        return value == null || value.isEmpty() ? null : Uri.parse(value);
    }

    public static void setImageUri(Context context, Uri uri) {
        preferences(context).edit()
                .putString(KEY_IMAGE_URI, uri == null ? null : uri.toString())
                .apply();
    }

    public static Uri getFontUri(Context context) {
        String value = preferences(context).getString(KEY_FONT_URI, null);
        return value == null || value.isEmpty() ? null : Uri.parse(value);
    }

    public static void setFontUri(Context context, Uri uri) {
        preferences(context).edit()
                .putString(KEY_FONT_URI, uri == null ? null : uri.toString())
                .apply();
    }

    public static int getImageOpacity(Context context) {
        return Math.max(5, Math.min(60,
                preferences(context).getInt(KEY_IMAGE_OPACITY, DEFAULT_OPACITY)));
    }

    public static void setImageOpacity(Context context, int opacity) {
        preferences(context).edit()
                .putInt(KEY_IMAGE_OPACITY, Math.max(5, Math.min(60, opacity)))
                .apply();
    }
}
