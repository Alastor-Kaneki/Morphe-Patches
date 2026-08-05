package dev.alastorkaneki.morphe.extension.chromeuserscripts;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Greasy Fork/Sleazy Fork browsing, installation resolution, and publish prefill support. */
final class ForkSiteSupport {
    static final String GREASY_HOST = "greasyfork.org";
    static final String SLEAZY_HOST = "sleazyfork.org";

    private static final String PENDING_HOST = "fork_publish_host";
    private static final String PENDING_PATH = "fork_publish_path";
    private static final String PENDING_NAME = "fork_publish_name";
    private static final String PENDING_FILE = "pending-fork-publish.user.js";

    private static final Pattern SCRIPT_ID = Pattern.compile("/scripts/(\\d+)(?:[-/]|$)");
    private static final Pattern INSTALL_LINK = Pattern.compile(
            "(?is)<a[^>]+(?:class=[\"'][^\"']*install-link[^\"']*[\"'][^>]+)?href=[\"']([^\"']+(?:\\.user\\.js|\\.user\\.css)(?:\\?[^\"']*)?)[\"']"
    );
    private static final Pattern ANY_SCRIPT_LINK = Pattern.compile(
            "(?is)href=[\"']([^\"']+(?:\\.user\\.js|\\.user\\.css)(?:\\?[^\"']*)?)[\"']"
    );
    private static final Map<Activity, String> LAST_INSTALL_PROMPT = new WeakHashMap<>();

    private ForkSiteSupport() { }

    static boolean isForkHost(String host) {
        String normalized = normalizeHost(host);
        return GREASY_HOST.equals(normalized) || SLEAZY_HOST.equals(normalized);
    }

