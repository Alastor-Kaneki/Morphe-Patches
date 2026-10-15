package dev.alastorkaneki.morphe.patches.reddit

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import dev.alastorkaneki.morphe.patches.reddit.Constants.REDDIT
import org.w3c.dom.Element

private const val ACTIVITY = "dev.alastorkaneki.morphe.extension.reddit.RedditGalleryActivity"
private const val PROVIDER = "dev.alastorkaneki.morphe.extension.reddit.RedditGalleryInitProvider"
private const val AUTHORITY = "com.reddit.frontpage.morphe.galleryoriginals.init"

/** Registers an in-app, opt-in gallery downloader; no hooks into Reddit's internal classes. */
@Suppress("unused")
internal val addRedditGalleryOriginalsManifestPatch = resourcePatch(
    description = "Registers a gallery-originals activity, floating entry point, and link share target."
) {
    compatibleWith(REDDIT)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val manifest = doc.documentElement
            val application = doc.getElementsByTagName("application").item(0) as Element

            if ((0 until doc.getElementsByTagName("uses-permission").length)
                    .none { (doc.getElementsByTagName("uses-permission").item(it) as Element)
                        .getAttribute("android:name") == "android.permission.INTERNET" }) {
                manifest.insertBefore(doc.createElement("uses-permission").apply {
                    setAttribute("android:name", "android.permission.INTERNET")
                }, application)
            }

            if ((0 until application.getElementsByTagName("provider").length)
                    .none { (application.getElementsByTagName("provider").item(it) as Element)
                        .getAttribute("android:name") == PROVIDER }) {
                application.appendChild(doc.createElement("provider").apply {
                    setAttribute("android:name", PROVIDER)
                    setAttribute("android:authorities", AUTHORITY)
                    setAttribute("android:enabled", "true")
                    setAttribute("android:exported", "false")
                    setAttribute("android:initOrder", "100")
                })
            }

            if ((0 until application.getElementsByTagName("activity").length)
                    .none { (application.getElementsByTagName("activity").item(it) as Element)
                        .getAttribute("android:name") == ACTIVITY }) {
                val activity = doc.createElement("activity").apply {
                    setAttribute("android:name", ACTIVITY)
                    setAttribute("android:exported", "true")
                    setAttribute("android:excludeFromRecents", "true")
                    setAttribute("android:label", "Reddit Gallery Originals")
                    setAttribute("android:theme", "@android:style/Theme.Material.NoActionBar")
                    setAttribute("android:launchMode", "singleTop")
                }
                activity.appendChild(doc.createElement("intent-filter").apply {
                    appendChild(doc.createElement("action").apply {
                        setAttribute("android:name", "android.intent.action.SEND")
                    })
                    appendChild(doc.createElement("category").apply {
                        setAttribute("android:name", "android.intent.category.DEFAULT")
                    })
                    appendChild(doc.createElement("data").apply {
                        setAttribute("android:mimeType", "text/plain")
                    })
                })
                application.appendChild(activity)
            }
        }
    }
}

/**
 * Independent opt-in patch: adds a floating Download Originals button in Reddit.
 *
 * Tapping it opens the self-contained activity. The activity accepts shared/copied
 * Reddit gallery links, fetches archived metadata (with direct Reddit fallback),
 * then saves only CDN originals. No API tokens, cookies or private data exported.
 */
@Suppress("unused")
val redditGalleryOriginalsPatch = bytecodePatch(
    name = "Download Reddit gallery originals",
    description =
        "Adds an in-app Download Originals button and share target. Fetches gallery metadata " +
        "from Arctic Shift (fallback to Reddit) and saves original-quality images or a ZIP.",
    default = false
) {
    compatibleWith(REDDIT)
    dependsOn(addRedditGalleryOriginalsManifestPatch)
    extendWith("extensions/extension.mpe")
    execute { }
}
