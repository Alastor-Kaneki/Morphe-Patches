package dev.alastorkaneki.morphe.extension.chromeuserscripts;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Full-screen installation review for direct, Greasy Fork, and Sleazy Fork userscripts. */
public final class UserscriptInstallActivity extends Activity {
    private TextView status;
    private TextView install;
    private TextView sourceView;
    private Userscript parsed;
    private String resolvedUrl;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        MonkeyUi.window(this);
        render();
        String pageUrl = getIntent().getStringExtra("script_page_url");
        if (pageUrl == null || pageUrl.trim().isEmpty()) {
            fail("No userscript URL was supplied");
            return;
        }
        load(pageUrl.trim());
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.setBackgroundColor(MonkeyUi.bg(this));

        TextView title = label("Install with MonkeyScript", 24, true, MonkeyUi.text(this));
        root.addView(title);
        status = label("Resolving userscript…", 14, false, MonkeyUi.muted(this));
        status.setPadding(0, dp(8), 0, dp(10));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        sourceView = label("", 12, false, MonkeyUi.text(this));
        sourceView.setTypeface(Typeface.MONOSPACE);
        sourceView.setTextIsSelectable(true);
        sourceView.setPadding(dp(12), dp(12), dp(12), dp(12));
        sourceView.setBackground(MonkeyUi.card(this));
        scroll.addView(sourceView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        TextView cancel = MonkeyUi.button(this, "Cancel", false);
        cancel.setOnClickListener(view -> finish());
        actions.addView(cancel, params(-2, -2, 8, 8));
        install = MonkeyUi.button(this, "Install", true);
        install.setEnabled(false);
        install.setAlpha(0.45f);
        install.setOnClickListener(view -> install());
        actions.addView(install, params(-2, -2, 8, 0));
        root.addView(actions);
        setContentView(root);
    }

    private void load(String pageUrl) {
        new Thread(() -> {
            try {
                String url = ForkSiteSupport.resolveInstallUrl(pageUrl);
                String source = MonkeyStore.fetch(url);
                Userscript script = UserscriptMetadataParser.parse(source, fileName(url), url);
                runOnUiThread(() -> ready(url, script));
            } catch (Throwable error) {
                runOnUiThread(() -> fail(error.getMessage() == null
                        ? error.getClass().getSimpleName()
                        : error.getMessage()));
            }
        }, "MonkeyScript-install").start();
    }

    private void ready(String url, Userscript script) {
        resolvedUrl = url;
        parsed = script;
        String kind = Userscript.KIND_CSS.equals(script.kind) ? "CSS userstyle" : "JavaScript userscript";
        status.setText(script.name + "\n"
                + kind + " • v" + script.version + " • "
                + (script.matches.size() + script.includes.size()) + " site rule(s)\n"
                + (script.description.isEmpty() ? url : script.description));
        sourceView.setText(script.source);
        install.setEnabled(true);
        install.setAlpha(1f);
    }

    private void fail(String message) {
        status.setText("Cannot install userscript\n" + (message == null ? "Unknown error" : message));
        sourceView.setText("");
        install.setEnabled(false);
        install.setAlpha(0.45f);
    }

    private void install() {
        if (parsed == null || resolvedUrl == null) return;
        install.setEnabled(false);
        status.setText("Installing " + parsed.name + "…");
        MonkeyStore.importText(this, parsed.source, fileName(resolvedUrl), resolvedUrl,
                (ok, message, script) -> runOnUiThread(() -> {
                    ChromeUserscriptController.toast(this, message);
                    if (ok) finish();
                    else install.setEnabled(true);
                }));
    }

    private TextView label(String text, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams params(int width, int height, int top, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(0, dp(top), dp(right), 0);
        return params;
    }

    private int dp(int value) { return MonkeyUi.dp(this, value); }

    private static String fileName(String url) {
        String segment = android.net.Uri.parse(url).getLastPathSegment();
        if (segment == null || segment.trim().isEmpty()) return "fork.user.js";
        return segment.toLowerCase(Locale.US).contains(".user.") ? segment : segment + ".user.js";
    }
}
