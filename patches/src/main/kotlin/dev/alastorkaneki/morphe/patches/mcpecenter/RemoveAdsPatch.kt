package dev.alastorkaneki.morphe.patches.mcpecenter

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.returnEarly
import dev.alastorkaneki.morphe.patches.mcpecenter.Constants.MCPE_CENTER

/**
 * Stops the ad objects at their native load boundary instead of hiding ad views after the fact.
 * Google Mobile Ads calls are no-op'd before a request is created. Yandex load commands are
 * acknowledged immediately so their Flutter MethodChannel futures do not hang.
 */
@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description =
        "Disables app-open, banner, native, interstitial and rewarded ad loading while keeping the app UI flow intact.",
    default = true
) {
    compatibleWith(MCPE_CENTER)

    execute {
        // The app's own AppOpenManager is separate from the Flutter ad plugins.
        AppOpenFetchAdFingerprint.method.returnEarly()
        AppOpenShowAdFingerprint.method.returnEarly()

        // google_mobile_ads returns MethodChannel success from the plugin after load() is called,
        // so returning from the individual load methods cleanly prevents network ad requests.
        googleAdLoadFingerprints.forEach { fingerprint ->
            fingerprint.method.returnEarly()
        }

        // yandex_mobileads routes each load through a command handler. Complete the command with
        // success(null), but never create/load an ad. p2 is disposable after this immediate return.
        yandexAdLoadFingerprints.forEach { fingerprint ->
            fingerprint.method.addInstructions(
                0,
                """
                    const/4 p2, 0x0
                    invoke-interface { p3, p2 }, Lio/flutter/plugin/common/MethodChannel\$Result;->success(Ljava/lang/Object;)V
                    return-void
                """
            )
        }
    }
}
