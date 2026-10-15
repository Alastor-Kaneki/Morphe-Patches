# Morphe Patches

A shared Morphe patch repository maintained by **Alastor Kaneki**. New Morphe
patches are added to this project and built together into one `.mpp` bundle.

## Included patches

<!-- PATCHES_START EXPANDED -->
> **[v1.16.0](https://github.com/Alastor-Kaneki/Morphe-Patches/releases/tag/v1.16.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;12 patches total
<details open>
<summary>📦 Mods AddOns for Minecraft PE&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

**🎯 Supported versions:**

| 🧪&nbsp;2.5.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Custom download folder](#custom-download-folder) | Adds a native Android folder picker, a Download folder app shortcut and automatic saving of downloaded add-ons to the selected folder. |  |
| [Preserve premium entitlement](#preserve-premium-entitlement) | Keeps an already-observed premium entitlement stable across local preference migrations without granting premium to a fresh install. |  |
| [Remove ads](#remove-ads) | Disables app-open, banner, native, interstitial and rewarded ad loading while keeping the app UI flow intact. |  |
| [Rename package](#rename-package) | Changes the Android package ID and updates the app-specific provider/permission references used for Minecraft imports. | • Package name |

</details>

<details open>
<summary>📦 Suno&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Customize Suno branding](#customize-suno-branding) | Clones Suno to a different package name, with optional app-name and launcher-icon customization. Accepts Morphe's adaptive custom-icon folder layout or a simple icon.png folder. | • App name<br>• Package name<br>• Custom icon |
| [Download timestamped lyrics](#download-timestamped-lyrics) | Adds an in-app Lyrics button and share target that exports Suno timing data as LRC, enhanced LRC, SRT, WebVTT, or JSON. |  |

</details>

<details open>
<summary>📦 Opera GX&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Download GX mods as files](#download-gx-mods-as-files) | Adds a visible Download Mod button in Opera GX, with URL auto-detection and a Share fallback. |  |
| [GX icon pack](#gx-icon-pack) | Adds a separate native selector for all 56 Opera GX launcher icons. Open it from the GX Icon Pack long-press shortcut. |  |

</details>

<details open>
<summary>📦 Reddit&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Download Reddit gallery originals](#download-reddit-gallery-originals) | Adds an in-app Download Originals button and share target. Fetches gallery metadata from Arctic Shift (fallback to Reddit) and saves original-quality images or a ZIP. |  |
| [Reddit native post-menu originals (2026.41.0)](#reddit-native-post-menu-originals-2026-41-0) | Use Reddit's actual post overflow-menu event to identify the current post, show a contextual Save Originals action, and let the existing downloader save the original images or ZIP without a share link. |  |

</details>

<details open>
<summary>📦 Google Chrome&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [MonkeyScript userscript manager](#monkeyscript-userscript-manager) | Adds the Chrome 150 Material You userscript manager and cloning. Trichrome builds need the matching original Trichrome Library APK installed before the patched Chrome APK. |  |

</details>

<details open>
<summary>📦 Pixilart&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.9.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Remove ads](#remove-ads) | Hard-disables Pixilart native/Cordova ads, drawing-editor web ads, and promoted/feed ad cards. |  |

</details>

<!-- PATCHES_END -->

### Opera GX — Download GX mods as files

Targets Opera GX Android (`com.opera.gx`). It adds a visible, theme-aware
**Download Mod** button and an Android share-sheet fallback. Both resolve the GX
Store's official raw `mod.crx` package and save it to **Downloads** without
installing or activating it.

### Chrome Android — MonkeyScript userscript manager

Targets Google Chrome Android (`com.android.chrome`). The patch adds a native
userscript manager that adapts portable behavior from Violentmonkey for Chrome
Android, where the normal desktop WebExtension runtime is unavailable.

#### Chrome Material You interface

The manager, editor, and installation-review screens inherit Chrome's own app
theme and resolve Chrome/Material You colors at runtime. Surfaces, primary and
secondary accents, text, outlines, controls, ripples, status bars, and navigation
bars follow the patched Chrome build's current light, dark, and dynamic-color
palette.

#### Process-aware startup and guaranteed access

Chrome can place its browser activities in a process different from the
application's default process. MonkeyScript registers an initializer in every
process that hosts a Chrome activity, so the menu binder, page runtime, install
interceptor, and userscript engine start in the process where Chrome's UI is
actually running.

The normal entry is **Userscripts** in Chrome's app menu. Android 7.1 and newer
also receive a dynamic **Userscripts** shortcut: long-press the patched Chrome
launcher icon to open the manager even when a particular Chrome release hides
or replaces its internal app-menu model.

#### Safe Chrome app-menu integration

The patch never scans Android popup windows or modifies arbitrary menu-view
hierarchies. It therefore does not touch text-selection, link, image, or other
context menus.

MonkeyScript first checks Chrome's `AppMenuHandler`. For obfuscated releases it
performs a bounded search through the Chrome Activity's non-view object graph and
accepts a `Menu` only after it matches Chrome app-menu signatures such as
Settings, History, Downloads, Bookmarks, recent tabs, and their resource-entry
names. It then adds:

- **Userscripts** — opens the complete userscript manager.
- **Install userscript** — appears for supported Fork pages and direct
  `.user.js` / `.user.css` URLs.

Both menu rows carry explicit Activity intents as well as click listeners, which
supports Chrome menu implementations that bypass ordinary item listeners.

#### Violentmonkey-derived compatibility core

Portable userscript behavior is adapted from Violentmonkey's MIT-licensed
metadata parser and installer logic. The adaptation includes:

- Userscript and userstyle metadata-block validation.
- Localized metadata such as `@name:en` and `@description:en`.
- Normalized hyphenated and underscored metadata keys.
- Trusted install URL families used by Greasy Fork, Sleazy Fork, GitHub,
  OpenUserJS, raw GitHub content, and GitHub releases.
- Greasy Fork and Sleazy Fork script-page detection.
- Correct Fork fallback URLs that preserve both the script ID and script slug.
- Install-link interception for `.user.js` and `.user.css` links on Fork pages.

The complete desktop Violentmonkey extension is not embedded because stock
Chrome Android does not provide its required WebExtension APIs. Chrome tab
access, script injection, storage, and the native manager are supplied by the
Morphe Android bridge instead. Attribution and the upstream MIT license are in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

#### Greasy Fork and Sleazy Fork

The manager has first-class **Greasy Fork** and **Sleazy Fork** catalogue
buttons. On either Fork, tapping a userscript install link is intercepted before
Chrome handles it as a normal file navigation. The patch then opens a full-screen
review showing the parsed metadata and complete source before installation.

MonkeyScript can recognize Fork script pages, resolve official install and
update URLs, install direct `.user.js` and `.user.css` URLs, review source and
metadata, and retain the original URL for update checks.

#### Publishing userscripts

The editor includes **Publish → Greasy Fork / Sleazy Fork**. MonkeyScript stages
the current JavaScript userscript in Chrome's private storage, opens the selected
Fork in the same Chrome app, and submits the source to that site's authenticated
prefill form. The site displays its normal publish/update page for final review.

Publishing uses the Fork account already logged into Chrome. MonkeyScript does
not request or store the account password or session cookie. Existing scripts
use the version-prefill route when their Fork script ID is available; new scripts
use the new-script prefill route. CSS userstyles remain installable and
exportable, but direct Fork publishing currently targets JavaScript userscripts.

#### Patch-time app cloning

The Morphe patch exposes two editable options:

- **App name** — defaults to `Chrome MonkeyScript`.
- **Package name** — defaults to `com.android.chrome.morphe`.

The package ID must differ from stock Chrome. The resource patch qualifies
relative components before changing the manifest package, renames launcher
labels, removes shared-UID metadata, and rewrites app-scoped permissions,
processes, task affinities, provider authorities, authority string resources,
and the injected providers. This is intended to let the patched build install
beside `com.android.chrome`.

**Trichrome dependency / installation (issue #35).** Modern Android Chrome
uses a separate, required `TrichromeLibrary.apk` for its native browser code.
The Chrome APK's `<uses-static-library>` entry pins the library **name,
version, and certificate**. Renaming or re-signing the Chrome APK does not
supply that library. If a matching Trichrome version is not on the device,
Android rejects the patched Chrome installation with a missing-library error.
The patch deliberately preserves the dependency: deleting the manifest entry
does not create the native code Chrome needs.

For a Trichrome-based Chrome APK:

1. Obtain the **original Trichrome Library APK belonging to the same Chrome
   release/build variant**, from a trusted source. It must match the exact
   static-library version and signer certificate pinned in the Chrome manifest.
   A random newer/older library or a re-signed library will **not** work.
2. Install that library first if the matching version is not already installed,
   then install the patched Chrome clone. Android can retain multiple versions
   of static shared libraries. **Do not remove or replace** your system WebView
   or its Trichrome Library in an attempt to force a match.
3. For a desktop/ADB-assisted install, the repository includes a verifier
   which checks the manifests and Trichrome signing certificate before install:

```sh
python3 tools/install-chrome-trichrome.py \
  --chrome path/to/patched-chrome.apk \
  --library path/to/matching-original-TrichromeLibrary.apk \
  --install
```

Requires Android SDK `aapt2` and `apksigner` on `PATH` and, for
`--install`, a connected ADB device. Omit `--install` to verify only.
If Chrome has split APKs, supply **each correctly patched and re-signed**
split using `--split path/to/split.apk`; the script then uses
`adb install-multiple`. It does not download, patch, or re-sign any APK.
You can also install the matched original library and patched Chrome APK in
that order using an Android package installer that supports these APK types.

If no matching library is available, this Trichrome Chrome APK **cannot be
made standalone by editing the manifest**. Use an independently packaged
Chromium build supported by the patcher, or a compatible Chrome build with
its actual library dependency supplied. Android split-package and Chrome
runtime/signature compatibility remain separate limitations; passing the
library check alone does not guarantee a working Chrome clone. The script's
tests use fixture manifests rather than actual APK installation.

Changing Chrome's package and signing certificate can break Google-account
sign-in, Chrome Sync, Play Integrity relationships, trusted WebAPK relationships,
or other Google services that authorize the official package/signature pair.

#### Manager and editor

- Searchable Material You dashboard with enable/disable switches and JS/CSS
  badges.
- Source editor with parsed-metadata inspection and URL-rule testing.
- Installation from Fork pages, `.user.js`, `.user.css`, local files, clipboard
  text, or a direct URL.
- JavaScript userscript and CSS userstyle templates.
- `@updateURL` and `@downloadURL` update checks.
- Individual script export and complete JSON backup/restore.
- Global pause and per-site disable rules.

#### Metadata and runtime compatibility

MonkeyScript supports common metadata including localized names,
`@namespace`, `@version`, `@description`, `@author`, match/include/exclude rules,
`@run-at`, `@noframes`, `@grant`, `@require`, `@resource`, update/download URLs,
icons, tags, `@connect`, `@antifeature`, and `@compatible`.

Matching scripts are injected into the active Chromium `WebContents`. The
compatibility layer provides `GM_info`, synchronous and Promise-style value
storage, style and logging APIs, registered commands, tab opening, clipboard,
notifications, downloads, a best-effort `GM_xmlhttpRequest`, and `unsafeWindow`.
`@require` dependencies are cached, and CSS userstyles are injected directly.

#### Important limitations

MonkeyScript is a userscript engine, not Chrome's desktop extension runtime.
Extension service workers, `chrome.tabs`, extension popups, native messaging,
and other desktop extension APIs are not provided.

Chrome's internal Java APIs vary between releases. The process-aware initializer
and long-press shortcut guarantee access to the manager, but exact placement of
the **Userscripts** row in Chrome's overflow menu still depends on the menu model
used by the specific Chrome APK. `document-start` is best effort, page-origin
networking restrictions can affect `GM_xmlhttpRequest`, value storage is scoped
by script and page origin, and scripts are not injected in Incognito.

#### Security behavior

Userscripts execute code in pages you visit. Install only scripts you trust.
MonkeyScript stores its database, cached dependencies, and temporary publishing
source in the patched app's private storage. It does not upload the script
library, browser history, credentials, or page contents. Files without a valid
userscript metadata block are rejected or disabled until reviewed.

### Reddit 2026.41.0 — Native post overflow integration

This **additional opt-in patch**, `Reddit native post-menu originals (2026.41.0)`,
was developed against the user's real Reddit 2026.41.0 APK bundle
(versionCode `2641021`, base APK + density/ABI/language splits). It depends on
**Download Reddit gallery originals** and keeps the working Android Share target.

Static DEX inspection established that
`com.reddit.feeds.ui.events.OnOverflowMenuOpened` has a primary
constructor beginning with `String linkKindWithId`. The first instruction
invokes the superclass constructor, so a passive `invoke-static { p1 }`
post-context hook is inserted immediately afterward. A separate Android
bridge validates `t3_<post ID>` (rejecting comments/subreddits), caches it
briefly, and shows a contextual **Save originals** quick action in the Reddit
activity. The existing **↓ Originals** button now uses that exact native
context before attempting the old generic fragment/activity detector. Images
and ZIPs still come from the original `i.redd.it` CDN via the existing
Arctic Shift resolver; no clipboard or manually entered URL is needed after
opening a supported post's overflow menu.

**Compatibility/limitations:** This hook is restricted to the exact
`2026.41.0` app. The contextual action is an in-app Android view, **not
yet** a row inserted into Reddit's proprietary action sheet; some dialog
window implementations can obscure the quick action. Close the native menu
then tap **↓ Originals** if the contextual action is covered. APK splitting,
patch application, signing, installation and on-device behavior still require
testing. The original APK is *not* copied into this repository. This patch
does not modify the unrelated Morphe patches.

### Reddit — Download gallery originals

Targets the official Android Reddit app (`com.reddit.frontpage`). The **opt-in,
experimental** Morphe patch adds an in-app **↓ Originals** pill and a text/plain
share target. Tap it on a post, paste a Reddit gallery link (or use a copied
post URL), and choose:

- **Save original images to Pictures** — `Pictures/RedditGallery/<post ID>/`.
- **Download originals as ZIP** — `Downloads/reddit_gallery_<post ID>.zip`.

The patch tries Arctic Shift's public archived post metadata first and Reddit
JSON second. Only URLs built for `https://i.redd.it/` are downloaded; Reddit
preview URLs, their resizing parameters and third-party image hosts are never
used. It recognizes `image/jpg` as JPEG, fixes the MIME mismatch found in
Gallery Goblin v4, checks original image signatures before saving, and enforces
per-image bounds. File writes use Android MediaStore, so **Android 10 or newer**
is required for this initial implementation.

**Native current-post detection:** Tap the in-app **↓ Originals** pill while
viewing an individual post. The patch inspects active/resumed Reddit navigation
fragments, the current visible post's UI metadata, and the Activity's deep-link
intent (in that order). When it finds a unique post ID, a native Android
dialog offers **Images · Pictures** or **ZIP · Downloads**; selecting a mode
starts the download immediately, without pasting a link. It also accepts
Reddit's native **Share → Reddit Gallery Originals** action, which carries the
post URL directly. A clipboard/paste fallback remains optional.

No background clipboard scraping, accessibility permissions, private account
credentials, or broad arbitrary-object traversal are used. The detector
deliberately refuses ambiguous feed screens rather than downloading the wrong
post. Since Reddit's internal navigation models vary by APK version, post
detection is best-effort until verified against the exact installed Reddit APK.
The activity still needs to remain running for completion; background survival
has not yet been tested. Private/deleted media might not be available in Arctic
Shift or on Reddit's original CDN.

**Reddit Share token compatibility:** Reddit's Android Share action can provide
`reddit.com/r/<community>/s/<token>` instead of a conventional `/comments/`
post URL. The patch now recognizes those shortlinks and safely follows only
HTTPS redirects on Reddit-controlled hosts to obtain the canonical post ID
before fetching Arctic Shift metadata. The native Share target opens an
Images/ZIP chooser immediately and needs no link paste.

**403 handling:** Reddit may return HTTP 403 while expanding opaque
`/r/<community>/s/<token>` Share URLs, even though Arctic Shift can retrieve
gallery metadata once a post ID is known. When the normal HTTP redirect
resolver fails, the patch now tries the device's Android WebView browser
stack (Reddit HTTPS hosts only). If both fail, the user sees an explicit
**SHARE LINK / REDIRECT** error instead of a misleading gallery/CDN error.
The metadata and image/storage stages also report their own failures.

**Known limitation:** On some modern Reddit builds the visible post is hosted
inside a single `com.reddit.launch.main.MainActivity` with no exposed
post-bearing Fragment or deep-link Intent. The floating Originals button
cannot reliably infer the current post from those Android APIs alone.
A version-specific hook based on an actual Reddit APK will be needed for
fully automatic in-feed detection. Until then, the native Reddit Share action
is preferred and may still fail when Reddit blocks both redirect clients.
No network method can guarantee expansion of an opaque `/s/` token.

**Native detection diagnostics:** If the floating ↓ Originals button cannot
identify the active post, tap **Copy diagnostics** in its failure dialog.
This copies the Android API, active Activity/Fragment class names,
recognized post-related argument *keys* (not values), and whether the
current screen exposes a post ID. It does not copy Reddit cookies, account
details, private storage, or arbitrary app state. The report is meant to
identify the relevant navigation shape in the installed Reddit APK.

**Testing:** `bash tools/test-reddit-gallery.sh` and
`gradle buildAndroid`. Validate on a patched Reddit APK before relying on it
for large gallery downloads. Source remains modular under `patches/.../reddit/`
and `extensions/.../reddit/`.

## Build

Requirements:

- JDK 21
- Gradle 9.6.1
- GitHub credentials with read access to Morphe's GitHub Packages registry

Run:

```bash
bash tools/test-parser.sh
bash tools/test-chrome-userscripts.sh
gradle buildAndroid --stacktrace
```

The bundle is generated under `patches/build/libs/` as an `.mpp`. Every push to
`main` also runs GitHub Actions and uploads the bundle as a workflow artifact.

## Repository structure

- `patches/` — Morphe patch definitions.
- `extensions/extension/` — Android code injected by patches.
- `tools/` — local parser, matcher, installer, and payload tests.
- `.github/workflows/` — CI and controlled GitHub Release publishing.

## License

GPL-3.0. See [`LICENSE`](LICENSE). Third-party notices are in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).