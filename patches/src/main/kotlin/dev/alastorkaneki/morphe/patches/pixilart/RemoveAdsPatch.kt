package dev.alastorkaneki.morphe.patches.pixilart

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import dev.alastorkaneki.morphe.patches.pixilart.Constants.PIXILART

private const val APP_BUNDLE = "assets/www/static/js/app.js"

private val ADMOB_PROVIDER_CHECK = Regex(
    """var\s+isAvailable\s*=\s*function\s+isAvailable\(\)\s*\{\s*return\s+typeof\s+admob\s*!==\s*['\"]undefined['\"]\s*;\s*\}\s*;"""
)

private val NATIVE_ADS_DISABLED_STATE = Regex(
    """disableAds\s*:\s*false"""
)

private val SHOW_BANNER_ACTION = Regex(
    """showBanner\s*:\s*function\s+showBanner\s*\(\s*context\s*\)\s*\{"""
)

private val SHOW_INTERSTITIAL_ACTION = Regex(
    """showInterstitial\s*:\s*function\s+showInterstitial\s*\(\s*context\s*\)\s*\{"""
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
 * Native advertising is blocked at multiple layers on purpose:
 * 1. The AdMob provider reports itself unavailable.
 * 2. Pixilart's ad state starts with disableAds enabled.
 * 3. Banner and interstitial display actions hard-return before reaching the SDK.
 *
 * Web script ads and server-fed promoted/feed cards are also removed independently.
 */
@Suppress("unused")
val removeAdsPatch = resourcePatch(
    name = "Remove ads",
    description =
        "Hard-disables Pixilart native banner/interstitial ads, injected web ads, and promoted/feed ad cards.",
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
            pattern = NATIVE_ADS_DISABLED_STATE,
            replacement = "disableAds: true",
            label = "native-ad disable state"
        )

        source = replaceRegexExactlyOnce(
            source = source,
            pattern = SHOW_BANNER_ACTION,
            replacement = "showBanner: function showBanner(context) { return;",
            label = "banner display action"
        )

        source = replaceRegexExactlyOnce(
            source = source,
            pattern = SHOW_INTERSTITIAL_ACTION,
            replacement = "showInterstitial: function showInterstitial(context) { return;",
            label = "interstitial display action"
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
