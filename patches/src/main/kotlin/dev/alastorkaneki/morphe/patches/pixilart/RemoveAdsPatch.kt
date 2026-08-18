package dev.alastorkaneki.morphe.patches.pixilart

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import dev.alastorkaneki.morphe.patches.pixilart.Constants.PIXILART

private const val APP_BUNDLE = "assets/www/static/js/app.js"

private val ADMOB_PROVIDER_CHECK = """
    var isAvailable = function isAvailable() {
        return typeof admob !== 'undefined';
      };
""".trimIndent()

private const val WEB_AD_SCRIPT_ENTRY =
    "appendScripts: function() {\n\t\tif(this.ads.loaded) return;"

private const val WEB_AD_SCRIPT_DISABLED =
    "appendScripts: function() {\n\t\treturn;\n\t\tif(this.ads.loaded) return;"

private const val FEED_AD_BRANCH =
    "(_vm.activity.type == 'ad')?_c('div',[_c('ad',{attrs:{\"ad\":_vm.activity}})],1):"

private const val FEED_AD_BRANCH_DISABLED =
    "(_vm.activity.type == 'ad')?_vm._e():"

private fun replaceExactlyOnce(
    source: String,
    needle: String,
    replacement: String,
    label: String
): String {
    val first = source.indexOf(needle)
    if (first < 0) {
        throw PatchException(
            "Pixilart $label hook was not found. This APK likely differs from the verified 1.9.0 build."
        )
    }

    val second = source.indexOf(needle, first + needle.length)
    if (second >= 0) {
        throw PatchException(
            "Expected exactly one Pixilart $label hook, but multiple matches were found."
        )
    }

    return source.replaceRange(first, first + needle.length, replacement)
}

/**
 * Removes all verified advertising paths from Pixilart 1.9.0.
 *
 * The app has three independent ad surfaces:
 * 1. Cordova AdMob banner/interstitial/native ads.
 * 2. Web ads injected by the bundled drawing UI (Freestar, NitroPay, Playwire and AdSense).
 * 3. Server-fed activity items with type == "ad", including promoted cards.
 *
 * This patch disables all three without changing Pro or ad-free account entitlements.
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

        source = replaceExactlyOnce(
            source = source,
            needle = ADMOB_PROVIDER_CHECK,
            replacement = """
                var isAvailable = function isAvailable() {
                    return false;
                  };
            """.trimIndent(),
            label = "AdMob provider"
        )

        source = replaceExactlyOnce(
            source = source,
            needle = WEB_AD_SCRIPT_ENTRY,
            replacement = WEB_AD_SCRIPT_DISABLED,
            label = "web-ad script loader"
        )

        source = replaceExactlyOnce(
            source = source,
            needle = FEED_AD_BRANCH,
            replacement = FEED_AD_BRANCH_DISABLED,
            label = "feed-ad renderer"
        )

        appBundle.writeText(source)
    }
}
