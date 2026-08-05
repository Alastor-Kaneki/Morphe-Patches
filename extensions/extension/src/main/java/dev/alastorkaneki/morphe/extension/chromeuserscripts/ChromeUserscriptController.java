package dev.alastorkaneki.morphe.extension.chromeuserscripts;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Installs the floating monkey control and userscript runtime in Chrome activities. */
final class ChromeUserscriptController implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "dev.alastorkaneki.monkeyscript.BUTTON";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    static void install(Application application) {
        if (INSTALLED.compareAndSet(false, true)) {
            application.registerActivityLifecycleCallbacks(new ChromeUserscriptController());
        }
    }

    @Override public void onActivityResumed(Activity activity) {
        if (activity instanceof UserscriptManagerActivity || activity instanceof UserscriptEditorActivity) return;
        MonkeyRuntime.start(activity);
        attach(activity);
    }

    @Override public void onActivityPaused(Activity activity) { MonkeyRuntime.stop(activity); }
    @Override public void onActivityDestroyed(Activity activity) { MonkeyRuntime.stop(activity); remove(activity); }

    private static void attach(Activity activity) {
        View existing = activity.getWindow().getDecorView().findViewWithTag(TAG);
        if (existing != null) {
            existing.setVisibility(MonkeyStore.showButton(activity) ? View.VISIBLE : View.GONE);
            return;
        }
        TextView button = MonkeyUi.button(activity, "🐒", true);
        button.setTag(TAG);
        button.setTextSize(18);
        button.setContentDescription("Open MonkeyScript");
        button.setOnClickListener(view -> menu(activity));
        button.setOnLongClickListener(view -> {
            activity.startActivity(new Intent(activity, UserscriptManagerActivity.class));
            return true;
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM
        );
        params.setMargins(MonkeyUi.dp(activity, 16), MonkeyUi.dp(activity, 16),
                MonkeyUi.dp(activity, 16), MonkeyUi.dp(activity, 92));
        activity.addContentView(button, params);
        button.setVisibility(MonkeyStore.showButton(activity) ? View.VISIBLE : View.GONE);
    }

    private static void menu(Activity activity) {
        String url = MonkeyRuntime.url(activity);
        List<Userscript> matching = MonkeyRuntime.matches(activity);
        String[] items = {
                "Open dashboard",
                "Run matching scripts (" + matching.size() + ")",
                "Page script commands",
                MonkeyStore.hostDisabled(activity, url) ? "Enable scripts on this site" : "Disable scripts on this site",
                "Install from clipboard",
                MonkeyStore.globalEnabled(activity) ? "Pause all scripts" : "Resume all scripts",
                "Hide monkey button"
        };
        new AlertDialog.Builder(activity)
                .setTitle("MonkeyScript")
                .setMessage(url.isEmpty() ? "No active web page" : url)
                .setItems(items, (dialog, which) -> {
                    try {
                        switch (which) {
                            case 0:
                                activity.startActivity(new Intent(activity, UserscriptManagerActivity.class)
                                        .putExtra("current_url", url));
                                break;
                            case 1:
                                int count = 0;
                                for (Userscript script : matching) if (MonkeyRuntime.run(activity, script)) count++;
                                toast(activity, "Ran " + count + " scripts");
                                break;
                            case 2:
                                if (!MonkeyRuntime.commands(activity)) toast(activity, "Current page is unavailable");
                                break;
                            case 3:
                                MonkeyStore.hostDisabled(activity, url, !MonkeyStore.hostDisabled(activity, url));
                                MonkeyRuntime.refresh(activity);
                                break;
                            case 4:
                                clipboard(activity);
                                break;
                            case 5:
                                MonkeyStore.globalEnabled(activity, !MonkeyStore.globalEnabled(activity));
                                MonkeyRuntime.refresh(activity);
                                break;
                            case 6:
                                MonkeyStore.showButton(activity, false);
                                remove(activity);
                                break;
                            default:
                                break;
                        }
                    } catch (Throwable error) { toast(activity, error.getMessage()); }
                })
                .show();
    }

    private static void clipboard(Activity activity) {
        ClipboardManager manager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = manager == null ? null : manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) { toast(activity, "Clipboard is empty"); return; }
        String text = String.valueOf(clip.getItemAt(0).coerceToText(activity)).trim();
        MonkeyStore.Callback callback = (ok, message, script) -> activity.runOnUiThread(() -> {
            toast(activity, message); MonkeyRuntime.refresh(activity);
        });
        if (text.startsWith("http://") || text.startsWith("https://")) {
            MonkeyStore.installUrl(activity, text, callback);
        } else {
            MonkeyStore.importText(activity, text, "clipboard.user.js", "", callback);
        }
    }

    private static void remove(Activity activity) {
        View view = activity.getWindow().getDecorView().findViewWithTag(TAG);
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    static void toast(Activity activity, String message) {
        Toast.makeText(activity, message == null ? "Unknown error" : message, Toast.LENGTH_LONG).show();
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
}
