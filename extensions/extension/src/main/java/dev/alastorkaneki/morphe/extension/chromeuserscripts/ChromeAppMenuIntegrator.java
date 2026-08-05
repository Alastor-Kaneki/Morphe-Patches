package dev.alastorkaneki.morphe.extension.chromeuserscripts;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Binds the resource-injected MonkeyScript rows inside Chrome's native app menu. */
final class ChromeAppMenuIntegrator implements Runnable {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Activity, ChromeAppMenuIntegrator> ACTIVE = new WeakHashMap<>();
    private static final Map<View, Boolean> BOUND_MANAGER = new WeakHashMap<>();
    private static final Map<View, Boolean> BOUND_INSTALL = new WeakHashMap<>();

    private final Activity activity;

    private ChromeAppMenuIntegrator(Activity activity) {
        this.activity = activity;
    }

    static void start(Activity activity) {
        stop(activity);
        ChromeAppMenuIntegrator integrator = new ChromeAppMenuIntegrator(activity);
        synchronized (ACTIVE) { ACTIVE.put(activity, integrator); }
        MAIN.post(integrator);
    }

    static void stop(Activity activity) {
        ChromeAppMenuIntegrator integrator;
        synchronized (ACTIVE) { integrator = ACTIVE.remove(activity); }
        if (integrator != null) MAIN.removeCallbacks(integrator);
    }

    @Override public void run() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            String url = MonkeyRuntime.url(activity);
            for (View root : windowRoots(activity)) bindRows(root, url);
        } catch (Throwable ignored) { }
        MAIN.postDelayed(this, 120);
    }

    private void bindRows(View root, String url) {
        List<TextView> labels = new ArrayList<>();
        collectTextViews(root, labels, 0);
        for (TextView label : labels) {
            CharSequence text = label.getText();
            if (text == null) continue;
            String value = text.toString().trim();
            if ("MonkeyScript".equals(value)) bindManager(rowFor(label));
            if ("Install userscript".equals(value)) bindInstall(rowFor(label), url);
        }
    }

    private void bindManager(View row) {
        if (row == null) return;
        row.setVisibility(View.VISIBLE);
        synchronized (BOUND_MANAGER) {
            if (BOUND_MANAGER.put(row, true) != null) return;
        }
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> activity.startActivity(
                new Intent(activity, UserscriptManagerActivity.class)
                        .putExtra("current_url", MonkeyRuntime.url(activity))
        ));
        row.setOnLongClickListener(view -> {
            String current = MonkeyRuntime.url(activity);
            if (ForkSiteSupport.isInstallablePage(current)) {
                ForkSiteSupport.openInstallPreview(activity, current);
                return true;
            }
            return false;
        });
    }

    private void bindInstall(View row, String url) {
        if (row == null) return;
        boolean visible = ForkSiteSupport.isInstallablePage(url);
        row.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        synchronized (BOUND_INSTALL) {
            if (BOUND_INSTALL.put(row, true) != null) return;
        }
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> {
            String current = MonkeyRuntime.url(activity);
            if (ForkSiteSupport.isInstallablePage(current)) {
                ForkSiteSupport.openInstallPreview(activity, current);
            }
        });
    }

    private static View rowFor(TextView label) {
        View current = label;
        for (int depth = 0; depth < 5; depth++) {
            if (current.isClickable() && current != label) return current;
            if (!(current.getParent() instanceof View)) break;
            View parent = (View) current.getParent();
            String name = parent.getClass().getName().toLowerCase();
            if (name.contains("menuitem") || name.contains("appmenu") || name.contains("list_item")) {
                return parent;
            }
            current = parent;
        }
        return label;
    }

    private static void collectTextViews(View view, List<TextView> output, int depth) {
        if (view == null || depth > 12 || output.size() > 250) return;
        if (view instanceof TextView) output.add((TextView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            collectTextViews(group.getChildAt(index), output, depth + 1);
        }
    }

    private static List<View> windowRoots(Activity activity) {
        List<View> roots = new ArrayList<>();
        View decor = activity.getWindow().getDecorView();
        if (decor != null) roots.add(decor);
        try {
            Class<?> type = Class.forName("android.view.WindowManagerGlobal");
            Method getInstance = type.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object global = getInstance.invoke(null);
            try {
                Method getRootViews = type.getDeclaredMethod("getRootViews");
                getRootViews.setAccessible(true);
                Object value = getRootViews.invoke(global);
                if (value instanceof View[]) {
                    Collections.addAll(roots, (View[]) value);
                } else if (value instanceof Iterable) {
                    for (Object item : (Iterable<?>) value) {
                        if (item instanceof View) roots.add((View) item);
                    }
                }
            } catch (Throwable ignored) {
                Field field = type.getDeclaredField("mRoots");
                field.setAccessible(true);
                Object value = field.get(global);
                if (value instanceof Iterable) {
                    for (Object root : (Iterable<?>) value) {
                        if (root == null) continue;
                        try {
                            Method getView = root.getClass().getDeclaredMethod("getView");
                            getView.setAccessible(true);
                            Object view = getView.invoke(root);
                            if (view instanceof View) roots.add((View) view);
                        } catch (Throwable ignoredRoot) { }
                    }
                }
            }
        } catch (Throwable ignored) { }
        return roots;
    }
}
