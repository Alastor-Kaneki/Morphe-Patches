import dev.alastorkaneki.morphe.extension.reddit.RedditPostToken;

/** Ensures native post IDs are parsed safely and arbitrary text is not mistaken for a post. */
public final class RedditNativePostSelfTest {
    private static void eq(String actual, String expected) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void yes(boolean actual) {
        if (!actual) throw new AssertionError("Expected true");
    }
    private static void no(boolean actual) {
        if (actual) throw new AssertionError("Expected false");
    }
    public static void main(String[] args) {
        eq(RedditPostToken.fromNamedField("t3_1ur2wv1"), "1ur2wv1");
        eq(RedditPostToken.fromNamedField("1UR2WV1"), "1ur2wv1");
        eq(RedditPostToken.fromNamedField("https://reddit.com/comments/1ur2wv1"), "1ur2wv1");
        eq(RedditPostToken.fromNamedField("https://redd.it/1ur2wv1"), "1ur2wv1");
        eq(RedditPostToken.fromNamedField("not a post"), null);
        eq(RedditPostToken.fromNamedField("t1_1ur2wv1"), null);
        eq(RedditPostToken.fromUntrustedText("Post id: t3_1ur2wv1"), "1ur2wv1");
        eq(RedditPostToken.fromUntrustedText("View: https://www.reddit.com/gallery/1ur2wv1"), "1ur2wv1");
        eq(RedditPostToken.fromUntrustedText("Just a title: 1ur2wv1"), null);
        eq(RedditPostToken.fromUntrustedText("https://notreddit.com/gallery/1ur2wv1"), null);
        yes(RedditPostToken.isPostKey("postId"));
        yes(RedditPostToken.isPostKey("link_id"));
        yes(RedditPostToken.isPostKey("thing_id"));
        yes(RedditPostToken.isPostKey("postFullname"));
        no(RedditPostToken.isPostKey("commentId"));
        no(RedditPostToken.isPostKey("session_token"));
        yes(RedditPostToken.isUrlKey("permalink"));
        yes(RedditPostToken.isUrlKey("post_url"));
        no(RedditPostToken.isUrlKey("avatar_url"));
        System.out.println("PASS: 19 native Reddit post detection cases");
    }
}
