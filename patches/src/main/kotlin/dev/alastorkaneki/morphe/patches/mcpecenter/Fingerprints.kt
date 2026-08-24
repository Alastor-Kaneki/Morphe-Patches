package dev.alastorkaneki.morphe.patches.mcpecenter

import app.morphe.patcher.Fingerprint

private const val MAIN_ACTIVITY =
    "Lcom/ultimategamestudio/mcpecenter/mods/MainActivity;"
private const val APP_OPEN_MANAGER =
    "Lcom/ultimategamestudio/mcpecenter/mods/AppOpenManager;"

internal object ConfigureFlutterEngineFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY,
    name = "configureFlutterEngine",
    returnType = "V",
    parameters = listOf("Lio/flutter/embedding/engine/FlutterEngine;")
)

internal object AddonMethodChannelFingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY,
    name = "lambda\$configureFlutterEngine\$0\$com-ultimategamestudio-mcpecenter-mods-MainActivity",
    returnType = "V",
    parameters = listOf(
        "Lio/flutter/plugin/common/MethodCall;",
        "Lio/flutter/plugin/common/MethodChannel\$Result;"
    ),
    strings = listOf("install", "open_with", "isAmazon")
)

internal object SendToMinecraft33Fingerprint : Fingerprint(
    definingClass = MAIN_ACTIVITY,
    name = "sendToMC33",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf(
        Constants.ORIGINAL_PACKAGE,
        Constants.ORIGINAL_FILE_PROVIDER,
        "com.mojang.minecraftpe"
    )
)

internal object AppOpenFetchAdFingerprint : Fingerprint(
    definingClass = APP_OPEN_MANAGER,
    name = "fetchAd",
    returnType = "V",
    parameters = listOf("Z"),
    strings = listOf("ca-app-pub-9930139020223199/7686566943")
)

internal object AppOpenShowAdFingerprint : Fingerprint(
    definingClass = APP_OPEN_MANAGER,
    name = "showAdIfAvailable",
    returnType = "V",
    parameters = listOf()
)

private fun googleLoadFingerprint(definingClass: String) = Fingerprint(
    definingClass = definingClass,
    name = "load",
    returnType = "V"
)

internal val googleAdLoadFingerprints = listOf(
    googleLoadFingerprint("Lio/flutter/plugins/googlemobileads/FlutterBannerAd;"),
    googleLoadFingerprint("Lio/flutter/plugins/googlemobileads/FlutterAdManagerBannerAd;"),
    googleLoadFingerprint("Lio/flutter/plugins/googlemobileads/FlutterInterstitialAd;"),
    googleLoadFingerprint("Lio/flutter/plugins/googlemobileads/FlutterAdManagerInterstitialAd;"),
    googleLoadFingerprint("Lio/flutter/plugins/googlemobileads/FlutterAppOpenAd;"),
    googleLoadFingerprint("Lio/flutter/plugins/googlemobileads/FlutterRewardedAd;"),
    googleLoadFingerprint("Lio/flutter/plugins/googlemobileads/FlutterRewardedInterstitialAd;"),
    googleLoadFingerprint("Lio/flutter/plugins/googlemobileads/FlutterNativeAd;")
)

private val yandexCommandParameters = listOf(
    "Ljava/lang/String;",
    "Ljava/lang/Object;",
    "Lio/flutter/plugin/common/MethodChannel\$Result;"
)

private fun yandexLoadFingerprint(definingClass: String) = Fingerprint(
    definingClass = definingClass,
    name = "handleCommand",
    returnType = "V",
    parameters = yandexCommandParameters
)

internal val yandexAdLoadFingerprints = listOf(
    yandexLoadFingerprint(
        "Lcom/yandex/mobile/ads/flutter/banner/command/LoadBannerCommandHandler;"
    ),
    yandexLoadFingerprint(
        "Lcom/yandex/mobile/ads/flutter/interstitial/command/LoadInterstitialAdCommandHandler;"
    ),
    yandexLoadFingerprint(
        "Lcom/yandex/mobile/ads/flutter/rewarded/command/LoadRewardedAdCommandHandler;"
    ),
    yandexLoadFingerprint(
        "Lcom/yandex/mobile/ads/flutter/appopenad/command/LoadAppOpenAdCommandHandler;"
    )
)
