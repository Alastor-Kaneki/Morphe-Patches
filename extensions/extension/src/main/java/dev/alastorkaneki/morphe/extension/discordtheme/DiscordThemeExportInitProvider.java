package dev.alastorkaneki.morphe.extension.discordtheme;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/** Installs the Discord theme-export overlay before Discord's first activity starts. */
public final class DiscordThemeExportInitProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        if (getContext() != null && getContext().getApplicationContext() instanceof Application) {
            DiscordThemeExportController.install(
                    (Application) getContext().getApplicationContext()
            );
        }
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) { return 0; }
}
