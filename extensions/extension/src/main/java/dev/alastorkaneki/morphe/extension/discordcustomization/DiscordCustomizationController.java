package dev.alastorkaneki.morphe.extension.discordcustomization;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Applies local image and font choices without touching Discord account data. */
public final class DiscordCustomizationController
        implements Application.ActivityLifecycleCallbacks {
    private static final String IMAGE_TAG =
            "dev.alastorkaneki.morphe.discord.customization.IMAGE";
    private static final String BUTTON_TAG =
            "dev.alastorkaneki.morphe.discord.customization.BUTTON";
    private static final long REFRESH_INTERVAL_MS = 850L;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final Map<Activity, Runnable> REFRESHERS = new WeakHashMap<>();
    private static final Set<Activity> ACTIVE =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<TextView, Typeface> ORIGINAL_FONTS = new WeakHashMap<>();

    private static String cachedFontUri;
    private static Typeface cachedTypeface;

    private DiscordCustomizationController() {
    }

    public static void install(Application application) {
        if (INSTALLED.compareAndSet(false, true)) {
            application.registerActivityLifecycleCallbacks(
                    new DiscordCustomizationController()
            );
        }
    }

    public static void refreshAll() {
        Activity[] activities;
        synchronized (ACTIVE) {
            activities = ACTIVE.toArray(new Activity[0]);
        }
        for (Activity activity : activities) {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) continue;
            activity.runOnUiThread(() -> apply(activity));
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        synchronized (ACTIVE) {
            ACTIVE.add(activity);
        }
        attachCustomizerButton(activity);
        apply(activity);
        startRefresher(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        stopRefresher(activity);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        stopRefresher(activity);
        synchronized (ACTIVE) {
            ACTIVE.remove(activity);
        }
    }

    private static void startRefresher(Activity activity) {
        stopRefresher(activity);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                apply(activity);
                View decor = activity.getWindow().getDecorView();
                decor.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        };
        synchronized (REFRESHERS) {
            REFRESHERS.put(activity, runnable);
        }
        activity.getWindow().getDecorView().post(runnable);
    }

    private static void stopRefresher(Activity activity) {
        Runnable runnable;
        synchronized (REFRESHERS) {
            runnable = REFRESHERS.remove(activity);
        }
        if (runnable != null) {
            activity.getWindow().getDecorView().removeCallbacks(runnable);
        }
    }

    private static void apply(Activity activity) {
        if (activity instanceof DiscordCustomizationActivity) return;
        applyImageOverlay(activity);
        applyFont(activity);
        View button = activity.getWindow().getDecorView().findViewWithTag(BUTTON_TAG);
        if (button != null) {
            button.setVisibility(isSettingsOrAppearanceScreen(activity)
                    ? View.VISIBLE
                    : View.GONE);
            button.bringToFront();
        }
    }

    private static void applyImageOverlay(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        View existing = decor.findViewWithTag(IMAGE_TAG);
        Uri imageUri = DiscordCustomizationStore.getImageUri(activity);

        if (imageUri == null) {
            if (existing != null && existing.getParent() instanceof ViewGroup) {
                ((ViewGroup) existing.getParent()).removeView(existing);
            }
            return;
        }

        ImageView overlay;
        if (existing instanceof ImageView) {
            overlay = (ImageView) existing;
        } else {
            overlay = new ImageView(activity);
            overlay.setTag(IMAGE_TAG);
            overlay.setScaleType(ImageView.ScaleType.CENTER_CROP);
            overlay.setClickable(false);
            overlay.setFocusable(false);
            overlay.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            );
            activity.addContentView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.FILL
            ));
        }

        String appliedUri = String.valueOf(overlay.getContentDescription());
        if (!imageUri.toString().equals(appliedUri)) {
            try {
                overlay.setImageURI(null);
                overlay.setImageURI(imageUri);
                overlay.setContentDescription(imageUri.toString());
            } catch (RuntimeException ignored) {
                overlay.setImageDrawable(null);
            }
        }
        overlay.setAlpha(DiscordCustomizationStore.getImageOpacity(activity) / 100.0f);
        overlay.setVisibility(View.VISIBLE);
    }

    private static void applyFont(Activity activity) {
        Uri fontUri = DiscordCustomizationStore.getFontUri(activity);
        Typeface typeface = fontUri == null ? null : loadTypeface(activity, fontUri);

        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(activity.getWindow().getDecorView());
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 5000) {
            View view = queue.removeFirst();
            if (view instanceof TextView && view.getTag() != BUTTON_TAG) {
                TextView text = (TextView) view;
                synchronized (ORIGINAL_FONTS) {
                    if (!ORIGINAL_FONTS.containsKey(text)) {
                        ORIGINAL_FONTS.put(text, text.getTypeface());
                    }
                    if (typeface != null) {
                        int style = text.getTypeface() == null
                                ? Typeface.NORMAL
                                : text.getTypeface().getStyle();
                        text.setTypeface(typeface, style);
                    } else {
                        Typeface original = ORIGINAL_FONTS.get(text);
                        if (original != null) text.setTypeface(original);
                    }
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int index = 0; index < group.getChildCount(); index++) {
                    queue.addLast(group.getChildAt(index));
                }
            }
        }
    }

    private static Typeface loadTypeface(Activity activity, Uri uri) {
        synchronized (DiscordCustomizationController.class) {
            String value = uri.toString();
            if (value.equals(cachedFontUri) && cachedTypeface != null) {
                return cachedTypeface;
            }
            ParcelFileDescriptor descriptor = null;
            try {
                descriptor = activity.getContentResolver().openFileDescriptor(uri, "r");
                if (descriptor == null) return null;
                Typeface typeface = new Typeface.Builder(descriptor.getFileDescriptor()).build();
                cachedFontUri = value;
                cachedTypeface = typeface;
                return typeface;
            } catch (IOException | RuntimeException ignored) {
                return null;
            } finally {
                if (descriptor != null) {
                    try {
                        descriptor.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private static void attachCustomizerButton(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        if (decor.findViewWithTag(BUTTON_TAG) != null) return;

        TextView button = new TextView(activity);
        button.setTag(BUTTON_TAG);
        button.setText("Customize  ✦");
        button.setContentDescription("Open Discord image and font customizer");
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 16), dp(activity, 10),
                dp(activity, 16), dp(activity, 10));
        button.setClickable(true);
        button.setFocusable(true);
        button.setElevation(dp(activity, 14));
        applyButtonTheme(activity, button);
        button.setOnClickListener(view -> {
            Intent intent = new Intent(activity, DiscordCustomizationActivity.class);
            activity.startActivity(intent);
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.BOTTOM
        );
        params.setMargins(dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 94));
        activity.addContentView(button, params);
        button.setVisibility(View.GONE);
        button.bringToFront();
    }

    private static boolean isSettingsOrAppearanceScreen(Activity activity) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(activity.getWindow().getDecorView());
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 1600) {
            View view = queue.removeFirst();
            StringBuilder text = new StringBuilder();
            if (view instanceof TextView) {
                CharSequence value = ((TextView) view).getText();
                if (value != null) text.append(value).append(' ');
            }
            CharSequence description = view.getContentDescription();
            if (description != null) text.append(description).append(' ');
            if (view.getId() != View.NO_ID) {
                try {
                    text.append(activity.getResources().getResourceEntryName(view.getId()));
                } catch (RuntimeException ignored) {
                }
            }
            String normalized = text.toString().toLowerCase();
            if (normalized.contains("appearance") ||
                    normalized.contains("app settings") ||
                    normalized.equals("settings") ||
                    normalized.contains(" theme")) {
                return true;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int index = 0; index < group.getChildCount(); index++) {
                    queue.addLast(group.getChildAt(index));
                }
            }
        }
        return false;
    }

    private static void applyButtonTheme(Activity activity, TextView button) {
        boolean dark = (activity.getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int fill = dark ? 0xED1E1F22 : 0xEDF2F3F5;
        int stroke = 0xFF5865F2;
        int text = dark ? Color.WHITE : Color.BLACK;

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(activity, 24));
        shape.setStroke(dp(activity, 2), stroke);
        button.setTextColor(text);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x555865F2),
                shape,
                null
        ));
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
}
