package dev.alastorkaneki.morphe.patches.discord

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import dev.alastorkaneki.morphe.patches.discord.Constants.DISCORD
import org.w3c.dom.Element

private const val LEGACY_NAV_PROVIDER =
    "dev.alastorkaneki.morphe.extension.discordnavigation.DiscordLegacyNavigationInitProvider"
private const val LEGACY_NAV_AUTHORITY =
    "com.discord.dev.alastorkaneki.legacynavigation.init"

@Suppress("unused")
internal val addDiscordLegacyNavigationManifestPatch = resourcePatch(
    description = "Registers the controller that replaces Discord's You Bar."
) {
    compatibleWith(DISCORD)

    execute {
        document("AndroidManifest.xml").use { document ->
            val application =
                document.getElementsByTagName("application").item(0) as Element
            val alreadyPresent =
                (0 until application.getElementsByTagName("provider").length)
                    .map { application.getElementsByTagName("provider").item(it) as Element }
                    .any { it.getAttribute("android:name") == LEGACY_NAV_PROVIDER }

            if (!alreadyPresent) {
                application.appendChild(document.createElement("provider").apply {
                    setAttribute("android:name", LEGACY_NAV_PROVIDER)
                    setAttribute("android:authorities", LEGACY_NAV_AUTHORITY)
                    setAttribute("android:exported", "false")
                    setAttribute("android:initOrder", "1999999997")
                })
            }
        }
    }
}

@Suppress("unused")
val restorePreviousDiscordNavigationPatch = bytecodePatch(
    name = "Restore previous Discord navigation",
    description =
        "Replaces the 2026 You Bar with classic Servers, Messages, Notifications, and You navigation controls.",
    default = true
) {
    compatibleWith(DISCORD)
    dependsOn(addDiscordLegacyNavigationManifestPatch)
    extendWith("extensions/extension.mpe")

    // The controller delegates to Discord's own visible navigation destinations.
    // No account, network, billing, or message APIs are changed.
    execute { }
}
