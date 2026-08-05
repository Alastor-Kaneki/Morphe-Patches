package dev.alastorkaneki.morphe.extension.chromeuserscripts;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Embeds MonkeyScript in Chrome's displayed app menu.
 *
 * Newer Chrome packages do not consistently ship readable res/menu XML files.
 * When the resource fast path is unavailable, this class identifies Chrome's
 * separate app-menu window and appends native-looking rows to that live view.
 */
final class ChromeAppMenuIntegrator implements Runnable {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Activity, ChromeAppMenuIntegrator> ACTIVE = new WeakHashMap<>();
    private static final Map<View, Boolean> BOUND_MANAGER = new WeakHashMap<>();
    private static final Map<View, Boolean> BOUND_INSTALL = new WeakHashMap<>();

    private static final String CONTAINER_TAG =
            "dev.alastorkaneki.monkeyscript.CHROME_MENU_CONTAINER";
    private static final String MANAGER_TAG =
            "dev.alastorkaneki.monkeyscript.CHROME_MENU_MANAGER";
    private static final String INSTALL_TAG =
            "dev.alastorkaneki.monkeyscript.CHROME_MENU_INSTALL";

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
            View decor = activity.getWindow().getDecorView();
            for (View root : windowRoots(activity)) {
                boolean resourceRowsPresent = bindRows(root, url);
                if (!resourceRowsPresent && root != decor) {
                    injectRuntimeRows(root, url);
                }
            }
        } catch (Throwable ignored) { }
        MAIN.postDelayed(this, 120);
    }

    /** Returns true when XML/resource-created MonkeyScript rows were found. */
    private boolean bindRows(View root, String url) {
        List<TextView> labels = new ArrayList<>();
        collectTextViews(root, labels, 0);
        boolean found = false;
        for (TextView label : labels) {
            CharSequence text = label.getText();
            if (text == null) continue;
            String value = text.toString().trim();
            if ("MonkeyScript".equals(value)) {
                bindManager(rowFor(label));
                found = true;
            }
            if ("Install userscript".equals(value)) {
                bindInstall(rowFor(label), url);
                found = true;
            }
        }
        return found;
    }

    private void injectRuntimeRows(View root, String url) {
        View existing = root.findViewWithTag(CONTAINER_TAG);
        if (existing instanceof ViewGroup) {
            updateRuntimeRows((ViewGroup) existing, url);
            return;
        }

        Candidate candidate = findMenuCandidate(root);
        if (candidate == null || candidate.score < 70) return;

        Attachment attachment = chooseAttachment(root, candidate.group);
        if (attachment == null) return;

        LinearLayout container = new LinearLayout(activity);
        container.setTag(CONTAINER_TAG);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setClickable(false);
        container.setFocusable(false);

        TextView manager = createMenuRow(candidate.exemplar, "MonkeyScript", MANAGER_TAG);
        bindManager(manager);
        container.addView(manager, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                rowHeight(candidate.exemplar)
        ));

        TextView install = createMenuRow(candidate.exemplar, "Install userscript", INSTALL_TAG);
        bindInstall(install, url);
        container.addView(install, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                rowHeight(candidate.exemplar)
        ));

        int footerHeight = rowHeight(candidate.exemplar)
                + (install.getVisibility() == View.VISIBLE ? rowHeight(candidate.exemplar) : 0);
        attachment.add(container, footerHeight);
        container.requestLayout();
        attachment.host.requestLayout();
    }

    private void updateRuntimeRows(ViewGroup container, String url) {
        View manager = container.findViewWithTag(MANAGER_TAG);
        if (manager != null) bindManager(manager);
        View install = container.findViewWithTag(INSTALL_TAG);
        if (install != null) bindInstall(install, url);
    }

    private TextView createMenuRow(TextView exemplar, String title, String tag) {
        TextView row = new TextView(activity);
        row.setTag(tag);
        row.setText(title);
        row.setGravity(exemplar == null ? Gravity.CENTER_VERTICAL : exemplar.getGravity());
        row.setSingleLine(true);
        row.setEllipsize(TextUtils.TruncateAt.END);
        row.setClickable(true);
        row.setFocusable(true);
        row.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                exemplar == null ? sp(16) : exemplar.getTextSize()
        );
        row.setTypeface(exemplar == null ? Typeface.DEFAULT : exemplar.getTypeface());
        if (exemplar != null) {
            row.setTextColor(exemplar.getTextColors());
            row.setCompoundDrawablePadding(exemplar.getCompoundDrawablePadding());
            row.setPadding(
                    Math.max(exemplar.getPaddingLeft(), dp(18)),
                    exemplar.getPaddingTop(),
                    Math.max(exemplar.getPaddingRight(), dp(18)),
                    exemplar.getPaddingBottom()
            );
        } else {
            TypedValue color = new TypedValue();
            if (activity.getTheme().resolveAttribute(android.R.attr.textColorPrimary, color, true)
                    && color.resourceId != 0) {
                row.setTextColor(activity.getResources().getColorStateList(color.resourceId, activity.getTheme()));
            }
            row.setPadding(dp(18), 0, dp(18), 0);
        }

        TypedValue background = new TypedValue();
        if (activity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground,
                background,
                true
        ) && background.resourceId != 0) {
            row.setBackgroundResource(background.resourceId);
        }
        return row;
    }

    private int rowHeight(TextView exemplar) {
        if (exemplar != null) {
            View nativeRow = rowFor(exemplar);
            int measured = nativeRow == null ? 0 : nativeRow.getHeight();
            if (measured >= dp(40) && measured <= dp(96)) return measured;
            int minimum = nativeRow == null ? 0 : nativeRow.getMinimumHeight();
            if (minimum >= dp(40) && minimum <= dp(96)) return minimum;
        }
        return dp(48);
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

    private Candidate findMenuCandidate(View root) {
        List<Candidate> candidates = new ArrayList<>();
        collectCandidates(root, root, candidates, 0);
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (best == null || candidate.score > best.score) best = candidate;
        }
        return best;
    }

    private void collectCandidates(
            View root,
            View view,
            List<Candidate> output,
            int depth
    ) {
        if (view == null || depth > 14 || output.size() > 180) return;
        if (view instanceof ViewGroup && view.isShown()) {
            ViewGroup group = (ViewGroup) view;
            List<TextView> labels = new ArrayList<>();
            collectTextViews(group, labels, 0);
            TextView exemplar = firstMenuLikeLabel(labels);
            int score = scoreCandidate(root, group, labels, exemplar);
            if (score > 0) output.add(new Candidate(group, exemplar, score));
            for (int index = 0; index < group.getChildCount(); index++) {
                collectCandidates(root, group.getChildAt(index), output, depth + 1);
            }
        }
    }

    private int scoreCandidate(
            View root,
            ViewGroup group,
            List<TextView> labels,
            TextView exemplar
    ) {
        if (labels.size() < 3 || exemplar == null) return 0;
        String classes = classPath(group);
        int score = 0;
        if (classes.contains("appmenu")) score += 120;
        if (classes.contains("popupmenu") || classes.contains("menupopup")) score += 90;
        if (classes.contains("popup")) score += 35;
        if (classes.contains("menu")) score += 25;
        if (classes.contains("recyclerview") || classes.contains("listview")) score += 20;
        if (root.getClass().getName().toLowerCase(Locale.US).contains("popup")) score += 25;

        int usefulLabels = 0;
        for (TextView label : labels) {
            CharSequence text = label.getText();
            if (text == null || text.toString().trim().isEmpty()) continue;
            if (rowFor(label).isClickable() || label.isClickable()) usefulLabels++;
        }
        score += Math.min(usefulLabels, 10) * 5;

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int width = group.getWidth();
        if (width > dp(160) && width < screenWidth * 0.9f) score += 20;
        if (group.getHeight() > dp(150)) score += 10;
        if (group instanceof AdapterView || classes.contains("recyclerview")) score += 10;
        return score;
    }

    private TextView firstMenuLikeLabel(List<TextView> labels) {
        for (TextView label : labels) {
            CharSequence text = label.getText();
            if (text == null || text.toString().trim().isEmpty()) continue;
            View row = rowFor(label);
            if (label.isClickable() || (row != null && row.isClickable())) return label;
        }
        for (TextView label : labels) {
            CharSequence text = label.getText();
            if (text != null && !text.toString().trim().isEmpty()) return label;
        }
        return null;
    }

    private Attachment chooseAttachment(View root, ViewGroup candidate) {
        if (isAppendable(candidate)) return new Attachment(candidate, candidate, false);

        View current = candidate;
        for (int depth = 0; depth < 6; depth++) {
            if (!(current.getParent() instanceof ViewGroup)) break;
            ViewGroup parent = (ViewGroup) current.getParent();
            if (isAppendable(parent)) {
                boolean overlay = !(parent instanceof LinearLayout);
                return new Attachment(parent, candidate, overlay);
            }
            current = parent;
        }

        if (root instanceof ViewGroup && isAppendable((ViewGroup) root)) {
            return new Attachment((ViewGroup) root, candidate, true);
        }
        return null;
    }

    private static boolean isAppendable(ViewGroup group) {
        String name = group.getClass().getName().toLowerCase(Locale.US);
        return !(group instanceof AdapterView)
                && !(group instanceof ScrollView)
                && !name.contains("recyclerview")
                && !name.contains("viewpager");
    }

    private static View rowFor(TextView label) {
        View current = label;
        for (int depth = 0; depth < 5; depth++) {
            if (current.isClickable() && current != label) return current;
            if (!(current.getParent() instanceof View)) break;
            View parent = (View) current.getParent();
            String name = parent.getClass().getName().toLowerCase(Locale.US);
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

    private static String classPath(View view) {
        StringBuilder output = new StringBuilder();
        View current = view;
        for (int depth = 0; depth < 6 && current != null; depth++) {
            output.append(' ').append(current.getClass().getName().toLowerCase(Locale.US));
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return output.toString();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private float sp(int value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                activity.getResources().getDisplayMetrics()
        );
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

    private static final class Candidate {
        final ViewGroup group;
        final TextView exemplar;
        final int score;

        Candidate(ViewGroup group, TextView exemplar, int score) {
            this.group = group;
            this.exemplar = exemplar;
            this.score = score;
        }
    }

    private static final class Attachment {
        final ViewGroup host;
        final View menuBody;
        final boolean overlay;

        Attachment(ViewGroup host, View menuBody, boolean overlay) {
            this.host = host;
            this.menuBody = menuBody;
            this.overlay = overlay;
        }

        void add(View container, int height) {
            if (host instanceof LinearLayout) {
                host.addView(container, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                return;
            }

            if (overlay && menuBody.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams body =
                        (ViewGroup.MarginLayoutParams) menuBody.getLayoutParams();
                body.bottomMargin = Math.max(body.bottomMargin, height);
                menuBody.setLayoutParams(body);
            }

            if (host instanceof FrameLayout) {
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM
                );
                host.addView(container, params);
            } else {
                host.addView(container, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
        }
    }
}
