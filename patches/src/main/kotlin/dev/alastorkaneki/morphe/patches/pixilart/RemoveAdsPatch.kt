package dev.alastorkaneki.morphe.patches.pixilart

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import dev.alastorkaneki.morphe.patches.pixilart.Constants.PIXILART

private const val APP_BUNDLE = "assets/www/static/js/app.js"

private val adProviderAvailability = Regex(
    """var isAvailable = function isAvailable\(\) \{\s*return typeof admob !== 'undefined';\s*\};"""
)

/**
 * Disables Pixilart's shared AdMob provider.
 *
 * Pixilart 1.9.0 routes its banner, interstitial, and native ads through this provider.
 * Making the provider unavailable prevents those paths from starting, loading, or showing ads
 * without modifying account entitlement flags such as Pro or ad-free status.
 */
@Suppress("unused")
val removeAdsPatch = resourcePatch(
    name = "Remove ads",
    description =
        "Disables Pixilart's shared AdMob provider so banner, interstitial, and native ads do not load or display.",
    default = true
) {
    compatibleWith(PIXILART)

    execute {
        val appBundle = get(APP_BUNDLE)
        if (!appBundle.isFile) {
            throw PatchException("Pixilart app bundle was not found at $APP_BUNDLE.")
        }

        val source = appBundle.readText()
        val matches = adProviderAvailability.findAll(source).toList()

        if (matches.size != 1) {
            throw PatchException(
                "Expected exactly one Pixilart AdMob provider availability check, found ${matches.size}. " +
                    "This APK likely differs from the verified Pixilart 1.9.0 build."
            )
        }

        val match = matches.single()
        val replacement = """
            var isAvailable = function isAvailable() {
                return false;
              };
        """.trimIndent()

        appBundle.writeText(source.replaceRange(match.range, replacement))
    }
}
