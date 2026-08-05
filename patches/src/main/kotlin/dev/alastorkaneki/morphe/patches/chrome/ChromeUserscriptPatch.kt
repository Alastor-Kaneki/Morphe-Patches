package dev.alastorkaneki.morphe.patches.chrome

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import dev.alastorkaneki.morphe.patches.chrome.Constants.CHROME
import org.w3c.dom.Element
import java.util.Locale

private const val ORIGINAL_PACKAGE = "com.android.chrome"
private const val INIT_PROVIDER =
    "dev.alastorkaneki.morphe.extension.chromeuserscripts.ChromeUserscriptInitProvider"
private const val MANAGER_ACTIVITY =
    "dev.alastorkaneki.morphe.extension.chromeuserscripts.UserscriptManagerActivity"
private const val EDITOR_ACTIVITY =
    "dev.alastorkaneki.morphe.extension.chromeuserscripts.UserscriptEditorActivity"
private const val PROVIDER_AUTHORITY =
    "com.android.chrome.dev.alastorkaneki.monkeyscript.init"

private val componentTags = setOf(
    "application",
    "activity",
    "activity-alias",
    "service",
    "receiver",
    "provider",
    "instrumentation"
)

private val packageScopedAttributes = listOf(
    "android:permission",
    "android:readPermission",
    "android:writePermission",
    "android:process",
    "android:taskAffinity",
    "android:targetPackage"
)

private val extraComponentAttributes = listOf(
    "android:backupAgent",
    "android:appComponentFactory",
    "android:manageSpaceActivity",
    "android:parentActivityName",
    "android:targetActivity",
    "android:zygotePreloadName"
)

private fun qualifyComponentName(name: String, originalPackage: String): String = when {
    name.startsWith('.') -> originalPackage + name
    '.' !in name -> "$originalPackage.$name"
    else -> name
}

private fun rewritePackageScopedValue(value: String, replacementPackage: String): String = when {
    value == ORIGINAL_PACKAGE -> replacementPackage
    value.startsWith("$ORIGINAL_PACKAGE.") ->
        replacementPackage + value.removePrefix(ORIGINAL_PACKAGE)
    value == "\${applicationId}" -> replacementPackage
    value.startsWith("\${applicationId}.") ->
        replacementPackage + value.removePrefix("\${applicationId}")
    else -> value
}

private fun rewriteAuthority(value: String, replacementPackage: String): String {
    val authority = value.trim()
    if (authority.isEmpty()) return authority
    return when {
        authority.startsWith("@string/") -> authority
        authority == ORIGINAL_PACKAGE || authority.startsWith("$ORIGINAL_PACKAGE.") ->
            replacementPackage + authority.removePrefix(ORIGINAL_PACKAGE)
        authority.contains("\${applicationId}") ->
            authority.replace("\${applicationId}", replacementPackage)
        authority == replacementPackage || authority.startsWith("$replacementPackage.") ->
            authority
        else -> "$replacementPackage.$authority"
    }
}

