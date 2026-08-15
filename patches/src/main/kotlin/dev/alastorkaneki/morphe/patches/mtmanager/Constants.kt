package dev.alastorkaneki.morphe.patches.mtmanager

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal object Constants {
    val MT_MANAGER = Compatibility(
        name = "MT Manager",
        packageName = "bin.mt.plus",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x2196F3,
        targets = listOf(
            // Manifest-only patch: no version-specific bytecode fingerprints are required.
            AppTarget(
                version = null,
                isExperimental = true
            )
        )
    )
}
