package dev.alastorkaneki.morphe.patches.chrome

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import dev.alastorkaneki.morphe.patches.chrome.Constants.CHROME
import org.w3c.dom.Element

private const val INIT_PROVIDER =
    "dev.alastorkaneki.morphe.extension.chromeuserscripts.ChromeUserscriptInitProvider"
private const val MANAGER_ACTIVITY =
    "dev.alastorkaneki.morphe.extension.chromeuserscripts.UserscriptManagerActivity"
private const val EDITOR_ACTIVITY =
    "dev.alastorkaneki.morphe.extension.chromeuserscripts.UserscriptEditorActivity"
private const val PROVIDER_AUTHORITY =
    "com.android.chrome.dev.alastorkaneki.monkeyscript.init"

@Suppress("unused")
internal val addChromeUserscriptManifestPatch = resourcePatch(
    description = "Registers the MonkeyScript userscript engine and native manager."
) {
    compatibleWith(CHROME)

    execute {
        document("AndroidManifest.xml").use { document ->
            val application =
                document.getElementsByTagName("application").item(0) as Element

            val providers = application.getElementsByTagName("provider")
            val providerAlreadyPresent = (0 until providers.length)
                .map { providers.item(it) as Element }
                .any { it.getAttribute("android:name") == INIT_PROVIDER }

            if (!providerAlreadyPresent) {
                application.appendChild(document.createElement("provider").apply {
                    setAttribute("android:name", INIT_PROVIDER)
                    setAttribute("android:authorities", PROVIDER_AUTHORITY)
                    setAttribute("android:exported", "false")
                    setAttribute("android:initOrder", "1999999996")
                })
            }

            val activities = application.getElementsByTagName("activity")
            val existingNames = (0 until activities.length)
                .map { (activities.item(it) as Element).getAttribute("android:name") }
                .toSet()

            if (MANAGER_ACTIVITY !in existingNames) {
                application.appendChild(document.createElement("activity").apply {
                    setAttribute("android:name", MANAGER_ACTIVITY)
                    setAttribute("android:exported", "false")
                    setAttribute("android:excludeFromRecents", "true")
                    setAttribute("android:label", "MonkeyScript")
                    setAttribute("android:theme", "@android:style/Theme.Material.NoActionBar")
                    setAttribute("android:windowSoftInputMode", "adjustResize")
                })
            }

            if (EDITOR_ACTIVITY !in existingNames) {
                application.appendChild(document.createElement("activity").apply {
                    setAttribute("android:name", EDITOR_ACTIVITY)
                    setAttribute("android:exported", "false")
                    setAttribute("android:excludeFromRecents", "true")
                    setAttribute("android:label", "MonkeyScript Editor")
                    setAttribute("android:theme", "@android:style/Theme.Material.NoActionBar")
                    setAttribute("android:windowSoftInputMode", "adjustResize")
                })
            }
        }
    }
}

@Suppress("unused")
val chromeUserscriptManagerPatch = bytecodePatch(
    name = "MonkeyScript userscript manager",
    description =
        "Adds a Tampermonkey/Violentmonkey/FireMonkey/Greasemonkey-inspired userscript and userstyle manager to Chrome Android.",
    default = true
) {
    compatibleWith(CHROME)
    dependsOn(addChromeUserscriptManifestPatch)
    extendWith("extensions/extension.mpe")

    // Chrome's Java API changes frequently, so the injected engine locates the active
    // Chromium Tab/WebContents at runtime instead of fingerprinting one Chrome build.
    execute { }
}
