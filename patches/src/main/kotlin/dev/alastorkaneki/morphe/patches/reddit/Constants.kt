package dev.alastorkaneki.morphe.patches.reddit

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal object Constants {
    val REDDIT = Compatibility(
        name = "Reddit",
        packageName = "com.reddit.frontpage",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF4500,
        targets = listOf(
            // Self-contained Java extension; avoids version-specific/obfuscated hooks.
            AppTarget(version = null, isExperimental = true)
        )
    )
}
