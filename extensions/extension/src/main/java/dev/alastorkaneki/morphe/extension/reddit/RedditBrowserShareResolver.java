package dev.alastorkaneki.morphe.extension.reddit;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.net.URL;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Browser-network fallback for native Reddit /r/<community>/s/<token> redirects.
 *
 * Reddit can reject HEAD/GET from HttpURLConnection with 403 even though the
 * same navigation works in the device's browser. This temporary WebView follows
 * only Reddit HTTPS hosts; its purpose is to find the canonical post URL.
 * No JavaScript interface, injected credentials, persistent data collection,
 * or arbitrary external navigation is used.
 */
public final class RedditBrowserShareResolver {
    public interface Callback {
        void onResolved(String postId, String failure);
    }

    private RedditBrowserShareResolver() { }

    public static void resolve(Activity activity, String text, Callback callback) {
        String link = RedditShareLink.find(text);
        if (link == null) {
            callback.onResolved(null, "No recognized Reddit /s/ share URL");
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) {
            callback.onResolved(null, "Host Activity is no longer available");
            return;
        }
        final FrameLayout root = activity.getWindow().getDecorView()
                .findViewById(android.R.id.content);
        if (root == null) {
            callback.onResolved(null, "Could not attach browser resolver");
            return;
        }
        final WebView browser;
        try {
            browser = new WebView(activity);
        } catch (RuntimeException e) {
            callback.onResolved(null, "Android WebView unavailable: " + e.getMessage());
            return;
        }

        // One pixel, attached to a live Activity so WebView can actually
        // process redirects; no user-visible fake Reddit page.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                1, 1, Gravity.BOTTOM | Gravity.START);
        root.addView(browser, params);
        final Handler main = new Handler(Looper.getMainLooper());
        final AtomicBoolean finished = new AtomicBoolean();
        final String[] failure = {"No canonical post URL was found"};
        final Runnable[] timeout = new Runnable[1];

        class Complete {
            void finish(String id, String detail) {
                if (!finished.compareAndSet(false, true)) return;
                if (timeout[0] != null) main.removeCallbacks(timeout[0]);
                browser.stopLoading();
                browser.setWebViewClient(null);
                root.removeView(browser);
                browser.destroy();
                callback.onResolved(id, detail);
            }
            void visit(String url) {
                if (url == null) return;
                if (!trusted(url)) {
                    failure[0] = "Redirect left trusted Reddit HTTPS hosts";
                    return;
                }
                String id = RedditGalleryLink.postId(url);
                if (id != null) finish(id, null);
            }
        }
        final Complete complete = new Complete();
        try {
            WebSettings settings = browser.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            browser.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView v,
                        WebResourceRequest req) {
                    if (req == null || req.getUrl() == null) return true;
                    String url = req.getUrl().toString();
                    if (!trusted(url)) {
                        failure[0] = "Untrusted redirect blocked";
                        return true;
                    }
                    complete.visit(url);
                    return finished.get(); // WebView handles trusted navigation
                }

                @Override public void onPageStarted(WebView v, String url,
                        android.graphics.Bitmap favicon) {
                    complete.visit(url);
                }
                @Override public void onPageFinished(WebView v, String url) {
                    complete.visit(url);
                }
                @Override public void onReceivedHttpError(WebView v,
                        WebResourceRequest request, WebResourceResponse response) {
                    if (request != null && request.isForMainFrame()
                            && response != null) {
                        failure[0] = "Browser HTTP " + response.getStatusCode() +
                                " at " + request.getUrl().getHost();
                    }
                }
                @Override public void onReceivedError(WebView v,
                        WebResourceRequest request, WebResourceError error) {
                    if (request != null && request.isForMainFrame()) {
                        failure[0] = "Browser navigation error " +
                                (error == null ? "unknown" : error.getErrorCode());
                    }
                }
            });
            timeout[0] = () -> complete.finish(null, failure[0] +
                    " (browser resolution timed out)");
            main.postDelayed(timeout[0], 17000);
            browser.loadUrl(link);
        } catch (RuntimeException e) {
            complete.finish(null, "Browser resolver crashed: " +
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static boolean trusted(String raw) {
        try {
            URL url = new URL(raw);
            if (!"https".equalsIgnoreCase(url.getProtocol())) return false;
            String h = url.getHost().toLowerCase(Locale.US);
            return h.equals("reddit.com") || h.endsWith(".reddit.com") ||
                    h.equals("redd.it");
        } catch (Exception ignored) {
            return false;
        }
    }
}
