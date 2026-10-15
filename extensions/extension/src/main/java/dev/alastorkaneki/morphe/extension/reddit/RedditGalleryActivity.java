package dev.alastorkaneki.morphe.extension.reddit;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Self-contained Reddit patch UI; needs no access to Reddit's internal classes. */
public final class RedditGalleryActivity extends Activity {
    public static final String EXTRA_AUTOSTART_MODE = "morphe.reddit.gallery.autostart_mode";
    private static final int DARK = Color.rgb(14, 13, 19);
    private static final int SURFACE = Color.rgb(29, 25, 38);
    private static final int ACCENT = Color.rgb(169, 0, 255);
    private static final int MUTED = Color.rgb(196, 190, 204);
    private static final ExecutorService DOWNLOADS = Executors.newSingleThreadExecutor();

    private EditText input;
    private TextView status;
    private Button saveImages;
    private Button saveZip;
    private ProgressBar progress;
    private volatile boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        buildUi();
        handleIntent(getIntent(), state == null);
    }

    @Override protected void onNewIntent(Intent next) {
        super.onNewIntent(next);
        setIntent(next);
        handleIntent(next, true);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(DARK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int gap = dp(18);
        root.setPadding(gap, dp(24), gap, gap);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = label("Reddit Gallery Originals", 23, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView hint = label(
                "Use Reddit's native Share → Reddit Gallery Originals for one-tap post selection. " +
                "The ↓ Originals button also attempts to detect the active post. " +
                "Original CDN files only; no previews, sign-in, or cookies.",
                14, MUTED);
        hint.setPadding(0, dp(12), 0, dp(15));
        root.addView(hint);

        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setHint("Detected post URL (or paste manually)");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setBackground(roundRect(SURFACE, dp(12)));
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));

        Button paste = button("Paste link manually (optional)", SURFACE);
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(-1, dp(49));
        pasteParams.topMargin = dp(10);
        root.addView(paste, pasteParams);
        paste.setOnClickListener(v -> {
            String clip = readClipboard();
            if (clip == null) {
                toast("Clipboard does not contain a Reddit gallery/post link.");
            } else {
                input.setText(clip);
                input.setSelection(input.length());
            }
        });

        saveImages = button("Save original images to Pictures", ACCENT);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, dp(54));
        imageParams.topMargin = dp(20);
        root.addView(saveImages, imageParams);
        saveImages.setOnClickListener(v -> startDownload(false));

        saveZip = button("Download originals as ZIP", SURFACE);
        LinearLayout.LayoutParams zipParams = new LinearLayout.LayoutParams(-1, dp(54));
        zipParams.topMargin = dp(10);
        root.addView(saveZip, zipParams);
        saveZip.setOnClickListener(v -> startDownload(true));

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(42));
        progressParams.topMargin = dp(12);
        root.addView(progress, progressParams);

        status = label("Open ↓ Originals from a Reddit post or use Reddit's Share menu — no paste needed.", 13, MUTED);
        status.setPadding(0, dp(8), 0, dp(12));
        root.addView(status);
        root.addView(label(
                "Images: Pictures/RedditGallery/<post ID> · ZIP: Downloads. " +
                "The archive does not guarantee deleted or private media still exists on the CDN.",
                12, MUTED));
    }

    private void handleIntent(Intent intent, boolean allowAutoDownload) {
        if (intent == null || input == null) return;
        String text = intent.getDataString();
        if (!isSupportedPostReference(text)) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared != null) text = shared.toString();
        }
        boolean suppliedByReddit = isSupportedPostReference(text);
        if (!suppliedByReddit) text = readClipboard();
        if (isSupportedPostReference(text)) {
            input.setText(text);
            input.setSelection(input.length());
            String id = RedditGalleryLink.postId(text);
            status.setText(suppliedByReddit ?
                    (id == null ? "Reddit share token detected — ready to resolve automatically." :
                        "Current post detected: " + id) :
                    "Post link from clipboard — choose Images or ZIP.");
        }

        // The floating button only supplies this flag after the user explicitly
        // selected a download type. A clipboard candidate never auto-starts.
        String mode = intent.getStringExtra(EXTRA_AUTOSTART_MODE);
        if (allowAutoDownload && suppliedByReddit && ("images".equals(mode) || "zip".equals(mode))) {
            intent.removeExtra(EXTRA_AUTOSTART_MODE);
            startDownload("zip".equals(mode));
            return;
        }

        // Reddit's own Share -> Reddit Gallery Originals is already a native
        // context action. Offer a save-mode chooser immediately, no paste.
        if (allowAutoDownload && suppliedByReddit &&
                Intent.ACTION_SEND.equals(intent.getAction())) {
            intent.setAction("morphe.reddit.gallery.share.handled");
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Save this Reddit post's originals")
                    .setMessage("Reddit shared the current post. ZIP or image download will resolve its post ID automatically.")
                    .setItems(new String[]{"Images · Pictures", "ZIP · Downloads"},
                            (dialog, selected) -> startDownload(selected == 1))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    private static boolean isSupportedPostReference(String text) {
        return RedditGalleryLink.postId(text) != null || RedditShareLink.find(text) != null;
    }

    private String readClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return null;
            ClipData data = clipboard.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) return null;
            CharSequence text = data.getItemAt(0).coerceToText(this);
            String candidate = text == null ? null : text.toString();
            return !isSupportedPostReference(candidate) ? null : candidate;
        } catch (RuntimeException ignored) { return null; }
    }

    private void startDownload(boolean zip) {
        if (busy) return;
        String submitted = input.getText().toString();
        String directId = RedditGalleryLink.postId(submitted);
        if (directId == null && RedditShareLink.find(submitted) == null) {
            status.setText("No post detected. Use Reddit's Share menu to send the current gallery.");
            return;
        }

        busy = true;
        saveImages.setEnabled(false);
        saveZip.setEnabled(false);
        progress.setVisibility(android.view.View.VISIBLE);
        if (directId != null) {
            startResolvedDownload(directId, zip);
            return;
        }

        // A short /s/ URL contains a random opaque token, not a post ID.
        // Resolving it is separate from Arctic Shift metadata and CDN access.
        status.setText("Share link: expanding Reddit's /s/ token…");
        DOWNLOADS.execute(() -> {
            try {
                String id = RedditShareLink.resolve(submitted);
                runOnUiThread(() -> startResolvedDownload(id, zip));
            } catch (Exception first) {
                String http = first.getClass().getSimpleName() + ": " + first.getMessage();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    status.setText("Share redirect failed (" + http +
                            "). Trying the Android browser network stack…");
                    RedditBrowserShareResolver.resolve(this, submitted, (postId, webError) -> {
                        if (postId != null) {
                            startResolvedDownload(postId, zip);
                        } else {
                            finishDownload("SHARE LINK 403/REDIRECT failure: HTTP client: " +
                                    http + "; Android browser: " + webError +
                                    ". The short /s/ token could not be expanded into a post ID.");
                        }
                    });
                });
            }
        });
    }

    private void startResolvedDownload(String id, boolean zip) {
        showStatus("POST ID " + id + " resolved. Loading Arctic Shift gallery metadata…");
        DOWNLOADS.execute(() -> {
            String result;
            try {
                RedditGalleryResolver.Gallery gallery;
                try {
                    gallery = RedditGalleryResolver.resolve(id);
                } catch (Exception e) {
                    throw new IOException("METADATA stage failed: " + e.getMessage(), e);
                }
                showStatus("METADATA OK: " + gallery.photos.size() + " originals from " +
                        gallery.source + ". Starting image transfer…");
                try {
                    result = zip ? downloadZip(gallery) : downloadPhotos(gallery);
                } catch (Exception e) {
                    throw new IOException("IMAGE DOWNLOAD or STORAGE stage failed: " +
                            e.getMessage(), e);
                }
            } catch (Exception error) {
                result = error.getClass().getSimpleName() + ": " + error.getMessage();
            }
            final String finalResult = result;
            runOnUiThread(() -> finishDownload(finalResult));
        });
    }

    private void finishDownload(String result) {
        busy = false;
        saveImages.setEnabled(true);
        saveZip.setEnabled(true);
        progress.setVisibility(android.view.View.GONE);
        status.setText(result);
        toast(result.length() > 100 ? result.substring(0, 100) : result);
    }

    private String downloadZip(RedditGalleryResolver.Gallery gallery) throws IOException {
        File staged = File.createTempFile("reddit-gallery-", ".zip", getCacheDir());
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(staged))) {
                for (int index = 0; index < gallery.photos.size(); index++) {
                    RedditGalleryResolver.Photo photo = gallery.photos.get(index);
                    showStatus("Downloading original " + (index + 1) + "/" + gallery.photos.size() +
                            " to ZIP…");
                    zip.putNextEntry(new ZipEntry(photo.name));
                    try {
                        RedditGalleryResolver.streamOriginal(photo, (source, maxBytes) ->
                                copyBounded(source, zip, maxBytes));
                    } catch (IOException e) {
                        throw new IOException("CDN original " + (index + 1) + "/" +
                                gallery.photos.size() + " (" + photo.name + ") failed: " +
                                e.getMessage(), e);
                    }
                    zip.closeEntry();
                }
                zip.putNextEntry(new ZipEntry("manifest.txt"));
                String manifest = "Reddit: https://www.reddit.com/gallery/" + gallery.id +
                        "\nSource: " + gallery.source + "\nOriginal images: " +
                        gallery.photos.size() + "\nNo resized previews included.\n";
                zip.write(manifest.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            String filename = "reddit_gallery_" + gallery.id + ".zip";
            Uri destination = createDestination(filename, "application/zip", false, gallery.id);
            try {
                try (InputStream source = new java.io.FileInputStream(staged);
                     OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                    if (out == null) throw new IOException("Android could not open the ZIP destination");
                    copyBounded(source, out, 1024 * 1024 * 1024);
                }
                complete(destination);
            } catch (Exception error) {
                getContentResolver().delete(destination, null, null);
                throw error instanceof IOException ? (IOException) error :
                        new IOException("Could not save ZIP", error);
            }
            return "Saved " + gallery.photos.size() + " originals in Downloads/" + filename;
        } finally {
            if (staged.exists() && !staged.delete()) staged.deleteOnExit();
        }
    }

    private String downloadPhotos(RedditGalleryResolver.Gallery gallery) throws IOException {
        int done = 0;
        IOException lastFailure = null;
        for (int index = 0; index < gallery.photos.size(); index++) {
            RedditGalleryResolver.Photo photo = gallery.photos.get(index);
            showStatus("Saving original " + (index + 1) + "/" + gallery.photos.size() + "…");
            Uri destination = null;
            try {
                destination = createDestination(photo.name, photo.mime, true, gallery.id);
                Uri saved = destination;
                RedditGalleryResolver.streamOriginal(photo, (source, maxBytes) -> {
                    try (OutputStream out = getContentResolver().openOutputStream(saved, "w")) {
                        if (out == null) throw new IOException("Android refused image output stream");
                        copyBounded(source, out, maxBytes);
                    }
                });
                complete(destination);
                done++;
            } catch (IOException | RuntimeException e) {
                lastFailure = new IOException(photo.name + ": " + e.getMessage(), e);
                if (destination != null) getContentResolver().delete(destination, null, null);
            }
        }
        if (done == 0) throw new IOException("No originals saved. Last failure: " +
                (lastFailure == null ? "unknown" : lastFailure.getMessage()));
        String summary = "Saved " + done + "/" + gallery.photos.size() +
                " originals in Pictures/RedditGallery/" + gallery.id;
        if (lastFailure != null) summary += " (some failed: " + lastFailure.getMessage() + ")";
        return summary;
    }

    private Uri createDestination(String name, String mime, boolean image, String id)
            throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            throw new IOException("This patch requires Android 10+ for public MediaStore downloads");
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, image ?
                Environment.DIRECTORY_PICTURES + "/RedditGallery/" + id :
                Environment.DIRECTORY_DOWNLOADS);
        Uri collection = image ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI :
                MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri uri = getContentResolver().insert(collection, values);
        if (uri == null) throw new IOException("Android could not reserve a Downloads entry");
        return uri;
    }

    private void complete(Uri uri) {
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        getContentResolver().update(uri, done, null, null);
    }

    private static void copyBounded(InputStream in, OutputStream out, int maxBytes)
            throws IOException {
        byte[] buffer = new byte[16384];
        int count = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            count += read;
            if (count > maxBytes) throw new IOException("Original exceeds safe size limit");
            out.write(buffer, 0, read);
        }
    }

    private void showStatus(String message) {
        runOnUiThread(() -> {
            if (status != null) status.setText(message);
        });
    }

    private TextView label(String text, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return view;
    }

    private Button button(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackground(roundRect(color, dp(13)));
        return button;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
