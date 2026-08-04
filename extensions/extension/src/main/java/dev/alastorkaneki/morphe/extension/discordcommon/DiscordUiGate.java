package dev.alastorkaneki.morphe.extension.discordcommon;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Shared conservative screen detection for Discord overlays. */
public final class DiscordUiGate {
    private static final String MORPHE_PREFIX = "dev.alastorkaneki.morphe";
    private static final int MAX_VIEWS = 3000;

    private DiscordUiGate() {
    }

    public static boolean isBlockedScreen(Activity activity) {
        String text = collectVisibleText(activity, 1800, 16000);
        return text.contains("welcome to discord") ||
                text.contains("tap below to get started") ||
                text.contains("create an account") ||
                text.contains("email or phone number") ||
                text.contains("forgot your password") ||
                text.contains("verify your account") ||
                text.contains("phone verification") ||
                text.contains("date of birth") ||
                text.contains("age gate") ||
                text.contains("captcha") ||
                containsWholeWord(text, "register") ||
                containsWholeWord(text, "login") ||
                text.contains("log in") ||
                text.contains("sign up");
    }

    public static boolean hasAuthenticatedNavigation(Activity activity) {
        if (isBlockedScreen(activity)) return false;

        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(activity.getWindow().getDecorView());
        Set<String> signals = new HashSet<>();
        int score = 0;
        int inspected = 0;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;

        while (!queue.isEmpty() && inspected++ < MAX_VIEWS) {
            View view = queue.removeFirst();
            if (isMorpheView(view)) continue;

            if (view.getVisibility() == View.VISIBLE) {
                String text = searchableText(view);
                Rect rect = new Rect();
                boolean visible = view.getGlobalVisibleRect(rect);
                boolean nearBottom = visible && rect.centerY() > screenHeight * 0.62f;

                score += navigationSignal(text, "you bar", "youbar", "you_bar", signals, nearBottom, 12);
                score += navigationSignal(text, "bottom navigation", "bottom_navigation", "bottom nav", signals, nearBottom, 10);
                score += navigationSignal(text, "direct messages", "direct_messages", "dms", signals, nearBottom, 7);
                score += navigationSignal(text, "notifications", "notification", "inbox", signals, nearBottom, 6);
                score += navigationSignal(text, "servers", "server list", "guilds", signals, nearBottom, 6);
                score += navigationSignal(text, "messages", "friends", "mentions", signals, nearBottom, 4);
                score += navigationSignal(text, "channels", "channel list", "guild list", signals, nearBottom, 4);
                score += navigationSignal(text, "profile", "account", "user settings", signals, nearBottom, 3);
            }

            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int index = 0; index < group.getChildCount(); index++) {
                    queue.addLast(group.getChildAt(index));
                }
            }
        }

        return score >= 12 || signals.size() >= 2;
    }

    public static String collectVisibleText(Activity activity, int maxViews, int maxChars) {
        StringBuilder output = new StringBuilder();
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(activity.getWindow().getDecorView());
        int inspected = 0;

        while (!queue.isEmpty() && inspected++ < maxViews && output.length() < maxChars) {
            View view = queue.removeFirst();
            if (isMorpheView(view)) continue;

            if (view.getVisibility() == View.VISIBLE) {
                String text = searchableText(view);
                if (!text.isEmpty()) output.append(text).append(' ');
            }

            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int index = 0; index < group.getChildCount(); index++) {
                    queue.addLast(group.getChildAt(index));
                }
            }
        }
        return output.toString().toLowerCase(Locale.US);
    }

    public static boolean isMorpheView(View view) {
        View current = view;
        while (current != null) {
            Object tag = current.getTag();
            if (tag != null && tag.toString().startsWith(MORPHE_PREFIX)) return true;
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return false;
    }

    public static String searchableText(View view) {
        StringBuilder result = new StringBuilder();
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            append(result, textView.getText());
            append(result, textView.getHint());
        }
        append(result, view.getContentDescription());

        if (view.getId() != View.NO_ID) {
            try {
                append(result, view.getResources().getResourceEntryName(view.getId()));
            } catch (Resources.NotFoundException ignored) {
            }
        }
        return result.toString().trim().toLowerCase(Locale.US).replace('_', ' ');
    }

    private static int navigationSignal(
            String text,
            String first,
            String second,
            String third,
            Set<String> signals,
            boolean nearBottom,
            int weight
    ) {
        if (!(text.contains(first) || text.contains(second) || text.contains(third))) return 0;
        signals.add(first);
        return weight + (nearBottom ? 4 : 0);
    }

    private static boolean containsWholeWord(String text, String word) {
        int start = 0;
        while ((start = text.indexOf(word, start)) >= 0) {
            int end = start + word.length();
            boolean left = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
            boolean right = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (left && right) return true;
            start = end;
        }
        return false;
    }

    private static void append(StringBuilder builder, Object value) {
        if (value == null) return;
        String text = value.toString().trim();
        if (text.isEmpty()) return;
        if (builder.length() > 0) builder.append(' ');
        builder.append(text);
    }
}
