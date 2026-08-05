package dev.alastorkaneki.morphe.extension.chromeuserscripts;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/** Dependency-free visual style combining the major monkey userscript managers. */
final class MonkeyUi {
    static final int ORANGE = 0xFFFF8A24;
    static final int PURPLE = 0xFF7C5CFF;
    static final int RED = 0xFFFF4E64;

    static boolean dark(Activity activity) {
        return MonkeyStore.amoled(activity) || (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static int bg(Activity activity) {
        return MonkeyStore.amoled(activity) ? Color.BLACK : dark(activity) ? 0xFF101218 : 0xFFF5F6FA;
    }

    static int surface(Activity activity) {
        return MonkeyStore.amoled(activity) ? 0xFF090A0D : dark(activity) ? 0xFF1C1F28 : Color.WHITE;
    }

    static int text(Activity activity) { return dark(activity) ? Color.WHITE : 0xFF15171E; }
    static int muted(Activity activity) { return dark(activity) ? 0xFFB0B5C4 : 0xFF626A7A; }

    static void window(Activity activity) {
        activity.getWindow().setStatusBarColor(bg(activity));
        activity.getWindow().setNavigationBarColor(bg(activity));
        activity.getWindow().getDecorView().setBackgroundColor(bg(activity));
    }

    static TextView button(Activity activity, String label, boolean primary) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextColor(primary ? Color.WHITE : text(activity));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        view.setClickable(true);
        view.setFocusable(true);
        GradientDrawable shape = primary
                ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ORANGE, PURPLE, RED})
                : new GradientDrawable();
        if (!primary) { shape.setColor(surface(activity)); shape.setStroke(dp(activity, 1), PURPLE); }
        shape.setCornerRadius(dp(activity, 22));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), shape, null));
        return view;
    }

    static GradientDrawable card(Activity activity) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(surface(activity));
        drawable.setCornerRadius(dp(activity, 16));
        drawable.setStroke(dp(activity, 1), 0x667C5CFF);
        return drawable;
    }

    static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
