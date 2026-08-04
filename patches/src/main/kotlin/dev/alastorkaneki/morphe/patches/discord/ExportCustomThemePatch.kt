package dev.alastorkaneki.morphe.patches.discord

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import dev.alastorkaneki.morphe.patches.discord.Constants.DISCORD
import org.w3c.dom.Element

private const val INIT_PROVIDER =
    "dev.alastorkaneki.morphe.extension.discordtheme.DiscordThemeExportInitProvider"
private const val PROVIDER_AUTHORITY =
    "com.discord.dev.alastorkaneki.themeexport.init"

@Suppress("unused")
internal val addDiscordThemeExporterManifestPatch = resourcePatch(
    description = "Registers the Discord custom-theme export initializer."
) {
    compatibleWith(DISCORD)

    execute {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement
            val application =
                document.getElementsByTagName("application").item(0) as Element

            val permissionAlreadyPresent =
                (0 until document.getElementsByTagName("uses-permission").length)
                    .map { document.getElementsByTagName("uses-permission").item(it) as Element }
                    .any {
                        it.getAttribute("android:name") ==
                            "android.permission.WRITE_EXTERNAL_STORAGE"
                    }

            if (!permissionAlreadyPresent) {
                document.createElement("uses-permission").also { permission ->
                    permission.setAttribute(
                        "android:name",
                        "android.permission.WRITE_EXTERNAL_STORAGE"
                    )
                    permission.setAttribute("android:maxSdkVersion", "28")
                    manifest.insertBefore(permission, application)
                }
            }

            val providerAlreadyPresent =
                (0 until application.getElementsByTagName("provider").length)
                    .map { application.getElementsByTagName("provider").item(it) as Element }
                    .any { it.getAttribute("android:name") == INIT_PROVIDER }

            if (!providerAlreadyPresent) {
                application.appendChild(document.createElement("provider").apply {
                    setAttribute("android:name", INIT_PROVIDER)
                    setAttribute("android:authorities", PROVIDER_AUTHORITY)
                    setAttribute("android:exported", "false")
                    setAttribute("android:initOrder", "1999999999")
                })
            }
        }
    }
}

@Suppress("unused")
val exportDiscordCustomThemePatch = bytecodePatch(
    name = "Export Nitro custom theme",
    description =
        "Adds an Export Theme button in Discord's theme screens and saves the active custom theme as CSS plus a clean PNG preview.",
    default = true
) {
    compatibleWith(DISCORD)
    dependsOn(addDiscordThemeExporterManifestPatch)
    extendWith("extensions/extension.mpe")

    // The implementation is injected as a self-contained Android lifecycle overlay.
    // It does not unlock Nitro, modify billing state, or hook Discord account APIs.
    execute { }
}
