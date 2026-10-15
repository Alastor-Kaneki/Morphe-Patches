import dev.alastorkaneki.morphe.extension.reddit.RedditNativePostId;

public final class RedditNativeOverflow202641SelfTest {
    private static void assertId(String expected, String input) {
        String actual = RedditNativePostId.fromMenuLinkKindWithId(input);
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError("Input [" + input + "] expected [" + expected + "] got [" + actual + "]");
    }
    public static void main(String[] args) {
        assertId("1ur2wv1", "t3_1ur2wv1");
        assertId("1ur2wv1", "T3_1UR2WV1");
        assertId("1ur2wv1", " t3_1ur2wv1 ");
        assertId(null, "t1_1ur2wv1"); // Comment is not a post.
        assertId(null, "t5_1ur2wv1"); // Subreddit is not a post.
        assertId(null, "1ur2wv1"); // Require actual post event fullname.
        assertId(null, "t3_../../etc");
        assertId(null, "t3_1ur2wv1/trailing");
        assertId(null, null);
        System.out.println("PASS: 9 real Reddit t3 overflow-event post-ID checks");
    }
}
