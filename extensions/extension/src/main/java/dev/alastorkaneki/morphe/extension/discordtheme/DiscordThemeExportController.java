package dev.alastorkaneki.morphe.extension.discordtheme;

import android.app.Activity;
import android.app.Application;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
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
import android.widget.Toast;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Adds a theme-aware Export Theme button to Discord's theme screens. */
public final class DiscordThemeExportController
        implements Application.ActivityLifecycleCallbacks {
    private static final String BUTTON_TAG =
            "dev.alastorkaneki.morphe.extension.discordtheme.EXPORT_BUTTON";
    private static final long REFRESH_INTERVAL_MS = 700L;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final Map<Activity, Runnable> REFRESHERS = new WeakHashMap<>();

    private DiscordThemeExportController() {
    }

    public static void install(Application application) {
        if (INSTALLED.compareAndSet(false, true)) {
            application.registerActivityLifecycleCallbacks(
                    new DiscordThemeExportController()
            );
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        TextView button = attachButton(activity);
        startVisibilityRefresh(activity, button);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        stopVisibilityRefresh(activity);
        detachButton(activity);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        stopVisibilityRefresh(activity);
        detachButton(activity);
    }

    private static TextView attachButton(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        View existing = decor.findViewWithTag(BUTTON_TAG);
        if (existing instanceof TextView) {
            TextView button = (TextView) existing;
            applyTheme(activity, button);
            return button;
        }

        TextView button = new TextView(activity);
        button.setTag(BUTTON_TAG);
        button.setText("Export Theme  ↓");
        button.setContentDescription("Export current Discord custom theme as CSS and PNG");
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setElevation(dp(activity, 12));
        button.setPadding(
                dp(activity, 18),
                dp(activity, 11),
                dp(activity, 18),
                dp(activity, 11)
        );
        button.setVisibility(View.GONE);
        applyTheme(activity, button);

        button.setOnClickListener(view -> {
            if (!DiscordThemeScreenDetector.isThemeExportScreen(activity)) {
                Toast.makeText(
                        activity,
                        "Open Discord Settings > Appearance > Custom Theme first.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            button.setEnabled(false);
            button.setText("Exporting…");
            DiscordThemeExporter.export(activity, button, result -> {
                button.setEnabled(true);
                button.setText("Export Theme  ↓");
                if (result.success) {
                    Toast.makeText(
                            activity,
                            "Saved CSS and PNG to Downloads/DiscordThemes",
                            Toast.LENGTH_LONG
                    ).show();
                } else {
                    Toast.makeText(
                            activity,
                            "Theme export failed: " + result.message,
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM
        );
        params.setMargins(
                dp(activity, 16),
                dp(activity, 16),
                dp(activity, 16),
                dp(activity, 96)
        );

        activity.addContentView(button, params);
        button.bringToFront();
        return button;
    }

    private static void startVisibilityRefresh(Activity activity, TextView button) {
        stopVisibilityRefresh(activity);

        Runnable refresher = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed() ||
                        button.getParent() == null) {
                    return;
                }

                boolean visible = DiscordThemeScreenDetector.isThemeExportScreen(activity);
                button.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (visible) {
                    applyTheme(activity, button);
                    button.bringToFront();
                }
                button.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        };

        synchronized (REFRESHERS) {
            REFRESHERS.put(activity, refresher);
        }
        button.post(refresher);
    }

    private static void stopVisibilityRefresh(Activity activity) {
        Runnable refresher;
        synchronized (REFRESHERS) {
            refresher = REFRESHERS.remove(activity);
        }
        if (refresher != null) {
            activity.getWindow().getDecorView().removeCallbacks(refresher);
            View button = activity.getWindow().getDecorView().findViewWithTag(BUTTON_TAG);
            if (button != null) button.removeCallbacks(refresher);
        }
    }

    private static void detachButton(Activity activity) {
        View button = activity.getWindow().getDecorView().findViewWithTag(BUTTON_TAG);
        if (button != null && button.getParent() instanceof ViewGroup) {
            ((ViewGroup) button.getParent()).removeView(button);
        }
    }

    private static void applyTheme(Activity activity, TextView button) {
        boolean dark = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        int surface = resolveColor(activity, "colorSurface",
                dark ? 0xFF1E1F22 : 0xFFF2F3F5);
        int accent = resolveColor(activity, "colorPrimary",
                resolveColor(activity, "colorAccent", 0xFF5865F2));
        int text = bestTextColor(surface);
        int fill = blend(surface, accent, dark ? 0.22f : 0.13f);
        text = bestTextColor(fill);
        int stroke = ensureContrast(accent, fill, dark);
        int ripple = Color.argb(
                dark ? 0x55 : 0x3D,
                Color.red(stroke),
                Color.green(stroke),
                Color.blue(stroke)
        );

        button.setTextColor(text);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(activity, 28));
        shape.setStroke(dp(activity, 2), stroke);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(ripple),
                shape,
                null
        ));
    }

    private static int resolveColor(Activity activity, String attrName, int fallback) {
        int appAttr = activity.getResources().getIdentifier(
                attrName,
                "attr",
                activity.getPackageName()
        );
        Integer color = resolveAttribute(activity, appAttr);
        if (color != null) return color;

        int androidAttr = activity.getResources().getIdentifier(attrName, "attr", "android");
        color = resolveAttribute(activity, androidAttr);
        return color == null ? fallback : color;
    }

    @SuppressWarnings("deprecation")
    private static Integer resolveAttribute(Activity activity, int attrId) {
        if (attrId == 0) return null;
        TypedValue value = new TypedValue();
        if (!activity.getTheme().resolveAttribute(attrId, value, true)) return null;
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        if (value.resourceId == 0) return null;
        try {
            return activity.getResources().getColor(value.resourceId, activity.getTheme());
        } catch (Throwable ignored) {
            try {
                return activity.getResources().getColor(value.resourceId);
            } catch (Throwable ignoredAgain) {
                return null;
            }
        }
    }

    private static int ensureContrast(int candidate, int background, boolean dark) {
        if (contrast(candidate, background) >= 1.8) return candidate;
        return blend(candidate, dark ? Color.WHITE : Color.BLACK, 0.42f);
    }

    private static int bestTextColor(int background) {
        return contrast(Color.WHITE, background) >= contrast(Color.BLACK, background)
                ? Color.WHITE
                : Color.BLACK;
    }

    private static int blend(int first, int second, float amount) {
        float inverse = 1.0f - amount;
        return Color.rgb(
                Math.round(Color.red(first) * inverse + Color.red(second) * amount),
                Math.round(Color.green(first) * inverse + Color.green(second) * amount),
                Math.round(Color.blue(first) * inverse + Color.blue(second) * amount)
        );
    }

    private static double contrast(int first, int second) {
        double light = Math.max(luminance(first), luminance(second));
        double dark = Math.min(luminance(first), luminance(second));
        return (light + 0.05) / (dark + 0.05);
    }

    private static double luminance(int color) {
        double red = linear(Color.red(color) / 255.0);
        double green = linear(Color.green(color) / 255.0);
        double blue = linear(Color.blue(color) / 255.0);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linear(double channel) {
        return channel <= 0.04045
                ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
}
