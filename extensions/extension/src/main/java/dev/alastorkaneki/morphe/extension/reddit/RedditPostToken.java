package dev.alastorkaneki.morphe.extension.reddit;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java, constrained parser for Reddit's native t3 fullname and post links. */
public final class RedditPostToken {
    private static final Pattern FULLNAME = Pattern.compile(
            "(?i)(?:^|[^a-z0-9_])t3_([a-z0-9]{5,12})(?=$|[^a-z0-9_])");
    private static final Pattern BARE = Pattern.compile("(?i)^[a-z0-9]{5,12}$");
    private RedditPostToken() { }

    /** IDs from a strongly named postId/linkId/thingId field, not arbitrary UI text. */
    public static String fromNamedField(String value) {
        if (value == null) return null;
        String candidate = value.trim();
        String urlId = RedditGalleryLink.postId(candidate);
        if (urlId != null) return urlId;
        Matcher match = FULLNAME.matcher(candidate);
        if (match.find()) return match.group(1).toLowerCase(Locale.US);
        if (BARE.matcher(candidate).matches()) return candidate.toLowerCase(Locale.US);
        return null;
    }

    /** Accept URLs or post fullnames, but *never* arbitrary plain alphanumeric strings. */
    public static String fromUntrustedText(String value) {
        String link = RedditGalleryLink.postId(value);
        if (link != null) return link;
        if (value == null || value.length() > 4096) return null;
        Matcher match = FULLNAME.matcher(value);
        return match.find() ? match.group(1).toLowerCase(Locale.US) : null;
    }

    public static boolean isPostKey(String key) {
        if (key == null) return false;
        String name = key.replace("_", "").replace("-", "").toLowerCase(Locale.US);
        switch (name) {
            case "postid": case "postid36": case "postfullname":
            case "linkid": case "linkfullname": case "thingid":
            case "redditpostid": case "targetpostid": case "submissionid":
                return true;
            default: return false;
        }
    }

    public static boolean isUrlKey(String key) {
        if (key == null) return false;
        String name = key.replace("_", "").replace("-", "").toLowerCase(Locale.US);
        switch (name) {
            case "permalink": case "posturl": case "shareurl":
            case "deeplink": case "deeplinkurl": case "url":
            case "postpermalink": case "canonicalurl": case "navigationurl":
                return true;
            default: return false;
        }
    }
}
