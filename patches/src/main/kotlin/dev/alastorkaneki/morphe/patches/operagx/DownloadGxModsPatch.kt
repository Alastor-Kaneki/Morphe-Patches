package dev.alastorkaneki.morphe.patches.operagx

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import dev.alastorkaneki.morphe.patches.operagx.Constants.OPERA_GX
import org.w3c.dom.Element

private const val DOWNLOADER_ACTIVITY =
    "dev.alastorkaneki.morphe.extension.operagx.GxModDownloadActivity"

/**
 * Adds a dedicated Android share target to the patched Opera GX manifest.
 *
 * User flow: open a GX Store mod page -> Share -> Download GX Mod.
 */
@Suppress("unused")
internal val addGxModDownloaderManifestPatch = resourcePatch(
    description = "Registers the GX mod-file downloader share target."
) {
    compatibleWith(OPERA_GX)

    execute {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement
            val application =
                document.getElementsByTagName("application").item(0) as Element

            // Required only on Android 9 and older. Harmless on newer builds because
            // maxSdkVersion prevents it from being requested there.
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

            val activityAlreadyPresent =
                (0 until application.getElementsByTagName("activity").length)
                    .map { application.getElementsByTagName("activity").item(it) as Element }
                    .any { it.getAttribute("android:name") == DOWNLOADER_ACTIVITY }

            if (!activityAlreadyPresent) {
                val activity = document.createElement("activity").apply {
                    setAttribute("android:name", DOWNLOADER_ACTIVITY)
                    setAttribute("android:exported", "true")
                    setAttribute("android:excludeFromRecents", "true")
                    setAttribute("android:noHistory", "true")
                    setAttribute("android:label", "Download GX Mod")
                    setAttribute("android:theme", "@android:style/Theme.Translucent.NoTitleBar")
                }

                val intentFilter = document.createElement("intent-filter").apply {
                    appendChild(document.createElement("action").apply {
                        setAttribute("android:name", "android.intent.action.SEND")
                    })
                    appendChild(document.createElement("category").apply {
                        setAttribute("android:name", "android.intent.category.DEFAULT")
                    })
                    appendChild(document.createElement("data").apply {
                        setAttribute("android:mimeType", "text/plain")
                    })
                }

                activity.appendChild(intentFilter)
                application.appendChild(activity)
            }
        }
    }
}

@Suppress("unused")
val downloadGxModsAsFilesPatch = bytecodePatch(
    name = "Download GX mods as files",
    description =
        "Adds a Share target that resolves a GX Store page to its official mod.crx and saves it in Downloads.",
    default = true
) {
    compatibleWith(OPERA_GX)
    dependsOn(addGxModDownloaderManifestPatch)
    extendWith("extensions/extension.mpe")

    // No Opera method is modified. The injected Activity is intentionally
    // self-contained to avoid brittle fingerprints against obfuscated builds.
    execute { }
}
