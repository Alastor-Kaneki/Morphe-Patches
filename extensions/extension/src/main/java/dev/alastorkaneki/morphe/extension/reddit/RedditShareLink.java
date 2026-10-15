package dev.alastorkaneki.morphe.extension.reddit;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detect Reddit's /r/community/s/TOKEN native share links, then safely follow redirects. */
public final class RedditShareLink {
    private static final Pattern SHORT_SHARE = Pattern.compile(
        "(?i)(?:https?://)?(?:(?:www|m|old|sh)\\.)?reddit\\.com/" +
        "r/[a-z0-9_]+/s/[a-z0-9_-]{4,60}(?:[/?#][^\\s]*)?"
    );
    private static final String USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";

    private RedditShareLink() { }

    public static String find(String text) {
        if (text == null || text.length() > 4096) return null;
        Matcher matcher = SHORT_SHARE.matcher(text);
        while (matcher.find()) {
            if (matcher.start() > 0) {
                char prefix = text.charAt(matcher.start() - 1);
                if (Character.isLetterOrDigit(prefix) || prefix == '.' ||
                    prefix == '-' || prefix == '_') continue;
            }
            String raw = matcher.group();
            // Trim punctuation from the surrounding share message.
            raw = raw.replaceAll("[),.;\\]]+$", "");
            String candidate = raw.startsWith("http") ? raw : "https://" + raw;
            try {
                URL url = new URL(candidate);
                if (trustedHost(url.getHost()) && url.getPath().matches(
                        "(?i)^/r/[a-z0-9_]+/s/[a-z0-9_-]{4,60}/?$"))
                    return url.toExternalForm();
            } catch (Exception ignored) { }
        }
        return null;
    }

    public static String resolve(String text) throws IOException {
        String directId = RedditGalleryLink.postId(text);
        if (directId != null) return directId;
        String share = find(text);
        if (share == null) throw new IOException("Not a supported Reddit post or /s/ share link");

        URL current = new URL(share);
        for (int hop = 0; hop < 7; hop++) {
            if (!"https".equalsIgnoreCase(current.getProtocol()) ||
                    !trustedHost(current.getHost()))
                throw new IOException("Share link redirected outside Reddit; blocked");

            String found = RedditGalleryLink.postId(current.toExternalForm());
            if (found != null) return found;
            IOException last = null;
            URL next = null;
            for (String method : new String[]{"HEAD", "GET"}) {
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) current.openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(12000);
                    conn.setRequestMethod(method);
                    conn.setRequestProperty("User-Agent", USER_AGENT);
                    conn.setRequestProperty("Accept", "text/html,*/*");
                    int status = conn.getResponseCode();
                    String location = conn.getHeaderField("Location");
                    if (location != null && status >= 300 && status < 400) {
                        next = new URL(current, location);
                        break;
                    }
                    last = new IOException("Reddit short link returned HTTP " + status +
                        " without a usable redirect");
                } catch (IOException error) {
                    last = error;
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
            if (next == null) throw last != null ? last :
                new IOException("Reddit did not resolve the share token");
            if (!"https".equalsIgnoreCase(next.getProtocol()) ||
                    !trustedHost(next.getHost()))
                throw new IOException("Untrusted redirect host blocked");
            current = next;
        }
        throw new IOException("Reddit short link exceeded redirect safety limit");
    }

    private static boolean trustedHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.US);
        return "redd.it".equals(normalized) || "reddit.com".equals(normalized) ||
                normalized.endsWith(".reddit.com");
    }
}
