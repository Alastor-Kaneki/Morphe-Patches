package dev.alastorkaneki.morphe.patches.mtmanager

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import dev.alastorkaneki.morphe.patches.mtmanager.Constants.MT_MANAGER
import org.w3c.dom.Element

/**
 * Marks the patched MT Manager APK as debuggable at the manifest level.
 *
 * This deliberately avoids touching MT Manager's obfuscated DEX. Android's
 * application-wide android:debuggable flag is sufficient for the platform to
 * classify the rebuilt package as debuggable.
 */
@Suppress("unused")
val enableDebuggablePatch = resourcePatch(
    name = "Enable debugging",
    description =
        "Sets android:debuggable=true on MT Manager so Android debugging tools can attach to the patched build.",
    default = false
) {
    compatibleWith(MT_MANAGER)

    execute {
        document("AndroidManifest.xml").use { document ->
            val application =
                document.getElementsByTagName("application").item(0) as? Element
                    ?: throw PatchException("MT Manager application manifest element was not found.")

            application.setAttribute("android:debuggable", "true")
        }
    }
}
