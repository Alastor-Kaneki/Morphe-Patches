package dev.alastorkaneki.morphe.extension.discordcustomization;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Native configuration screen for image themes and custom fonts. */
public final class DiscordCustomizationActivity extends Activity {
    private static final int REQUEST_IMAGE = 4101;
    private static final int REQUEST_FONT = 4102;

    private TextView imageStatus;
    private TextView fontStatus;
    private TextView opacityStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Discord Customizer");

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = dp(20);
        content.setPadding(padding, padding, padding, padding);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Discord Customizer");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(10));
        content.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText(
                "Choose an image overlay and a local TTF/OTF font. " +
                        "Selections stay on this device and are applied only to the patched Discord app."
        );
        description.setTextSize(15);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 0, 0, dp(18));
        content.addView(description, matchWrap());

        imageStatus = statusView();
        content.addView(imageStatus, matchWrap());
        content.addView(button("Choose theme image", view -> chooseImage()), matchWrap());
        content.addView(button("Clear theme image", view -> {
            DiscordCustomizationStore.setImageUri(this, null);
            DiscordCustomizationController.refreshAll();
            refreshStatus();
        }), matchWrap());

        opacityStatus = statusView();
        opacityStatus.setPadding(0, dp(18), 0, dp(6));
        content.addView(opacityStatus, matchWrap());

        SeekBar opacity = new SeekBar(this);
        opacity.setMax(55);
        opacity.setProgress(DiscordCustomizationStore.getImageOpacity(this) - 5);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + 5;
                DiscordCustomizationStore.setImageOpacity(
                        DiscordCustomizationActivity.this,
                        value
                );
                opacityStatus.setText("Image opacity: " + value + "%");
                DiscordCustomizationController.refreshAll();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        content.addView(opacity, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        fontStatus = statusView();
        fontStatus.setPadding(0, dp(18), 0, dp(6));
        content.addView(fontStatus, matchWrap());
        content.addView(button("Choose TTF / OTF font", view -> chooseFont()), matchWrap());
        content.addView(button("Restore Discord fonts", view -> {
            DiscordCustomizationStore.setFontUri(this, null);
            DiscordCustomizationController.refreshAll();
            refreshStatus();
        }), matchWrap());

        Button done = button("Done", view -> finish());
        LinearLayout.LayoutParams doneParams = matchWrap();
        doneParams.topMargin = dp(24);
        content.addView(done, doneParams);

        setContentView(scrollView);
        refreshStatus();
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    private void chooseFont() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "font/ttf",
                "font/otf",
                "application/x-font-ttf",
                "application/x-font-opentype",
                "application/font-sfnt",
                "application/octet-stream"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_FONT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        int flags = data.getFlags() &
                (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (RuntimeException ignored) {
            // Some document providers grant durable access without supporting this call.
        }

        if (requestCode == REQUEST_IMAGE) {
            DiscordCustomizationStore.setImageUri(this, uri);
        } else if (requestCode == REQUEST_FONT) {
            DiscordCustomizationStore.setFontUri(this, uri);
        } else {
            return;
        }

        DiscordCustomizationController.refreshAll();
        refreshStatus();
        Toast.makeText(this, "Customization updated", Toast.LENGTH_SHORT).show();
    }

    private void refreshStatus() {
        Uri image = DiscordCustomizationStore.getImageUri(this);
        Uri font = DiscordCustomizationStore.getFontUri(this);
        imageStatus.setText("Theme image: " + displayName(image, "None"));
        fontStatus.setText("Custom font: " + displayName(font, "Discord default"));
        opacityStatus.setText(
                "Image opacity: " + DiscordCustomizationStore.getImageOpacity(this) + "%"
        );
    }

    private String displayName(Uri uri, String fallback) {
        if (uri == null) return fallback;
        android.database.Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    uri,
                    new String[] { OpenableColumns.DISPLAY_NAME },
                    null,
                    null,
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.isEmpty()) return value;
                }
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        String last = uri.getLastPathSegment();
        return last == null || last.isEmpty() ? fallback : last;
    }

    private TextView statusView() {
        TextView view = new TextView(this);
        view.setTextSize(16);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(8), 0, dp(6));
        return view;
    }

    private Button button(String text, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        button.setMinWidth(dp(240));
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
