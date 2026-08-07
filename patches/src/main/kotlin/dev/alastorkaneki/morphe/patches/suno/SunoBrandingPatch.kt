package dev.alastorkaneki.morphe.patches.suno

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.folderOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import dev.alastorkaneki.morphe.patches.suno.Constants.SUNO
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File

private val MORPHE_ICON_DENSITIES = listOf(
    "mipmap-mdpi",
    "mipmap-hdpi",
    "mipmap-xhdpi",
    "mipmap-xxhdpi",
    "mipmap-xxxhdpi"
)

private const val MORPHE_BACKGROUND_FILE = "morphe_adaptive_background_custom.png"
private const val MORPHE_FOREGROUND_FILE = "morphe_adaptive_foreground_custom.png"
private const val EASY_ICON_FILE = "icon.png"

private const val CUSTOM_LAUNCHER = "morphe_suno_launcher"
private const val CUSTOM_BACKGROUND = "morphe_suno_adaptive_background"
private const val CUSTOM_FOREGROUND = "morphe_suno_adaptive_foreground"

private fun Element.directChildren(tag: String): List<Element> =
    (0 until childNodes.length)
        .map { childNodes.item(it) }
        .filter { it.nodeType == Node.ELEMENT_NODE && it.nodeName == tag }
        .map { it as Element }

private fun Element.hasLauncherIntent(): Boolean =
    directChildren("intent-filter").any { filter ->
        val actions = filter.directChildren("action")
            .map { it.getAttribute("android:name") }
        val categories = filter.directChildren("category")
            .map { it.getAttribute("android:name") }

        "android.intent.action.MAIN" in actions &&
            (
                "android.intent.category.LAUNCHER" in categories ||
                    "android.intent.category.LEANBACK_LAUNCHER" in categories
                )
    }

/**
 * Static Suno branding patch.
 *
 * This intentionally changes only resources and manifest metadata. It does not clone or
 * rename the package, so it can be selected independently from the timestamped-lyrics patch.
 */
