package dev.alastorkaneki.morphe.extension.reddit;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java URL extractor; never accepts arbitrary hostnames or image URLs. */
public final class RedditGalleryLink {
    private static final Pattern URL = Pattern.compile(
            "(?i)(?:https?://)?(?:(?:www|old|m)\\.)?reddit\\.com/" +
            "(?:gallery/|(?:r/[a-z0-9_]+/)?comments/)([a-z0-9]{5,12})(?=[/?#\\s]|$)");
    private static final Pattern SHORT = Pattern.compile(
            "(?i)(?:https?://)?(?:www\\.)?redd\\.it/([a-z0-9]{5,12})(?=[/?#\\s]|$)");

    private RedditGalleryLink() { }

    public static String postId(String text) {
        if (text == null) return null;
        // Disallow matching a reddit.com substring inside a different domain.
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            if (matcher.start() > 0 && isHostCharacter(text.charAt(matcher.start() - 1))) continue;
            return matcher.group(1).toLowerCase(Locale.US);
        }
        matcher = SHORT.matcher(text);
        while (matcher.find()) {
            if (matcher.start() > 0 && isHostCharacter(text.charAt(matcher.start() - 1))) continue;
            return matcher.group(1).toLowerCase(Locale.US);
        }
        return null;
    }

    private static boolean isHostCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_';
    }
}
