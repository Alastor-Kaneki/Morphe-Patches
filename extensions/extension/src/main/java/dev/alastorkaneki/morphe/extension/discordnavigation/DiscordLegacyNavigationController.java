package dev.alastorkaneki.morphe.extension.discordnavigation;

import android.app.Activity;
import android.app.Application;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Replaces Discord's 2026 You Bar with a classic four-destination bottom bar. */
public final class DiscordLegacyNavigationController
        implements Application.ActivityLifecycleCallbacks {
    private static final String BAR_TAG =
            "dev.alastorkaneki.morphe.discord.navigation.LEGACY_BAR";
    private static final long REFRESH_INTERVAL_MS = 700L;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final Map<Activity, Runnable> REFRESHERS = new WeakHashMap<>();
    private static final Map<Activity, View> YOU_BARS = new WeakHashMap<>();
    private static final Map<Activity, Map<View, Integer>> HIDDEN_VIEWS =
            new WeakHashMap<>();
    private static final Set<Activity> ACTIVE =
            Collections.newSetFromMap(new WeakHashMap<>());

    private DiscordLegacyNavigationController() {
    }

    public static void install(Application application) {
        if (INSTALLED.compareAndSet(false, true)) {
            application.registerActivityLifecycleCallbacks(
                    new DiscordLegacyNavigationController()
            );
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        synchronized (ACTIVE) {
            ACTIVE.add(activity);
        }
        attachLegacyBar(activity);
        refresh(activity);
        startRefresher(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        stopRefresher(activity);
        restoreHiddenViews(activity);
        detachLegacyBar(activity);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        stopRefresher(activity);
        restoreHiddenViews(activity);
        detachLegacyBar(activity);
        synchronized (ACTIVE) {
            ACTIVE.remove(activity);
        }
    }

    private static void startRefresher(Activity activity) {
        stopRefresher(activity);
        Runnable refresher = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                refresh(activity);
                activity.getWindow().getDecorView().postDelayed(this, REFRESH_INTERVAL_MS);
            }
        };
        synchronized (REFRESHERS) {
            REFRESHERS.put(activity, refresher);
        }
        activity.getWindow().getDecorView().post(refresher);
    }

    private static void stopRefresher(Activity activity) {
        Runnable refresher;
        synchronized (REFRESHERS) {
            refresher = REFRESHERS.remove(activity);
        }
        if (refresher != null) {
            activity.getWindow().getDecorView().removeCallbacks(refresher);
        }
    }

    private static void refresh(Activity activity) {
        View bar = activity.getWindow().getDecorView().findViewWithTag(BAR_TAG);
        if (bar == null) {
            attachLegacyBar(activity);
            bar = activity.getWindow().getDecorView().findViewWithTag(BAR_TAG);
        }
        locateAndSuppressYouBar(activity);
        if (bar != null) bar.bringToFront();
    }

    private static void attachLegacyBar(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        if (decor.findViewWithTag(BAR_TAG) != null) return;

        LinearLayout bar = new LinearLayout(activity);
        bar.setTag(BAR_TAG);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setElevation(dp(activity, 24));
        applyBarTheme(activity, bar);

        addDestination(activity, bar, "Servers", "Servers", Destination.SERVERS);
        addDestination(activity, bar, "Messages", "Messages", Destination.MESSAGES);
        addDestination(activity, bar, "Notifications", "Notifications",
                Destination.NOTIFICATIONS);
        addDestination(activity, bar, "You", "You", Destination.PROFILE);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 68),
                Gravity.BOTTOM
        );
        activity.addContentView(bar, params);
        bar.bringToFront();
    }

    private static void addDestination(
            Activity activity,
            LinearLayout bar,
            String label,
            String description,
            Destination destination
    ) {
        TextView item = new TextView(activity);
        item.setText(label);
        item.setContentDescription(description);
        item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        item.setTypeface(Typeface.DEFAULT_BOLD);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setPadding(dp(activity, 4), dp(activity, 8),
                dp(activity, 4), dp(activity, 8));
        applyItemTheme(activity, item);
        item.setOnClickListener(view -> navigate(activity, destination));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.0f
        );
        bar.addView(item, params);
    }

    private static void navigate(Activity activity, Destination destination) {
        View target = findBestTarget(activity, destination);
        if (target != null && clickTarget(target)) return;

        if ((destination == Destination.SERVERS || destination == Destination.MESSAGES) &&
                shouldToggleWorkspace(activity, destination) &&
                swipeYouBar(activity)) {
            return;
        }

        Toast.makeText(
                activity,
                "Discord did not expose the " + destination.label +
                        " destination on this build.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private static boolean clickTarget(View target) {
        View current = target;
        for (int depth = 0; depth < 5 && current != null; depth++) {
            if (current.isEnabled() && current.performClick()) return true;
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return false;
    }

    private static View findBestTarget(Activity activity, Destination destination) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(activity.getWindow().getDecorView());
        View best = null;
        int bestScore = 0;
        int visited = 0;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;

        while (!queue.isEmpty() && visited++ < 4500) {
            View view = queue.removeFirst();
            if (isLegacyBarView(view)) continue;

            String searchable = searchableText(activity, view);
            int score = score(searchable, destination);
            if (score > 0) {
                Rect rect = new Rect();
                if (view.getGlobalVisibleRect(rect)) {
                    if (rect.centerY() > screenHeight * 0.65f) score += 5;
                    if (rect.centerY() > screenHeight * 0.82f) score += 8;
                }
                if (view.isClickable()) score += 4;
                if (view.getVisibility() != View.VISIBLE) score -= 2;
                if (score > bestScore) {
                    bestScore = score;
                    best = view;
                }
            }

            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int index = 0; index < group.getChildCount(); index++) {
                    queue.addLast(group.getChildAt(index));
                }
            }
        }
        return bestScore >= 8 ? best : null;
    }

    private static int score(String value, Destination destination) {
        if (value.isEmpty()) return 0;
        int score = 0;
        for (String keyword : destination.keywords) {
            if (value.equals(keyword)) score = Math.max(score, 18);
            else if (value.contains(keyword)) score = Math.max(score, 10);
        }
        if (destination == Destination.SERVERS && value.contains("server settings")) score -= 12;
        if (destination == Destination.MESSAGES &&
                (value.contains("message input") || value.contains("send message"))) score -= 12;
        if (destination == Destination.PROFILE && value.contains("user profile")) score -= 5;
        return score;
    }

    private static String searchableText(Activity activity, View view) {
        StringBuilder text = new StringBuilder();
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null) text.append(value).append(' ');
        }
        CharSequence description = view.getContentDescription();
        if (description != null) text.append(description).append(' ');
        if (view.getId() != View.NO_ID) {
            try {
                text.append(activity.getResources().getResourceEntryName(view.getId()))
                        .append(' ');
            } catch (RuntimeException ignored) {
            }
        }
        text.append(view.getClass().getSimpleName());
        return text.toString().trim().toLowerCase();
    }

    private static void locateAndSuppressYouBar(Activity activity) {
        View current = YOU_BARS.get(activity);
        if (current != null && current.getParent() != null) {
            hide(activity, current);
            return;
        }

        View candidate = findYouBar(activity);
        if (candidate != null) {
            synchronized (YOU_BARS) {
                YOU_BARS.put(activity, candidate);
            }
            hide(activity, candidate);
        }
    }

    private static View findYouBar(Activity activity) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(activity.getWindow().getDecorView());
        View best = null;
        int bestScore = 0;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int visited = 0;

        while (!queue.isEmpty() && visited++ < 3500) {
            View view = queue.removeFirst();
            if (isLegacyBarView(view)) continue;

            Rect rect = new Rect();
            boolean visibleRect = view.getGlobalVisibleRect(rect);
            String searchable = searchableText(activity, view);
            int score = 0;
            if (searchable.contains("you_bar") || searchable.contains("youbar") ||
                    searchable.contains("user bar")) score += 30;
            if (searchable.contains("notifications")) score += 5;
            if (searchable.contains("status")) score += 3;
            if (searchable.contains("profile")) score += 3;
            if (visibleRect && rect.bottom >= screenHeight - dp(activity, 4)) score += 8;
            if (visibleRect && rect.height() >= dp(activity, 42) &&
                    rect.height() <= dp(activity, 130)) score += 5;
            if (view instanceof ViewGroup && ((ViewGroup) view).getChildCount() >= 2) score += 2;

            if (score > bestScore) {
                bestScore = score;
                best = view;
            }

            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int index = 0; index < group.getChildCount(); index++) {
                    queue.addLast(group.getChildAt(index));
                }
            }
        }
        return bestScore >= 18 ? best : null;
    }

    private static void hide(Activity activity, View view) {
        synchronized (HIDDEN_VIEWS) {
            Map<View, Integer> hidden = HIDDEN_VIEWS.get(activity);
            if (hidden == null) {
                hidden = new HashMap<>();
                HIDDEN_VIEWS.put(activity, hidden);
            }
            if (!hidden.containsKey(view)) hidden.put(view, view.getVisibility());
        }
        view.setVisibility(View.INVISIBLE);
    }

    private static void restoreHiddenViews(Activity activity) {
        Map<View, Integer> hidden;
        synchronized (HIDDEN_VIEWS) {
            hidden = HIDDEN_VIEWS.remove(activity);
        }
        if (hidden != null) {
            for (Map.Entry<View, Integer> entry : hidden.entrySet()) {
                if (entry.getKey() != null) entry.getKey().setVisibility(entry.getValue());
            }
        }
        synchronized (YOU_BARS) {
            YOU_BARS.remove(activity);
        }
    }

    private static boolean shouldToggleWorkspace(
            Activity activity,
            Destination destination
    ) {
        String visible = collectVisibleText(activity);
        boolean messages = visible.contains("direct messages") ||
                visible.contains("friends") || visible.contains("new message");
        boolean servers = visible.contains("channels") ||
                visible.contains("browse channels") || visible.contains("server guide");
        return (destination == Destination.SERVERS && messages) ||
                (destination == Destination.MESSAGES && servers);
    }

    private static String collectVisibleText(Activity activity) {
        StringBuilder output = new StringBuilder();
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(activity.getWindow().getDecorView());
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 1200 && output.length() < 12000) {
            View view = queue.removeFirst();
            if (view.getVisibility() == View.VISIBLE && !isLegacyBarView(view)) {
                output.append(searchableText(activity, view)).append(' ');
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int index = 0; index < group.getChildCount(); index++) {
                    queue.addLast(group.getChildAt(index));
                }
            }
        }
        return output.toString();
    }

    private static boolean swipeYouBar(Activity activity) {
        View bar;
        synchronized (YOU_BARS) {
            bar = YOU_BARS.get(activity);
        }
        if (bar == null || bar.getWidth() < 20 || bar.getHeight() < 20) return false;

        float y = bar.getHeight() / 2.0f;
        float startX = Math.max(4, bar.getWidth() * 0.12f);
        float endX = bar.getWidth() * 0.88f;
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, startX, y, 0
        );
        MotionEvent move = MotionEvent.obtain(
                downTime, downTime + 80, MotionEvent.ACTION_MOVE,
                (startX + endX) / 2.0f, y, 0
        );
        MotionEvent up = MotionEvent.obtain(
                downTime, downTime + 150, MotionEvent.ACTION_UP, endX, y, 0
        );
        try {
            bar.dispatchTouchEvent(down);
            bar.dispatchTouchEvent(move);
            return bar.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            move.recycle();
            up.recycle();
        }
    }

    private static boolean isLegacyBarView(View view) {
        View current = view;
        while (current != null) {
            if (BAR_TAG.equals(current.getTag())) return true;
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return false;
    }

    private static void detachLegacyBar(Activity activity) {
        View bar = activity.getWindow().getDecorView().findViewWithTag(BAR_TAG);
        if (bar != null && bar.getParent() instanceof ViewGroup) {
            ((ViewGroup) bar.getParent()).removeView(bar);
        }
    }

    private static void applyBarTheme(Activity activity, LinearLayout bar) {
        boolean dark = (activity.getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xFA111214 : 0xFAFFFFFF);
        background.setStroke(dp(activity, 1), dark ? 0xFF3F4147 : 0xFFD5D8DE);
        bar.setBackground(background);
    }

    private static void applyItemTheme(Activity activity, TextView item) {
        boolean dark = (activity.getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        item.setTextColor(dark ? Color.WHITE : 0xFF111214);
        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.TRANSPARENT);
        content.setCornerRadius(dp(activity, 18));
        item.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x555865F2),
                content,
                null
        ));
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private enum Destination {
        SERVERS("Servers", new String[] {
                "servers", "server list", "guilds", "guild list", "home"
        }),
        MESSAGES("Messages", new String[] {
                "messages", "direct messages", "dms", "friends"
        }),
        NOTIFICATIONS("Notifications", new String[] {
                "notifications", "inbox", "mentions"
        }),
        PROFILE("You", new String[] {
                "you", "profile", "account", "settings"
        });

        final String label;
        final String[] keywords;

        Destination(String label, String[] keywords) {
            this.label = label;
            this.keywords = keywords;
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
}
