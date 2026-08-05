package dev.alastorkaneki.morphe.extension.chromeuserscripts;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Adds MonkeyScript to Chrome's actual Android app-menu model.
 *
 * This intentionally never scans or modifies WindowManager popup/context-menu views. The old
 * live-view fallback could mistake selection/context menus for Chrome's overflow menu and corrupt
 * them. This implementation starts from Chrome's AppMenuHandler object and only searches its
 * object graph for the backing {@link Menu} instance.
 */
final class ChromeAppMenuIntegrator implements Runnable {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Activity, ChromeAppMenuIntegrator> ACTIVE = new WeakHashMap<>();

    private static final int MANAGER_ID = 0x4D530101;
    private static final int INSTALL_ID = 0x4D530102;
    private static final int ORDER_MANAGER = 0x6FFF0000;
    private static final int ORDER_INSTALL = 0x6FFF0001;

    private final Activity activity;
    private Menu lastMenu;

    private ChromeAppMenuIntegrator(Activity activity) {
        this.activity = activity;
    }

    static void start(Activity activity) {
        stop(activity);
        ChromeAppMenuIntegrator integrator = new ChromeAppMenuIntegrator(activity);
        synchronized (ACTIVE) {
            ACTIVE.put(activity, integrator);
        }
        MAIN.post(integrator);
    }

    static void stop(Activity activity) {
        ChromeAppMenuIntegrator integrator;
        synchronized (ACTIVE) {
            integrator = ACTIVE.remove(activity);
        }
        if (integrator != null) MAIN.removeCallbacks(integrator);
    }

    @Override public void run() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            Menu menu = findChromeAppMenu();
            if (menu != null) {
                lastMenu = menu;
                bind(menu, MonkeyRuntime.url(activity));
            } else if (lastMenu != null) {
                // Chrome may temporarily hide the handler while retaining the same menu object.
                bind(lastMenu, MonkeyRuntime.url(activity));
            }
        } catch (Throwable ignored) {
            lastMenu = null;
        }
        MAIN.postDelayed(this, 300);
    }

    private void bind(Menu menu, String url) {
        MenuItem manager = findByIdOrTitle(menu, MANAGER_ID, "MonkeyScript");
        if (manager == null) {
            manager = menu.add(Menu.NONE, MANAGER_ID, ORDER_MANAGER, "MonkeyScript");
        }
        manager.setVisible(true);
        manager.setEnabled(true);
        manager.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        try { manager.setIcon(android.R.drawable.ic_menu_manage); } catch (Throwable ignored) { }
        manager.setOnMenuItemClickListener(item -> {
            activity.startActivity(new Intent(activity, UserscriptManagerActivity.class)
                    .putExtra("current_url", MonkeyRuntime.url(activity)));
            return true;
        });

        MenuItem install = findByIdOrTitle(menu, INSTALL_ID, "Install userscript");
        if (install == null) {
            install = menu.add(Menu.NONE, INSTALL_ID, ORDER_INSTALL, "Install userscript");
        }
        boolean installable = ForkSiteSupport.isInstallablePage(url)
                || ForkSiteSupport.hasInstallMarker(url);
        install.setVisible(installable);
        install.setEnabled(installable);
        install.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        try { install.setIcon(android.R.drawable.stat_sys_download_done); } catch (Throwable ignored) { }
        install.setOnMenuItemClickListener(item -> {
            String current = MonkeyRuntime.url(activity);
            String marked = ForkSiteSupport.installUrlFromMarker(current);
            String target = marked == null ? current : marked;
            if (ForkSiteSupport.isInstallablePage(target)) {
                ForkSiteSupport.openInstallPreview(activity, target);
                return true;
            }
            return false;
        });
    }

    private Menu findChromeAppMenu() {
        Object handler = callNoArgs(activity, "getAppMenuHandler");
        Menu menu = findMenu(handler, 5);
        if (menu != null) return menu;

        // Some release builds rename the getter but keep an AppMenu-typed field.
        for (Field field : allFields(activity.getClass())) {
            String fieldName = field.getName().toLowerCase(Locale.US);
            String typeName = field.getType().getName().toLowerCase(Locale.US);
            if (!fieldName.contains("appmenu") && !typeName.contains("appmenu")) continue;
            try {
                field.setAccessible(true);
                menu = findMenu(field.get(activity), 5);
                if (menu != null) return menu;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static Menu findMenu(Object start, int maxDepth) {
        if (start == null) return null;
        ArrayDeque<Node> queue = new ArrayDeque<>();
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        queue.add(new Node(start, 0));
        int visited = 0;

        while (!queue.isEmpty() && visited++ < 180) {
            Node node = queue.removeFirst();
            Object value = node.value;
            if (value == null || seen.put(value, true) != null) continue;
            if (value instanceof Menu) return (Menu) value;

            for (Method method : allMethods(value.getClass())) {
                if (method.getParameterTypes().length != 0
                        || !Menu.class.isAssignableFrom(method.getReturnType())) continue;
                try {
                    method.setAccessible(true);
                    Object result = method.invoke(value);
                    if (result instanceof Menu) return (Menu) result;
                } catch (Throwable ignored) { }
            }

            if (node.depth >= maxDepth) continue;
            for (Field field : allFields(value.getClass())) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                String typeName = field.getType().getName().toLowerCase(Locale.US);
                String fieldName = field.getName().toLowerCase(Locale.US);
                if (!isAppMenuRelated(typeName, fieldName)) continue;
                try {
                    field.setAccessible(true);
                    Object child = field.get(value);
                    if (child instanceof Menu) return (Menu) child;
                    if (child != null) queue.addLast(new Node(child, node.depth + 1));
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private static boolean isAppMenuRelated(String typeName, String fieldName) {
        return typeName.contains("appmenu")
                || typeName.contains("menubuilder")
                || typeName.equals("android.view.menu")
                || typeName.startsWith("org.chromium.chrome.browser.ui.appmenu")
                || fieldName.contains("appmenu")
                || fieldName.equals("menu")
                || fieldName.endsWith("menu");
    }

    private static MenuItem findByIdOrTitle(Menu menu, int id, String title) {
        MenuItem item = menu.findItem(id);
        if (item != null) return item;
        for (int index = 0; index < menu.size(); index++) {
            MenuItem candidate = menu.getItem(index);
            CharSequence candidateTitle = candidate.getTitle();
            if (candidateTitle != null && title.contentEquals(candidateTitle)) return candidate;
        }
        return null;
    }

    private static Object callNoArgs(Object target, String name) {
        if (target == null) return null;
        for (Method method : allMethods(target.getClass())) {
            if (!name.equals(method.getName()) || method.getParameterTypes().length != 0) continue;
            try {
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> cursor = type; cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            try { Collections.addAll(result, cursor.getDeclaredFields()); }
            catch (Throwable ignored) { }
        }
        return result;
    }

    private static List<Method> allMethods(Class<?> type) {
        List<Method> result = new ArrayList<>();
        for (Class<?> cursor = type; cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            try { Collections.addAll(result, cursor.getDeclaredMethods()); }
            catch (Throwable ignored) { }
        }
        return result;
    }

    private static final class Node {
        final Object value;
        final int depth;
        Node(Object value, int depth) {
            this.value = value;
            this.depth = depth;
        }
    }
}
