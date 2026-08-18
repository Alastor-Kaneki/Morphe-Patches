package dev.alastorkaneki.morphe.patches.pixilart

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import dev.alastorkaneki.morphe.patches.pixilart.Constants.PIXILART

private const val APP_BUNDLE = "assets/www/static/js/app.js"

private val ADMOB_PROVIDER_CHECK = Regex(
    """var\s+isAvailable\s*=\s*function\s+isAvailable\(\)\s*\{\s*return\s+typeof\s+admob\s*!==\s*['\"]undefined['\"]\s*;\s*\}\s*;"""
)

private val WEB_AD_SCRIPT_ENTRY = Regex(
    """appendScripts\s*:\s*function\s*\(\)\s*\{\s*if\s*\(\s*this\.ads\.loaded\s*\)\s*return\s*;"""
)

private val FEED_AD_BRANCH = Regex(
    """\(_vm\.activity\.type\s*==\s*['\"]ad['\"]\)\s*\?\s*_c\(\s*['\"]div['\"]\s*,\s*\[_c\(\s*['\"]ad['\"]\s*,\s*\{attrs\s*:\s*\{['\"]ad['\"]\s*:\s*_vm\.activity\}\}\)\]\s*,\s*1\s*\)\s*:"""
)

private fun replaceRegexExactlyOnce(
    source: String,
    pattern: Regex,
    replacement: String,
    label: String
): String {
    val matches = pattern.findAll(source).toList()
    if (matches.isEmpty()) {
        throw PatchException(
            "Pixilart $label hook was not found. This APK may have been modified already or differs from the verified 1.9.0 build."
        )
    }

    if (matches.size != 1) {
        throw PatchException(
            "Expected exactly one Pixilart $label hook, but found ${matches.size}."
        )
    }

    val match = matches.single()
    return source.replaceRange(match.range, replacement)
}

/**
 * Removes all verified advertising paths from Pixilart 1.9.0.
 *
 * The app has three independent ad surfaces:
 * 1. Cordova AdMob banner/interstitial/native ads.
 * 2. Web ads injected by the bundled drawing UI (Freestar, NitroPay, Playwire and AdSense).
 * 3. Server-fed activity items with type == "ad", including promoted cards.
 *
 * Whitespace-tolerant anchors are used because Morphe/resource processing can normalize
 * formatting in bundled JavaScript without changing the actual code.
 */
@Suppress("unused")
val removeAdsPatch = resourcePatch(
    name = "Remove ads",
    description =
        "Removes Pixilart's native AdMob ads, injected web ads, and promoted/feed ad cards.",
    default = true
) {
    compatibleWith(PIXILART)

    execute {
        val appBundle = get(APP_BUNDLE)
        if (!appBundle.isFile) {
            throw PatchException("Pixilart app bundle was not found at $APP_BUNDLE.")
        }

        var source = appBundle.readText()

        source = replaceRegexExactlyOnce(
            source = source,
            pattern = ADMOB_PROVIDER_CHECK,
            replacement = """
                var isAvailable = function isAvailable() {
                    return false;
                  };
            """.trimIndent(),
            label = "AdMob provider"
        )

        source = replaceRegexExactlyOnce(
            source = source,
            pattern = WEB_AD_SCRIPT_ENTRY,
            replacement = "appendScripts: function() { return;",
            label = "web-ad script loader"
        )

        source = replaceRegexExactlyOnce(
            source = source,
            pattern = FEED_AD_BRANCH,
            replacement = "(_vm.activity.type == 'ad')?_vm._e():",
            label = "feed-ad renderer"
        )

        appBundle.writeText(source)
    }
}
