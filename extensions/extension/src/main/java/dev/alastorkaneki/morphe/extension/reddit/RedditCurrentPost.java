package dev.alastorkaneki.morphe.extension.reddit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolve the *currently displayed* Reddit post when the in-app Originals
 * action is tapped. Prefer active/visible navigation fragments, never a
 * remembered feed item or clipboard entry.
 *
 * Public Android APIs are preferred; optional narrowly-scoped reflection
 * supports different Reddit builds using AndroidX fragments. Reflections and
 * view traversal are bounded and fail closed if the screen is ambiguous.
 */
public final class RedditCurrentPost {
    public static final class Match {
        public final String postId;
        public final String source;
        Match(String postId, String source) {
            this.postId = postId;
            this.source = source;
        }
    }

    private RedditCurrentPost() { }

    public static Match detect(Activity activity) {
        if (activity == null) return null;

        // The 2026.41.0-specific overflow event carries linkKindWithId as
        // t3_<id>; this is stronger evidence than Activity or Compose scanning.
        String eventPost = RedditNativeOverflowContext.recentPostId();
        if (eventPost != null)
            return new Match(eventPost, "native Reddit post overflow event");

        // A reused Reddit Activity can retain a stale startup intent. Prefer
        // the current resumed fragment and visible screen metadata instead.
        Match fragment = findActiveFragment(activity);
        if (fragment != null) return fragment;

        Match navigation = findNavigationController(activity);
        if (navigation != null) return navigation;

        Match screen = findOnScreen(activity);
        if (screen != null) return screen;

        // Finally, a true Reddit post deep-link or explicit intent post-id is
        // useful for builds with a single reused activity.
        try {
            Intent intent = activity.getIntent();
            if (intent != null) {
                String urlId = RedditGalleryLink.postId(intent.getDataString());
                if (urlId != null) return new Match(urlId, "Reddit post deep link");
                String id = fromBundle(intent.getExtras(), 0);
                if (id != null) return new Match(id, "Reddit navigation extras");
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    private static Match findActiveFragment(Activity activity) {
        try {
            Object supportManager = invoke(activity, "getSupportFragmentManager");
            Match result = fromFragmentManager(supportManager, 0);
            if (result != null) return result;
        } catch (RuntimeException ignored) { }
        try {
            Match result = fromFragmentManager(activity.getFragmentManager(), 0);
            if (result != null) return result;
        } catch (RuntimeException ignored) { }
        return null;
    }

    private static Match fromFragmentManager(Object manager, int depth) {
        if (manager == null || depth > 5) return null;
        Object primary = invoke(manager, "getPrimaryNavigationFragment");
        Match first = fromFragment(primary, depth + 1);
        if (first != null) return first;
        Object fragments = invoke(manager, "getFragments");
        if (!(fragments instanceof List)) return null;
        List<?> list = (List<?>) fragments;
        // Topmost screens commonly appear last in the FragmentManager list.
        Set<String> candidates = new LinkedHashSet<>();
        for (int i = list.size() - 1, inspected = 0; i >= 0 && inspected < 24; i--) {
            Object fragment = list.get(i);
            if (!isActive(fragment)) continue;
            inspected++;
            Match match = fromFragment(fragment, depth + 1);
            if (match != null) candidates.add(match.postId);
            if (candidates.size() > 1) return null;
        }
        return candidates.size() == 1 ?
                new Match(candidates.iterator().next(), "visible Reddit screen") : null;
    }

    private static Match fromFragment(Object fragment, int depth) {
        if (fragment == null || depth > 5 || !isActive(fragment)) return null;

        // Nested active navigation screen takes precedence over its container.
        Match child = fromFragmentManager(invoke(fragment, "getChildFragmentManager"), depth);
        if (child != null) return child;

        Match nav = fromNavController(invoke(fragment, "getNavController"));
        if (nav != null) return nav;

        Object args = invoke(fragment, "getArguments");
        if (args instanceof Bundle) {
            String id = fromBundle((Bundle) args, 0);
            if (id != null) return new Match(id, "active Reddit post navigation");
        }

        // Only known post-id/permalink fields (no traversal of the whole app
        // object graph, no access to credentials or cookies).
        String id = fromNamedFields(fragment);
        return id == null ? null : new Match(id, "active Reddit post model");
    }

    /** Support AndroidX Navigation/Compose host wrappers where fragments do not expose a post ID. */
    private static Match findNavigationController(Activity activity) {
        Match direct = fromNavController(invoke(activity, "getNavController"));
        if (direct != null) return direct;
        try {
            Class<?> navigation = Class.forName("androidx.navigation.Navigation");
            Method finder = navigation.getMethod("findNavController", View.class);
            Object controller = finder.invoke(null, activity.getWindow().getDecorView());
            return fromNavController(controller);
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static Match fromNavController(Object controller) {
        if (controller == null) return null;
        try {
            Object entry = invoke(controller, "getCurrentBackStackEntry");
            if (entry != null) {
                Object args = invoke(entry, "getArguments");
                if (args instanceof Bundle) {
                    String id = fromBundle((Bundle) args, 0);
                    if (id != null) return new Match(id, "current AndroidX navigation entry");
                }
            }
            Object destination = invoke(controller, "getCurrentDestination");
            if (destination != null) {
                Object route = invoke(destination, "getRoute");
                if (route instanceof String) {
                    String id = RedditPostToken.fromUntrustedText((String) route);
                    if (id != null) return new Match(id, "current navigation route");
                }
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    /** Minimal, value-free diagnostic report. No cookies, texts, account info or hidden objects. */
    public static String diagnosticReport(Activity activity) {
        StringBuilder b = new StringBuilder("Reddit Morphe native-post diagnostics\n");
        b.append("Android API ").append(android.os.Build.VERSION.SDK_INT).append('\n');
        if (activity == null) return b.append("No foreground activity\n").toString();
        b.append("Activity: ").append(activity.getClass().getName()).append('\n');
        b.append("Native overflow hook: ")
                .append(RedditNativeOverflowContext.recentPostId() == null ?
                        "NO RECENT EVENT" : "CAPTURED").append('\n');
        Match current = detect(activity);
        b.append("Detected: ").append(current == null ? "NONE" : current.postId + " (" + current.source + ")").append('\n');
        try {
            Intent intent = activity.getIntent();
            b.append("Intent post URL: ")
                    .append(intent != null && RedditGalleryLink.postId(intent.getDataString()) != null)
                    .append('\n');
            b.append("Intent /s/ share token: ")
                    .append(intent != null && RedditShareLink.find(intent.getDataString()) != null)
                    .append('\n');
            if (intent != null && intent.getExtras() != null) {
                b.append("Recognized extras: ");
                for (String key : intent.getExtras().keySet())
                    if (RedditPostToken.isPostKey(key) || RedditPostToken.isUrlKey(key))
                        b.append(key).append(' ');
                b.append('\n');
            }
        } catch (RuntimeException e) { b.append("Intent metadata unavailable\n"); }
        snapshotFragments(b, invoke(activity, "getSupportFragmentManager"), "AndroidX");
        snapshotFragments(b, activity.getFragmentManager(), "Framework");
        Match screen = findOnScreen(activity);
        b.append("Visible UI candidate: ").append(screen == null ? "NONE" : screen.postId).append('\n');
        return b.toString();
    }

    private static void snapshotFragments(StringBuilder b, Object manager, String title) {
        if (manager == null) return;
        Object fragments = invoke(manager, "getFragments");
        if (!(fragments instanceof List)) return;
        List<?> list = (List<?>) fragments;
        b.append(title).append(" fragments: ").append(list.size()).append('\n');
        for (int i = list.size() - 1, seen = 0; i >= 0 && seen < 10; i--) {
            Object f = list.get(i);
            if (f == null) continue;
            seen++;
            b.append(" - ").append(f.getClass().getName())
                .append(" active=").append(isActive(f)).append('\n');
            Object args = invoke(f, "getArguments");
            if (args instanceof Bundle) {
                b.append("   post-related argument keys: ");
                for (String key : ((Bundle) args).keySet()) {
                    if (RedditPostToken.isPostKey(key) || RedditPostToken.isUrlKey(key))
                        b.append(key).append(' ');
                }
                b.append('\n');
            }
        }
    }

    private static boolean isActive(Object fragment) {
        if (fragment == null) return false;
        Object resumed = invoke(fragment, "isResumed");
        Object visible = invoke(fragment, "isVisible");
        // Support fragment implementations that lack one of these methods.
        return !Boolean.FALSE.equals(resumed) && !Boolean.FALSE.equals(visible);
    }

    private static String fromBundle(Bundle bundle, int depth) {
        if (bundle == null || depth > 2) return null;
        try {
            Set<String> candidates = new LinkedHashSet<>();
            for (String key : bundle.keySet()) {
                Object value = bundle.get(key);
                if (value instanceof CharSequence) {
                    String id = RedditPostToken.isPostKey(key) ?
                            RedditPostToken.fromNamedField(value.toString()) :
                            RedditPostToken.isUrlKey(key) ?
                                    RedditGalleryLink.postId(value.toString()) : null;
                    if (id != null) candidates.add(id);
                } else if (value instanceof Bundle && depth < 2) {
                    String nested = fromBundle((Bundle) value, depth + 1);
                    if (nested != null) candidates.add(nested);
                }
                if (candidates.size() > 1) return null;
            }
            return candidates.size() == 1 ? candidates.iterator().next() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String fromNamedFields(Object source) {
        if (source == null) return null;
        Set<String> found = new LinkedHashSet<>();
        Class<?> cls = source.getClass();
        // Only the concrete fragment/model class and its closest superclass.
        for (int depth = 0; cls != null && depth < 2; depth++, cls = cls.getSuperclass()) {
            for (Field field : cls.getDeclaredFields()) {
                if (!RedditPostToken.isPostKey(field.getName())
                        && !RedditPostToken.isUrlKey(field.getName())) continue;
                if (field.getType() != String.class
                        && !CharSequence.class.isAssignableFrom(field.getType())) continue;
                try {
                    if (!field.isAccessible()) field.setAccessible(true);
                    Object value = field.get(source);
                    if (!(value instanceof CharSequence)) continue;
                    String id = RedditPostToken.isUrlKey(field.getName()) ?
                            RedditGalleryLink.postId(value.toString()) :
                            RedditPostToken.fromNamedField(value.toString());
                    if (id != null) found.add(id);
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
                if (found.size() > 1) return null;
            }
        }
        return found.size() == 1 ? found.iterator().next() : null;
    }

    private static Match findOnScreen(Activity activity) {
        View root;
        try { root = activity.getWindow().getDecorView(); }
        catch (RuntimeException ignored) { return null; }
        if (root == null) return null;
        Set<String> ids = new LinkedHashSet<>();
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 650) {
            View view = queue.removeFirst();
            if (view.getVisibility() != View.VISIBLE || !view.isShown()) continue;
            if (view.getTag() instanceof CharSequence) {
                String id = RedditPostToken.fromUntrustedText(view.getTag().toString());
                if (id != null) ids.add(id);
            }
            CharSequence description = view.getContentDescription();
            if (description != null) {
                String id = RedditGalleryLink.postId(description.toString());
                if (id != null) ids.add(id);
            }
            if (view instanceof WebView) {
                String id = RedditGalleryLink.postId(((WebView) view).getUrl());
                if (id != null) ids.add(id);
            }
            // Do not scan TextView body text: comments and feed cards can
            // mention unrelated posts, causing an incorrect download.
            if (ids.size() > 1) return null;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++)
                    queue.addLast(group.getChildAt(i));
            }
        }
        return ids.size() == 1 ?
                new Match(ids.iterator().next(), "visible Reddit post UI") : null;
    }

    private static Object invoke(Object object, String method) {
        if (object == null) return null;
        try {
            Method target = object.getClass().getMethod(method);
            return target.invoke(object);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