@Suppress("unused")
val sunoBrandingPatch = resourcePatch(
    name = "Customize Suno branding",
    description =
        "Lets you rename Suno and optionally replace its launcher icon. " +
            "Accepts Morphe's adaptive custom-icon folder layout or a simple icon.png folder.",
    default = false
) {
    compatibleWith(SUNO)

    val customName by stringOption(
        key = "customName",
        title = "App name",
        description = "Optional replacement name shown by Android for the patched Suno app."
    )

    val customIcon by folderOption(
        key = "customIcon",
        title = "Custom icon",
        description = """
            Optional folder containing the launcher icon.

            Easy layout:
            - icon.png

            Morphe adaptive layout:
            - mipmap-mdpi/$MORPHE_BACKGROUND_FILE
            - mipmap-mdpi/$MORPHE_FOREGROUND_FILE
            - mipmap-hdpi/$MORPHE_BACKGROUND_FILE
            - mipmap-hdpi/$MORPHE_FOREGROUND_FILE
            - mipmap-xhdpi/$MORPHE_BACKGROUND_FILE
            - mipmap-xhdpi/$MORPHE_FOREGROUND_FILE
            - mipmap-xxhdpi/$MORPHE_BACKGROUND_FILE
            - mipmap-xxhdpi/$MORPHE_FOREGROUND_FILE
            - mipmap-xxxhdpi/$MORPHE_BACKGROUND_FILE
            - mipmap-xxxhdpi/$MORPHE_FOREGROUND_FILE

            You may provide only the density folders you need, but each provided density
            must contain both the background and foreground image.
        """.trimIndent()
    )

    execute {
        val nameWasProvided = customName != null
        val resolvedName = customName?.trim()
        if (nameWasProvided && resolvedName.isNullOrEmpty()) {
            throw PatchException("Custom Suno app name cannot be blank.")
        }

        var iconReference: String? = null

        customIcon?.trim()?.takeIf { it.isNotEmpty() }?.let { rawPath ->
            val iconFolder = File(rawPath)
            if (!iconFolder.exists()) {
                throw PatchException(
                    "The custom Suno icon folder cannot be found: ${iconFolder.absolutePath}"
                )
            }
            if (!iconFolder.isDirectory) {
                throw PatchException(
                    "The custom Suno icon path must be a folder: ${iconFolder.absolutePath}"
                )
            }

            val easyIcon = iconFolder.resolve(EASY_ICON_FILE)
            if (easyIcon.isFile) {
                val output = get("res/mipmap-nodpi/$CUSTOM_LAUNCHER.png")
                output.parentFile.mkdirs()
                easyIcon.copyTo(output, overwrite = true)
                iconReference = "@mipmap/$CUSTOM_LAUNCHER"
            } else {
                var copiedAdaptivePair = false

                MORPHE_ICON_DENSITIES.forEach { density ->
                    val sourceDirectory = iconFolder.resolve(density)
                    if (!sourceDirectory.isDirectory) return@forEach

                    val background = sourceDirectory.resolve(MORPHE_BACKGROUND_FILE)
                    val foreground = sourceDirectory.resolve(MORPHE_FOREGROUND_FILE)
                    val hasBackground = background.isFile
                    val hasForeground = foreground.isFile

                    if (hasBackground != hasForeground) {
                        throw PatchException(
                            "$density must contain both $MORPHE_BACKGROUND_FILE and " +
                                "$MORPHE_FOREGROUND_FILE."
                        )
                    }
                    if (!hasBackground) return@forEach

                    val targetDirectory = get("res/$density")
                    targetDirectory.mkdirs()
                    background.copyTo(
                        targetDirectory.resolve("$CUSTOM_BACKGROUND.png"),
                        overwrite = true
                    )
                    foreground.copyTo(
                        targetDirectory.resolve("$CUSTOM_FOREGROUND.png"),
                        overwrite = true
                    )
                    copiedAdaptivePair = true
                }

                if (!copiedAdaptivePair) {
                    throw PatchException(
                        "Custom Suno icon folder must contain icon.png or at least one " +
                            "complete Morphe adaptive-icon density folder."
                    )
                }

                val legacyXml = get("res/mipmap-anydpi/$CUSTOM_LAUNCHER.xml")
                legacyXml.parentFile.mkdirs()
                legacyXml.writeText(
                    """<?xml version="1.0" encoding="utf-8"?>
                        |<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
                        |    <item android:drawable="@mipmap/$CUSTOM_BACKGROUND" />
                        |    <item android:drawable="@mipmap/$CUSTOM_FOREGROUND" />
                        |</layer-list>
                    """.trimMargin()
                )

                val adaptiveXml = get("res/mipmap-anydpi-v26/$CUSTOM_LAUNCHER.xml")
                adaptiveXml.parentFile.mkdirs()
                adaptiveXml.writeText(
                    """<?xml version="1.0" encoding="utf-8"?>
                        |<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                        |    <background android:drawable="@mipmap/$CUSTOM_BACKGROUND" />
                        |    <foreground android:drawable="@mipmap/$CUSTOM_FOREGROUND" />
                        |</adaptive-icon>
                    """.trimMargin()
                )

                iconReference = "@mipmap/$CUSTOM_LAUNCHER"
            }
        }

        document("AndroidManifest.xml").use { document ->
            val application =
                document.getElementsByTagName("application").item(0) as? Element
                    ?: throw PatchException("Suno application manifest element was not found.")

            if (resolvedName != null) {
                application.setAttribute("android:label", resolvedName)
            }

            if (iconReference != null) {
                application.setAttribute("android:icon", iconReference!!)
                application.setAttribute("android:roundIcon", iconReference!!)
            }

            sequenceOf("activity", "activity-alias")
                .flatMap { tag -> application.directChildren(tag).asSequence() }
                .filter { it.hasLauncherIntent() }
                .forEach { launcher ->
                    if (resolvedName != null) {
                        launcher.setAttribute("android:label", resolvedName)
                    }
                    if (iconReference != null) {
                        launcher.setAttribute("android:icon", iconReference!!)
                    }
                }
        }
    }
}
