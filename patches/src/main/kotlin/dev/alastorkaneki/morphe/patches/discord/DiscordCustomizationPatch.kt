package dev.alastorkaneki.morphe.patches.discord

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.imageOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import dev.alastorkaneki.morphe.patches.discord.Constants.DISCORD
import org.w3c.dom.Element
import java.io.File

private const val ORIGINAL_PACKAGE = "com.discord"
private const val CUSTOMIZER_PROVIDER =
    "dev.alastorkaneki.morphe.extension.discordcustomization.DiscordCustomizationInitProvider"
private const val CUSTOMIZER_ACTIVITY =
    "dev.alastorkaneki.morphe.extension.discordcustomization.DiscordCustomizationActivity"
private const val CUSTOMIZER_AUTHORITY =
    "com.discord.dev.alastorkaneki.customization.init"

private val componentTags = setOf(
    "application",
    "activity",
    "activity-alias",
    "service",
    "receiver",
    "provider",
    "instrumentation"
)

private fun qualifyComponentName(name: String, originalPackage: String): String = when {
    name.startsWith('.') -> originalPackage + name
    '.' !in name -> "$originalPackage.$name"
    else -> name
}

@Suppress("unused")
internal val addDiscordCustomizationManifestPatch = resourcePatch(
    description = "Adds Discord image/font customization and patch-time branding options."
) {
    compatibleWith(DISCORD)

    val customAppName by stringOption(
        key = "discordCustomAppName",
        default = "Discord Morphe",
        title = "App name",
        description = "Launcher and system app name for the patched Discord build.",
        required = true
    ) { !it.isNullOrBlank() && it.length <= 80 }

    val customPackageName by stringOption(
        key = "discordCustomPackageName",
        default = "com.discord.morphe",
        title = "Package name",
        description =
            "Android package ID for side-by-side installation. Keep this different from com.discord when official Discord is installed.",
        required = true
    ) {
        !it.isNullOrBlank() &&
            it != ORIGINAL_PACKAGE &&
            it.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$"))
    }

    val customIcon = imageOption(
        key = "discordCustomIcon",
        title = "Custom app icon",
        description =
            "Optional PNG, JPG, JPEG, or WebP image used as the launcher, installer, and system app icon.",
        allowedExtensions = listOf("png", "jpg", "jpeg", "webp")
    ) { path ->
        path == null || File(path).let { file ->
            file.isFile && file.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp")
        }
    }

    execute {
        val iconFile = customIcon.file
        var customIconResource: String? = null

        if (iconFile != null) {
            val extension = iconFile.extension.lowercase()
            val output = this["res/drawable-nodpi/morphe_discord_custom_icon.$extension"]
            output.parentFile?.mkdirs()
            iconFile.copyTo(output, overwrite = true)
            customIconResource = "@drawable/morphe_discord_custom_icon"
        }

        document("AndroidManifest.xml").use { document ->
            val application =
                document.getElementsByTagName("application").item(0) as Element

            application.setAttribute("android:label", customAppName)
            customIconResource?.let { resource ->
                application.setAttribute("android:icon", resource)
                application.setAttribute("android:roundIcon", resource)
            }

            val launcherTags = listOf("activity", "activity-alias")
            launcherTags.forEach { tag ->
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
                    if (launcher) {
                        element.setAttribute("android:label", customAppName)
                        customIconResource?.let { resource ->
                            element.setAttribute("android:icon", resource)
                        }
                    }
                }
            }

            val providerAlreadyPresent =
                (0 until application.getElementsByTagName("provider").length)
                    .map { application.getElementsByTagName("provider").item(it) as Element }
                    .any { it.getAttribute("android:name") == CUSTOMIZER_PROVIDER }

            if (!providerAlreadyPresent) {
                application.appendChild(document.createElement("provider").apply {
                    setAttribute("android:name", CUSTOMIZER_PROVIDER)
                    setAttribute("android:authorities", CUSTOMIZER_AUTHORITY)
                    setAttribute("android:exported", "false")
                    setAttribute("android:initOrder", "1999999998")
                })
            }

            val activityAlreadyPresent =
                (0 until application.getElementsByTagName("activity").length)
                    .map { application.getElementsByTagName("activity").item(it) as Element }
                    .any { it.getAttribute("android:name") == CUSTOMIZER_ACTIVITY }

            if (!activityAlreadyPresent) {
                application.appendChild(document.createElement("activity").apply {
                    setAttribute("android:name", CUSTOMIZER_ACTIVITY)
                    setAttribute("android:exported", "false")
                    setAttribute("android:excludeFromRecents", "true")
                    setAttribute("android:label", "Discord Customizer")
                    setAttribute("android:theme", "@android:style/Theme.Material.NoActionBar")
                })
            }
        }
    }

    finalize {
        val replacementPackage = customPackageName
        val providerStringResources = mutableSetOf<String>()

        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement
            val allElements = document.getElementsByTagName("*")

            // Relative component names resolve against the manifest package. Qualify
            // them before changing that package so Discord classes keep resolving.
            for (index in 0 until allElements.length) {
                val element = allElements.item(index) as Element
                if (element.tagName in componentTags && element.hasAttribute("android:name")) {
                    val name = element.getAttribute("android:name")
                    if (name.isNotBlank()) {
                        element.setAttribute(
                            "android:name",
                            qualifyComponentName(name, ORIGINAL_PACKAGE)
                        )
                    }
                }
                if (element.tagName == "activity-alias" &&
                    element.hasAttribute("android:targetActivity")) {
                    val target = element.getAttribute("android:targetActivity")
                    if (target.isNotBlank()) {
                        element.setAttribute(
                            "android:targetActivity",
                            qualifyComponentName(target, ORIGINAL_PACKAGE)
                        )
                    }
                }
            }

            val permissionAttributes = listOf(
                "android:permission",
                "android:readPermission",
                "android:writePermission"
            )

            for (index in 0 until allElements.length) {
                val element = allElements.item(index) as Element

                permissionAttributes.forEach { attribute ->
                    if (!element.hasAttribute(attribute)) return@forEach
                    val value = element.getAttribute(attribute)
                    if (value == ORIGINAL_PACKAGE || value.startsWith("$ORIGINAL_PACKAGE.")) {
                        element.setAttribute(
                            attribute,
                            value.replaceFirst(ORIGINAL_PACKAGE, replacementPackage)
                        )
                    }
                }

                if ((element.tagName == "permission" ||
                        element.tagName == "uses-permission") &&
                    element.hasAttribute("android:name")) {
                    val value = element.getAttribute("android:name")
                    if (value == ORIGINAL_PACKAGE || value.startsWith("$ORIGINAL_PACKAGE.")) {
                        element.setAttribute(
                            "android:name",
                            value.replaceFirst(ORIGINAL_PACKAGE, replacementPackage)
                        )
                    }
                }

                if (element.tagName == "provider" &&
                    element.hasAttribute("android:authorities")) {
                    val authorities = element.getAttribute("android:authorities")
                        .split(';')
                        .map { authority ->
                            when {
                                authority.startsWith("@string/") -> {
                                    providerStringResources += authority.removePrefix("@string/")
                                    authority
                                }
                                authority == ORIGINAL_PACKAGE ||
                                    authority.startsWith("$ORIGINAL_PACKAGE.") ->
                                    authority.replaceFirst(ORIGINAL_PACKAGE, replacementPackage)
                                else -> "$replacementPackage.$authority"
                            }
                        }
                    element.setAttribute("android:authorities", authorities.joinToString(";"))
                }
            }

            manifest.setAttribute("package", replacementPackage)
        }

        if (providerStringResources.isNotEmpty()) {
            runCatching {
                document("res/values/strings.xml").use { document ->
                    val children = document.documentElement.childNodes
                    for (index in 0 until children.length) {
                        val element = children.item(index) as? Element ?: continue
                        if (element.getAttribute("name") !in providerStringResources) continue
                        val authority = element.textContent
                        element.textContent = when {
                            authority == ORIGINAL_PACKAGE ||
                                authority.startsWith("$ORIGINAL_PACKAGE.") ->
                                authority.replaceFirst(ORIGINAL_PACKAGE, replacementPackage)
                            else -> "$replacementPackage.$authority"
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
val discordCustomizationPatch = bytecodePatch(
    name = "Discord customization and clone",
    description =
        "Adds image themes and custom fonts, plus patch-time app name, package ID, and custom app icon options.",
    default = true
) {
    compatibleWith(DISCORD)
    dependsOn(addDiscordCustomizationManifestPatch)
    extendWith("extensions/extension.mpe")
    execute { }
}
