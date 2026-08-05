import dev.alastorkaneki.morphe.extension.chromeuserscripts.ScriptInjector;
import dev.alastorkaneki.morphe.extension.chromeuserscripts.UrlPatternMatcher;
import dev.alastorkaneki.morphe.extension.chromeuserscripts.Userscript;
import dev.alastorkaneki.morphe.extension.chromeuserscripts.UserscriptMetadataParser;

public final class ChromeUserscriptSelfTest {
    public static void main(String[] args) {
        String source =
                "// ==UserScript==\n" +
                "// @name Example Script\n" +
                "// @namespace dev.alastor\n" +
                "// @version 2.4.1\n" +
                "// @match https://*.example.com/*\n" +
                "// @exclude https://private.example.com/*\n" +
                "// @grant GM_getValue\n" +
                "// @grant GM_setValue\n" +
                "// @run-at document-idle\n" +
                "// ==/UserScript==\n" +
                "document.body.dataset.monkey = 'yes';";

        Userscript script = UserscriptMetadataParser.parse(
                source,
                "example.user.js",
                "https://example.com/example.user.js"
        );
        assertEquals("Example Script", script.name);
        assertEquals("2.4.1", script.version);
        assertEquals("document-idle", script.runAt);
        assertTrue(script.enabled);
        assertTrue(UrlPatternMatcher.matches(script, "https://sub.example.com/page"));
        assertFalse(UrlPatternMatcher.matches(script, "https://private.example.com/page"));
        assertFalse(UrlPatternMatcher.matches(script, "chrome://settings"));

        String payload = ScriptInjector.buildPayload(script, "https://sub.example.com/page", false);
        assertContains(payload, "GM_getValue");
        assertContains(payload, "GM_registerMenuCommand");
        assertContains(payload, "document.body.dataset.monkey");
        assertContains(payload, "document-idle");

        String styleSource =
                "/* ==UserStyle==\n" +
                " * @name Example Style\n" +
                " * @match https://example.org/*\n" +
                " ==/UserStyle== */\n" +
                "body { background: #000; }";
        Userscript style = UserscriptMetadataParser.parse(
                styleSource,
                "example.user.css",
                ""
        );
        assertEquals(Userscript.KIND_CSS, style.kind);
        assertTrue(UrlPatternMatcher.matches(style, "https://example.org/"));
        assertContains(ScriptInjector.buildPayload(style, "https://example.org/", false), "monkey-style");

        Userscript raw = UserscriptMetadataParser.parse("console.log('raw');", "raw.js", "");
        assertFalse(raw.enabled);

        System.out.println("Chrome userscript parser/matcher/injector self-test passed.");
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + "\nActual: " + actual);
        }
    }

    private static void assertContains(String value, String expected) {
        if (!value.contains(expected)) {
            throw new AssertionError("Expected payload to contain: " + expected);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }
}
