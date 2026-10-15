package dev.alastorkaneki.morphe.patches.reddit

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

private const val MENU_EVENT =
    "Lcom/reddit/feeds/ui/events/OnOverflowMenuOpened;"
private const val NATIVE_CONTEXT =
    "Ldev/alastorkaneki/morphe/extension/reddit/RedditNativeOverflowContext;"

/**
 * Verified against the user's 2026.41.0 (2641021) base.apk.
 * Native event primary constructor:
 * (String linkKindWithId, String, boolean, OverflowMenuType, OverflowMenuTrigger, ue30)
 *
 * The primary constructor's first instruction is invoke-direct Object.<init>.
 * Inserting at instruction index 1 preserves constructor verification.
 */
private object Reddit202641OverflowEventFingerprint : Fingerprint(
    definingClass = MENU_EVENT,
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Z",
        "Lcom/reddit/feeds/ui/OverflowMenuType;",
        "Lcom/reddit/feeds/ui/OverflowMenuTrigger;",
        "Lue30;"
    )
)

/**
 * An additional opt-in patch restricted to the exact uploaded Reddit build.
 * Intercepts Reddit's real overflow-menu event to associate the menu with
 * its t3 post ID, then exposes a contextual Android Save Originals action.
 * It does not scrape views or interfere with the original menu's handlers.
 */
@Suppress("unused")
val reddit202641NativeOverflowPatch = bytecodePatch(
    name = "Reddit native post-menu originals (2026.41.0)",
    description = "Use Reddit's actual post overflow-menu event to identify the current post, " +
        "show a contextual Save Originals action, and let the existing downloader " +
        "save the original images or ZIP without a share link.",
    default = false
) {
    compatibleWith(
        Compatibility(
            name = "Reddit",
            packageName = "com.reddit.frontpage",
            apkFileType = ApkFileType.APK,
            appIconColor = 0xFF4500,
            targets = listOf(AppTarget(version = "2026.41.0", isExperimental = true))
        )
    )
    dependsOn(redditGalleryOriginalsPatch)

    execute {
        // The first parameter is named linkKindWithId in the original APK's
        // event toString. Values such as t3_1ur2wv1 identify exactly the post.
        // This is a passive side-effect; existing menu behavior continues.
        Reddit202641OverflowEventFingerprint.method.addInstruction(
            1,
            "invoke-static { p1 }, ${NATIVE_CONTEXT}->onMenuOpened(Ljava/lang/String;)V"
        )
    }
}
