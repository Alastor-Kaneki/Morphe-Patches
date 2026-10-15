import dev.alastorkaneki.morphe.extension.reddit.RedditShareLink;

/** Pure JDK regression tests for Reddit's short native Share URL format. */
public final class RedditShareLinkSelfTest {
    private static void expect(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        String share = "https://www.reddit.com/r/pics/s/AbCdEf12?utm_source=share";
        expect(share.equals(RedditShareLink.find("Check this: " + share)), "Find canonical shared URL");
        expect("https://reddit.com/r/pics/s/ABCD".equals(
            RedditShareLink.find("https://reddit.com/r/pics/s/ABCD")), "Bare 4-letter share token");
        expect(RedditShareLink.find("https://m.reddit.com/r/test_sub/s/8p4eQx") != null,
                "m.reddit.com short share link");
        expect(RedditShareLink.find("https://reddit.com.evil.net/r/pics/s/AbCdEf12") == null,
                "Do not match spoofed Reddit domain");
        expect(RedditShareLink.find("https://evilreddit.com/r/pics/s/AbCdEf12") == null,
                "Do not match embedded Reddit hostname");
        expect(RedditShareLink.find("https://www.reddit.com/gallery/1ur2wv1") == null,
                "Direct galleries are not share-token links");
        expect(RedditShareLink.find("Unrelated text") == null, "No spurious token");
        expect("1ur2wv1".equals(RedditShareLink.resolve(
                "https://www.reddit.com/gallery/1ur2wv1")), "Direct gallery resolves without HTTP");
        System.out.println("PASS: 8 Reddit native Share shortlink regression cases");
    }
}