@Suppress("unused")
internal val addChromeUserscriptManifestPatch = resourcePatch(
    description =
        "Registers MonkeyScript and adds patch-time Chrome app-name and package-ID cloning options."
) {
    compatibleWith(CHROME)

    val customAppName by stringOption(
        key = "chromeMonkeyScriptAppName",
        default = "Chrome MonkeyScript",
        title = "App name",
        description = "Launcher and Android system name for the patched Chrome build.",
        required = true
    ) {
        !it.isNullOrBlank() && it.trim().length <= 80
    }

    val customPackageName by stringOption(
        key = "chromeMonkeyScriptPackageName",
        default = "com.android.chrome.morphe",
        title = "Package name",
        description =
            "Android package ID for side-by-side installation. It must differ from com.android.chrome.",
        required = true
    ) {
        !it.isNullOrBlank() &&
            it != ORIGINAL_PACKAGE &&
            it.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))
    }

    execute {
        val appName = requireNotNull(customAppName).trim()

        document("AndroidManifest.xml").use { document ->
            val application =
                document.getElementsByTagName("application").item(0) as Element

            application.setAttribute("android:label", appName)

            listOf("activity", "activity-alias").forEach { tag ->
                val nodes = application.getElementsByTagName(tag)
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as Element
                    val filters = element.getElementsByTagName("intent-filter")
                    var launcher = false
                    for (filterIndex in 0 until filters.length) {
                        val filter = filters.item(filterIndex) as Element
                        val actions = filter.getElementsByTagName("action")
                        val categories = filter.getElementsByTagName("category")
                        val hasMain = (0 until actions.length).any {
                            (actions.item(it) as Element).getAttribute("android:name") ==
                                "android.intent.action.MAIN"
                        }
                        val hasLauncher = (0 until categories.length).any {
                            (categories.item(it) as Element).getAttribute("android:name") ==
                                "android.intent.category.LAUNCHER"
                        }
                        if (hasMain && hasLauncher) {
                            launcher = true
                            break
                        }
                    }
                    if (launcher) element.setAttribute("android:label", appName)
                }
            }

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

    finalize {
        val replacementPackage = requireNotNull(customPackageName)
            .trim()
            .lowercase(Locale.US)
        val authorityStringResources = mutableSetOf<String>()

        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement
            val allElements = document.getElementsByTagName("*")

            listOf(
                "android:sharedUserId",
                "android:sharedUserLabel",
                "android:sharedUserMaxSdkVersion"
            ).forEach(manifest::removeAttribute)

            for (index in 0 until allElements.length) {
                val element = allElements.item(index) as Element

                if (element.tagName in componentTags &&
                    element.hasAttribute("android:name")) {
                    val name = element.getAttribute("android:name")
                    if (name.isNotBlank()) {
                        element.setAttribute(
                            "android:name",
                            qualifyComponentName(name, ORIGINAL_PACKAGE)
                        )
                    }
                }

                extraComponentAttributes.forEach { attribute ->
                    if (!element.hasAttribute(attribute)) return@forEach
                    val name = element.getAttribute(attribute)
                    if (name.isNotBlank()) {
                        element.setAttribute(
                            attribute,
                            qualifyComponentName(name, ORIGINAL_PACKAGE)
                        )
                    }
                }

                packageScopedAttributes.forEach { attribute ->
                    if (!element.hasAttribute(attribute)) return@forEach
                    val value = element.getAttribute(attribute)
                    element.setAttribute(
                        attribute,
                        rewritePackageScopedValue(value, replacementPackage)
                    )
                }

                if (element.tagName in setOf(
                        "permission",
                        "permission-group",
                        "permission-tree",
                        "uses-permission",
                        "uses-permission-sdk-23"
                    ) && element.hasAttribute("android:name")) {
                    val value = element.getAttribute("android:name")
                    element.setAttribute(
                        "android:name",
                        rewritePackageScopedValue(value, replacementPackage)
                    )
                }

                if (element.hasAttribute("android:authorities")) {
                    val rewritten = element.getAttribute("android:authorities")
                        .split(';')
                        .map { authority ->
                            if (authority.startsWith("@string/")) {
                                authorityStringResources += authority.removePrefix("@string/")
                            }
                            rewriteAuthority(authority, replacementPackage)
                        }
                    element.setAttribute("android:authorities", rewritten.joinToString(";"))
                }
            }

            manifest.setAttribute("package", replacementPackage)
        }

        if (authorityStringResources.isNotEmpty()) {
            runCatching {
                document("res/values/strings.xml").use { document ->
                    val children = document.documentElement.childNodes
                    for (index in 0 until children.length) {
                        val element = children.item(index) as? Element ?: continue
                        if (element.getAttribute("name") !in authorityStringResources) continue
                        element.textContent = rewriteAuthority(
                            element.textContent,
                            replacementPackage
                        )
                    }
                }
            }
        }
    }
}

@Suppress("unused")
val chromeUserscriptManagerPatch = bytecodePatch(
    name = "MonkeyScript userscript manager",
    description =
        "Adds a monkey-style userscript/userstyle manager plus configurable Chrome app and package renaming for side-by-side installation.",
    default = true
) {
    compatibleWith(CHROME)
    dependsOn(addChromeUserscriptManifestPatch)
    extendWith("extensions/extension.mpe")

    // Chrome's Java API changes frequently, so the injected engine locates the active
    // Chromium Tab/WebContents at runtime instead of fingerprinting one Chrome build.
    execute { }
}
