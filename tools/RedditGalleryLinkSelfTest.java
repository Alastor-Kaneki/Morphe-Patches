import dev.alastorkaneki.morphe.extension.reddit.RedditGalleryLink;

/** Standalone link parsing tests, runs with stock javac (no Android SDK). */
public final class RedditGalleryLinkSelfTest {
    private static void check(String actual, String expected) {
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    public static void main(String[] args) {
        check(RedditGalleryLink.postId("https://www.reddit.com/gallery/1ur2wv1"), "1ur2wv1");
        check(RedditGalleryLink.postId("https://reddit.com/r/pics/comments/1ur2wv1/title/?share_id=foo"), "1ur2wv1");
        check(RedditGalleryLink.postId("Look at https://old.reddit.com/comments/1ur2wv1/"), "1ur2wv1");
        check(RedditGalleryLink.postId("https://m.reddit.com/gallery/1UR2WV1?foo=bar"), "1ur2wv1");
        check(RedditGalleryLink.postId("https://redd.it/1ur2wv1"), "1ur2wv1");
        check(RedditGalleryLink.postId("https://reddit.com/gallery/1ur2wv1"), "1ur2wv1");
        check(RedditGalleryLink.postId("https://reddit.com/gallery/"), null);
        check(RedditGalleryLink.postId("https://reddit.com.attacker.invalid/gallery/1ur2wv1"), null);
        check(RedditGalleryLink.postId("https://preview.redd.it/hello.jpg"), null);
        check(RedditGalleryLink.postId("https://example.com/reddit"), null);
        System.out.println("PASS: Reddit gallery link extractor (10 cases)");
    }
}
