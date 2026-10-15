package dev.alastorkaneki.morphe.extension.reddit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Self-contained launcher button displayed over foreground Reddit activities.
 * This is not an accessibility or system overlay; it only attaches inside Reddit.
 */
public final class RedditGalleryOverlayController implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "morphe.reddit.gallery.originals.button";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private RedditGalleryOverlayController() { }

    static void install(Application app) {
        if (INSTALLED.compareAndSet(false, true))
            app.registerActivityLifecycleCallbacks(new RedditGalleryOverlayController());
    }

    @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof RedditGalleryActivity) return;
        try { attach(activity); } catch (RuntimeException ignored) {
            // Never crash Reddit because its Activity hierarchy changed.
        }
    }

    private void attach(Activity activity) {
        View root = activity.getWindow().getDecorView();
        View existing = root.findViewWithTag(TAG);
        if (existing != null) {
            existing.bringToFront();
            return;
        }

        TextView button = new TextView(activity);
        button.setTag(TAG);
        button.setText("↓  Originals");
        button.setContentDescription("Download Reddit gallery original images");
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 14), dp(activity, 10),
                dp(activity, 14), dp(activity, 10));
        button.setClickable(true);
        button.setFocusable(true);
        button.setElevation(dp(activity, 10));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(65, 14, 85));
        background.setCornerRadius(dp(activity, 32));
        background.setStroke(dp(activity, 1), Color.rgb(169, 0, 255));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(65, 255, 255, 255)), background, null));

        button.setOnClickListener(v -> {
            // Capture the active Reddit post before starting our Activity.
            // Activity launch can destroy the previous view/navigation state.
            RedditCurrentPost.Match current = RedditCurrentPost.detect(activity);
            if (current != null) {
                String link = "https://www.reddit.com/gallery/" + current.postId;
                new android.app.AlertDialog.Builder(activity)
                        .setTitle("Originals · " + current.postId)
                        .setMessage("Detected from " + current.source + ". Save this post's original images:")
                        .setItems(new String[]{"Images · Pictures", "ZIP · Downloads"}, (dialog, selected) -> {
                            Intent intent = new Intent(activity, RedditGalleryActivity.class);
                            intent.setData(android.net.Uri.parse(link));
                            intent.putExtra(RedditGalleryActivity.EXTRA_AUTOSTART_MODE,
                                    selected == 0 ? "images" : "zip");
                            activity.startActivity(intent);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return;
            }

            // Some Reddit versions never expose the current post ID through
            // public Activity or fragment state. Don't silently download a
            // stale/unrelated feed post: offer native Share as the fallback.
            new android.app.AlertDialog.Builder(activity)
                    .setTitle("Can't identify the current post")
                    .setMessage("Open the post itself and tap ↓ Originals again. " +
                            "Or use Reddit's Share → Reddit Gallery Originals to pass the link automatically, without pasting.")
                    .setPositiveButton("Open downloader", (dialog, selected) ->
                            activity.startActivity(new Intent(activity, RedditGalleryActivity.class)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM);
        params.setMargins(dp(activity, 12), dp(activity, 12),
                dp(activity, 12), dp(activity, 108));
        activity.addContentView(button, params);
        button.bringToFront();
    }

    private static void detach(Activity activity) {
        try {
            View view = activity.getWindow().getDecorView().findViewWithTag(TAG);
            if (view != null && view.getParent() instanceof ViewGroup)
                ((ViewGroup) view.getParent()).removeView(view);
        } catch (RuntimeException ignored) { }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @Override public void onActivityPaused(Activity activity) { detach(activity); }
    @Override public void onActivityDestroyed(Activity activity) { detach(activity); }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
}