    static boolean isDirectScript(String url) {
        if (url == null) return false;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String path = uri.getPath();
            return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && path != null
                    && (path.toLowerCase(Locale.US).endsWith(".user.js")
                    || path.toLowerCase(Locale.US).endsWith(".user.css"));
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isForkScriptPage(String url) {
        if (url == null) return false;
        try {
            URI uri = new URI(url);
            return isForkHost(uri.getHost()) && SCRIPT_ID.matcher(uri.getPath() == null ? "" : uri.getPath()).find();
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isInstallablePage(String url) {
        return isDirectScript(url) || isForkScriptPage(url);
    }

    static String resolveInstallUrl(String pageUrl) throws Exception {
        if (isDirectScript(pageUrl)) return pageUrl;
        if (!isForkScriptPage(pageUrl)) throw new Exception("This page is not a userscript page");

        String html = MonkeyStore.fetch(pageUrl);
        String href = firstMatch(INSTALL_LINK, html);
        if (href == null) href = firstMatch(ANY_SCRIPT_LINK, html);
        if (href == null) {
            Matcher id = SCRIPT_ID.matcher(new URI(pageUrl).getPath());
            if (!id.find()) throw new Exception("Could not find the script install URL");
            String host = normalizeHost(new URI(pageUrl).getHost());
            String updateHost = GREASY_HOST.equals(host) ? "update.greasyfork.org" : "update.sleazyfork.org";
            return "https://" + updateHost + "/scripts/" + id.group(1) + "/script.user.js";
        }

        href = htmlDecode(href);
        String resolved = new URL(new URL(pageUrl), href).toString();
        String host = normalizeHost(new URI(resolved).getHost());
        if (!isAllowedDownloadHost(host)) throw new Exception("Refused non-Fork script host: " + host);
        return resolved;
    }

    static void openSite(Activity activity, String host) {
        openUrl(activity, "https://" + normalizeHost(host) + "/en/scripts");
    }

    static void openUrl(Activity activity, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setPackage(activity.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    static void openInstallPreview(Activity activity, String pageUrl) {
        activity.startActivity(new Intent(activity, UserscriptInstallActivity.class)
                .putExtra("script_page_url", pageUrl));
    }

    static void maybePromptDirectInstall(Activity activity, String url) {
        if (!isDirectScript(url)) return;
        synchronized (LAST_INSTALL_PROMPT) {
            if (url.equals(LAST_INSTALL_PROMPT.get(activity))) return;
            LAST_INSTALL_PROMPT.put(activity, url);
        }
        openInstallPreview(activity, url);
    }

    static void queuePublish(Activity activity, Userscript script, String targetHost) throws Exception {
        String host = normalizeHost(targetHost);
        if (!isForkHost(host)) throw new Exception("Unsupported publishing site");
        if (script == null || script.source == null || script.source.trim().isEmpty()) {
            throw new Exception("The script has no source code");
        }
        if (Userscript.KIND_CSS.equals(script.kind)) {
            throw new Exception("Fork publishing currently accepts JavaScript userscripts; export CSS userstyles as files");
        }

        File directory = new File(activity.getFilesDir(), "monkeyscript");
        if (!directory.exists() && !directory.mkdirs()) throw new Exception("Cannot create publish staging directory");
        File pending = new File(directory, PENDING_FILE);
        try (FileOutputStream output = new FileOutputStream(pending)) {
            output.write(script.source.getBytes(StandardCharsets.UTF_8));
        }

        String path = "/en/script_versions/prefill";
        String existingId = scriptIdForHost(script.installUrl, host);
        if (existingId != null) path = "/en/scripts/" + existingId + "/versions/prefill";

        MonkeyStore.prefs(activity).edit()
                .putString(PENDING_HOST, host)
                .putString(PENDING_PATH, path)
                .putString(PENDING_NAME, script.name)
                .apply();
        openUrl(activity, "https://" + host + "/en/");
    }

    static boolean injectPendingPublish(Activity activity, ChromeBridge.Page page) {
        if (page == null || page.incognito || page.url == null) return false;
        SharedPreferences preferences = MonkeyStore.prefs(activity);
        String host = preferences.getString(PENDING_HOST, "");
        if (host.isEmpty()) return false;
        try {
            URI current = new URI(page.url);
            if (!host.equals(normalizeHost(current.getHost()))) return false;
            String path = preferences.getString(PENDING_PATH, "/en/script_versions/prefill");
            File pending = new File(new File(activity.getFilesDir(), "monkeyscript"), PENDING_FILE);
            if (!pending.isFile()) {
                clearPending(activity);
                return false;
            }
            String source;
            try (FileInputStream input = new FileInputStream(pending)) {
                source = MonkeyStore.read(input, 3 * 1024 * 1024);
            }
            String encoded = Base64.encodeToString(source.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            String action = "https://" + host + path;
            String payload = "(function(){if(window.__MonkeyForkPublish)return;window.__MonkeyForkPublish=true;"
                    + "const b='" + encoded + "';const x=Uint8Array.from(atob(b),c=>c.charCodeAt(0));"
                    + "const code=new TextDecoder().decode(x);const f=document.createElement('form');"
                    + "f.method='POST';f.enctype='multipart/form-data';f.action='" + action + "';"
                    + "const t=document.createElement('textarea');t.name='script_version[code]';t.value=code;"
                    + "f.appendChild(t);f.style.display='none';document.documentElement.appendChild(f);f.submit();})();";
            if (!ChromeBridge.exec(page, payload)) return false;
            clearPending(activity);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String pendingPublishName(Activity activity) {
        return MonkeyStore.prefs(activity).getString(PENDING_NAME, "");
    }

    static void clearPending(Activity activity) {
        MonkeyStore.prefs(activity).edit()
                .remove(PENDING_HOST)
                .remove(PENDING_PATH)
                .remove(PENDING_NAME)
                .apply();
        File pending = new File(new File(activity.getFilesDir(), "monkeyscript"), PENDING_FILE);
        if (pending.isFile()) pending.delete();
    }

    private static String scriptIdForHost(String installUrl, String targetHost) {
        if (installUrl == null || installUrl.isEmpty()) return null;
        try {
            URI uri = new URI(installUrl);
            String host = normalizeHost(uri.getHost());
            boolean matchingFamily = targetHost.equals(host)
                    || (GREASY_HOST.equals(targetHost) && "update.greasyfork.org".equals(host))
                    || (SLEAZY_HOST.equals(targetHost) && "update.sleazyfork.org".equals(host));
            if (!matchingFamily) return null;
            Matcher matcher = SCRIPT_ID.matcher(uri.getPath() == null ? "" : uri.getPath());
            return matcher.find() ? matcher.group(1) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isAllowedDownloadHost(String host) {
        return isForkHost(host)
                || "update.greasyfork.org".equals(host)
                || "update.sleazyfork.org".equals(host);
    }

    private static String firstMatch(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String htmlDecode(String value) {
        return value.replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&quot;", "\"");
    }

    private static String normalizeHost(String host) {
        if (host == null) return "";
        String normalized = host.toLowerCase(Locale.US).trim();
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }
}
