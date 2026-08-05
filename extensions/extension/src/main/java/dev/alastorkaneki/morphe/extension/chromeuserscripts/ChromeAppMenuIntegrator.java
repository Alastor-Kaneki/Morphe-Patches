package dev.alastorkaneki.morphe.extension.chromeuserscripts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

import java.lang.reflect.Array;
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
 * Adds MonkeyScript to Chrome's real Android app-menu model.
 *
 * This class never scans WindowManager windows or mutates popup/context-menu views. It first uses
 * known AppMenuHandler access paths, then performs a bounded object-graph search from the Chrome
 * Activity. A Menu is changed only after it matches a Chrome app-menu signature such as Settings,
 * History, Downloads, Bookmarks, or their stable resource-entry names. This keeps text, link,
 * image, and selection context menus untouched.
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
            } else if (lastMenu != null && isChromeAppMenu(lastMenu)) {
                bind(lastMenu, MonkeyRuntime.url(activity));
            }
        } catch (Throwable ignored) {
            lastMenu = null;
        }
        MAIN.postDelayed(this, 250);
    }

    private void bind(Menu menu, String url) {
        MenuItem manager = findByIdOrTitle(menu, MANAGER_ID, "Userscripts");
        if (manager == null) {
            manager = menu.add(Menu.NONE, MANAGER_ID, ORDER_MANAGER, "Userscripts");
        }
        Intent managerIntent = new Intent(activity, UserscriptManagerActivity.class)
                .putExtra("current_url", url)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        manager.setVisible(true);
        manager.setEnabled(true);
        manager.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        manager.setIntent(managerIntent);
        try { manager.setIcon(android.R.drawable.ic_menu_manage); } catch (Throwable ignored) { }
        manager.setOnMenuItemClickListener(item -> {
            activity.startActivity(new Intent(managerIntent));
            return true;
        });

        String marked = ForkSiteSupport.installUrlFromMarker(url);
        String target = marked == null ? url : marked;
        boolean installable = ForkSiteSupport.isInstallablePage(target);
        MenuItem install = findByIdOrTitle(menu, INSTALL_ID, "Install userscript");
        if (install == null) {
            install = menu.add(Menu.NONE, INSTALL_ID, ORDER_INSTALL, "Install userscript");
        }
        Intent installIntent = new Intent(activity, UserscriptInstallActivity.class)
                .putExtra("script_page_url", target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        install.setVisible(installable);
        install.setEnabled(installable);
        install.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        install.setIntent(installIntent);
        try { install.setIcon(android.R.drawable.stat_sys_download_done); } catch (Throwable ignored) { }
        install.setOnMenuItemClickListener(item -> {
            if (!ForkSiteSupport.isInstallablePage(target)) return false;
            activity.startActivity(new Intent(installIntent));
            return true;
        });
    }

    private Menu findChromeAppMenu() {
        Object handler = callNoArgs(activity, "getAppMenuHandler");
        Menu menu = findValidatedMenu(handler, 6, 220);
        if (menu != null) return menu;

        // Some Chrome channels rename the getter but keep an AppMenu-typed field or method.
        for (Method method : allMethods(activity.getClass())) {
            if (method.getParameterTypes().length != 0) continue;
            String methodName = method.getName().toLowerCase(Locale.US);
            String typeName = method.getReturnType().getName().toLowerCase(Locale.US);
            if (!methodName.contains("appmenu") && !typeName.contains("appmenu")) continue;
            try {
                method.setAccessible(true);
                menu = findValidatedMenu(method.invoke(activity), 6, 220);
                if (menu != null) return menu;
            } catch (Throwable ignored) { }
        }

        for (Field field : allFields(activity.getClass())) {
            String fieldName = field.getName().toLowerCase(Locale.US);
            String typeName = field.getType().getName().toLowerCase(Locale.US);
            if (!fieldName.contains("appmenu") && !typeName.contains("appmenu")) continue;
            try {
                field.setAccessible(true);
                menu = findValidatedMenu(field.get(activity), 6, 220);
                if (menu != null) return menu;
            } catch (Throwable ignored) { }
        }

        // Release builds may obfuscate every AppMenu name. Search the Activity's non-view object
        // graph, but only accept a Menu with a Chrome app-menu signature.
        return findValidatedMenu(activity, 7, 520);
    }

    private Menu findValidatedMenu(Object start, int maxDepth, int maxVisited) {
        if (start == null) return null;
        ArrayDeque<Node> queue = new ArrayDeque<>();
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        queue.add(new Node(start, 0));
        int visited = 0;

        while (!queue.isEmpty() && visited++ < maxVisited) {
            Node node = queue.removeFirst();
            Object value = node.value;
            if (value == null || seen.put(value, true) != null) continue;
            if (value instanceof Menu && isChromeAppMenu((Menu) value)) return (Menu) value;

            for (Method method : allMethods(value.getClass())) {
                if (method.getParameterTypes().length != 0
                        || !Menu.class.isAssignableFrom(method.getReturnType())) continue;
                try {
                    method.setAccessible(true);
                    Object result = method.invoke(value);
                    if (result instanceof Menu && isChromeAppMenu((Menu) result)) {
                        return (Menu) result;
                    }
                } catch (Throwable ignored) { }
            }

            if (node.depth >= maxDepth) continue;
            enqueueContainer(value, node.depth, queue);
            for (Field field : allFields(value.getClass())) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object child = field.get(value);
                    if (child instanceof Menu && isChromeAppMenu((Menu) child)) {
                        return (Menu) child;
                    }
                    if (shouldTraverse(child)) queue.addLast(new Node(child, node.depth + 1));
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private void enqueueContainer(Object value, int depth, ArrayDeque<Node> queue) {
        if (value == null) return;
        Class<?> type = value.getClass();
        if (type.isArray() && !type.getComponentType().isPrimitive()) {
            int length = Math.min(Array.getLength(value), 40);
            for (int index = 0; index < length; index++) {
                Object child = Array.get(value, index);
                if (shouldTraverse(child)) queue.addLast(new Node(child, depth + 1));
            }
        } else if (value instanceof Iterable) {
            int count = 0;
            for (Object child : (Iterable<?>) value) {
                if (count++ >= 40) break;
                if (shouldTraverse(child)) queue.addLast(new Node(child, depth + 1));
            }
        } else if (value instanceof Map) {
            int count = 0;
            for (Object child : ((Map<?, ?>) value).values()) {
                if (count++ >= 40) break;
                if (shouldTraverse(child)) queue.addLast(new Node(child, depth + 1));
            }
        }
    }

    private boolean shouldTraverse(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum
                || value instanceof Class
                || value instanceof Thread
                || value instanceof Handler
                || value instanceof Looper
                || value instanceof Bundle
                || value instanceof Intent
                || value instanceof Resources
                || value instanceof Drawable
                || value instanceof MenuItem
                || value instanceof View
                || value instanceof Window) return false;
        if (value instanceof Context && value != activity) return false;

        String name = value.getClass().getName();
        if (name.startsWith("java.lang.reflect.")
                || name.startsWith("java.io.")
                || name.startsWith("java.net.")
                || name.startsWith("android.os.")
                || name.startsWith("android.view.")
                || name.startsWith("android.widget.")) return false;

        ClassLoader loader = value.getClass().getClassLoader();
        return loader == activity.getClass().getClassLoader()
                || name.startsWith("org.chromium.")
                || name.startsWith("com.google.android.apps.chrome.")
                || name.startsWith("androidx.appcompat.view.menu.");
    }

    private boolean isChromeAppMenu(Menu menu) {
        try {
            if (menu == null || menu.size() < 4) return false;
            int anchors = 0;
            int supporting = 0;
            for (int index = 0; index < menu.size(); index++) {
                MenuItem item = menu.getItem(index);
                String title = item.getTitle() == null
                        ? ""
                        : item.getTitle().toString().toLowerCase(Locale.US);
                String resourceName = resourceName(item.getItemId());
                String signal = title + " " + resourceName;

                if (containsAny(signal,
                        "settings", "history", "downloads", "download_page", "bookmarks",
                        "bookmark", "recent tabs", "recent_tabs", "new incognito",
                        "new_incognito", "clear browsing")) {
                    anchors++;
                } else if (containsAny(signal,
                        "new tab", "new_tab", "find in page", "find_in_page", "translate",
                        "desktop site", "request_desktop", "share", "reload", "forward",
                        "open in chrome", "open_in_chrome")) {
                    supporting++;
                }
            }
            return anchors >= 1 && anchors + supporting >= 2;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String resourceName(int id) {
        if (id == 0 || id == Menu.NONE) return "";
        try {
            return activity.getResources().getResourceEntryName(id).toLowerCase(Locale.US);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
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
