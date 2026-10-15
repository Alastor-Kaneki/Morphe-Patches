package dev.alastorkaneki.morphe.extension.reddit;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

 

/**
 * Exact Reddit 2026.41.0 hook target:
 * com.reddit.feeds.ui.events.OnOverflowMenuOpened's first String argument is
 * named linkKindWithId (verified against the supplied original APK DEX).
 *
 * Unlike scanning Activity fragments/Compose widgets, this event belongs to
 * the exact post whose overflow menu the user just opened.
 */
public final class RedditNativeOverflowContext {
     private static final long LIFETIME_MS = 45_000L;
    private static volatile String lastPostId;
    private static volatile long lastCapturedAt;

    private RedditNativeOverflowContext() { }

    /** Safe to invoke from an injected constructor. No activity/UI work inline. */
    public static void onMenuOpened(String linkKindWithId) {
        if (linkKindWithId == null) return;
        String id = RedditNativePostId.fromMenuLinkKindWithId(linkKindWithId);
        if (id == null) return; // Ignore comments, subreddits and unknown event kinds.
        lastPostId = id;
        lastCapturedAt = SystemClock.elapsedRealtime();
        new Handler(Looper.getMainLooper()).post(() ->
            RedditGalleryOverlayController.showNativeContextAction(id));
    }

    public static String recentPostId() {
        String id = lastPostId;
        return id != null && SystemClock.elapsedRealtime() - lastCapturedAt < LIFETIME_MS
            ? id : null;
    }

    /** For UI lifecycle changes which should invalidate the captured post. */
    public static void clear() {
        lastPostId = null;
        lastCapturedAt = 0;
    }
}
