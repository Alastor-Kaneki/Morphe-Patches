# Morphe Patches

A shared Morphe patch repository maintained by **Alastor Kaneki**. New Morphe
patches are added to this project and built together into one `.mpp` bundle.

## Included patches

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
palette. The previous orange-purple-red manager theme and independent AMOLED
option were removed.

#### Safe Chrome app-menu integration

The patch no longer scans Android popup windows or modifies arbitrary menu view
hierarchies. That older fallback could mistake selection and context menus for
Chrome's overflow menu.

MonkeyScript now starts from Chrome's `AppMenuHandler` object and searches only
that app-menu object graph for its backing Android `Menu`. When available, it
adds:

- **MonkeyScript** — opens the complete userscript manager.
- **Install userscript** — appears for supported Fork pages and direct
  `.user.js` / `.user.css` URLs.

The patch does not modify Chrome's text-selection, link, image, or other context
menus. It also no longer requires specific `res/menu/*.xml` filenames.

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

MonkeyScript can:

- Recognize Greasy Fork and Sleazy Fork script pages.
- Resolve official install links and update-host URLs.
- Install direct `.user.js` and `.user.css` URLs.
- Review the script name, version, type, rules, description, and source.
- Retain the original install URL for update checks.

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
and the injected provider. This is intended to let the patched build install
beside `com.android.chrome`.

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

MonkeyScript supports common metadata including:

- `@name`, localized names, `@namespace`, `@version`, `@description`, and
  `@author`.
- `@match`, `@include`, `@exclude`, and `@exclude-match`.
- `@run-at`, `@noframes`, `@grant`, `@require`, and `@resource`.
- `@updateURL`, `@downloadURL`, `@icon`, tags, `@connect`, `@antifeature`, and
  `@compatible`.

Matching scripts are injected into the active Chromium `WebContents`. The
compatibility layer provides commonly used APIs such as:

- `GM_info` and `GM.info`.
- Synchronous and Promise-style value storage APIs.
- `GM_addStyle`, `GM_log`, and their `GM.*` counterparts.
- `GM_registerMenuCommand`, `GM_openInTab`, clipboard, notification, and
  download helpers.
- A best-effort `GM_xmlhttpRequest`.
- `unsafeWindow`.

`@require` dependencies are fetched and cached when installing or updating a
script. CSS userstyles are injected directly into matching pages.

#### Important limitations

MonkeyScript is a userscript engine, not Chrome's desktop extension runtime.
Extension service workers, `chrome.tabs`, extension popups, native messaging,
and other desktop extension APIs are not provided.

Chrome's internal Java APIs vary between releases. The patch discovers the
active Chromium `Tab`, `WebContents`, and `AppMenuHandler` at runtime instead of
fingerprinting one obfuscated release. Exact menu availability and page
injection still require runtime testing on the specific Chrome APK being
patched. `document-start` is best effort, page-origin networking restrictions can
affect `GM_xmlhttpRequest`, and value storage is scoped by script and page
origin. Scripts are not injected in Incognito.

#### Security behavior

Userscripts execute code in pages you visit. Install only scripts you trust.
MonkeyScript stores its database, cached dependencies, and temporary publishing
source in the patched app's private storage. It does not upload the script
library, browser history, credentials, or page contents. Files without a valid
userscript metadata block are rejected or disabled until reviewed.

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
