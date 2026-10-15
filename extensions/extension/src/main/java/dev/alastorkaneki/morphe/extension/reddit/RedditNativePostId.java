package dev.alastorkaneki.morphe.extension.reddit;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strictly parses Reddit's post kind/fullname from the native menu event. */
public final class RedditNativePostId {
    private static final Pattern FULLNAME =
        Pattern.compile("(?i)^t3_([a-z0-9]{5,12})$");
    private RedditNativePostId() { }

    public static String fromMenuLinkKindWithId(String value) {
        if (value == null) return null;
        Matcher match = FULLNAME.matcher(value.trim());
        return match.matches() ? match.group(1).toLowerCase(Locale.US) : null;
    }
}
