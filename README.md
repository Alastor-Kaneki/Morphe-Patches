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

Targets Google Chrome Android (`com.android.chrome`). MonkeyScript combines the
mobile-friendly ideas of Tampermonkey, Violentmonkey, FireMonkey, and
Greasemonkey in a native manager built into patched Chrome.

#### Embedded Chrome menu

The patch modifies Chrome's actual `main_menu.xml` and `custom_tabs_menu.xml`
resources. Chrome's overflow menu receives:

- **MonkeyScript** — opens the complete userscript manager.
- **Install userscript** — appears on a supported Fork script page or direct
  `.user.js` / `.user.css` page and opens a full-screen installation review.

There is no floating monkey button or separate popup control. The injected
runtime binds actions to the resource-created Chrome menu rows when the app menu
is displayed.

#### Greasy Fork and Sleazy Fork

The manager has first-class **Greasy Fork** and **Sleazy Fork** buttons that open
the selected catalogue inside the patched Chrome app. MonkeyScript can:

- Recognize Greasy Fork and Sleazy Fork script pages.
- Resolve their official install links and update-host URLs.
- Review the script name, version, type, match rules, description, and complete
  source before installation.
- Install direct `.user.js` and `.user.css` URLs.
- Retain the Fork install URL for future update checks.

#### Publishing userscripts

The editor includes **Publish → Greasy Fork / Sleazy Fork**. MonkeyScript stages
the current JavaScript userscript in Chrome's private storage, opens the selected
Fork in the same Chrome app, and submits the source to that site's official
prefill form. The site then displays its normal publish/update page for the user
to review and confirm.

Publishing uses the Fork account already logged into Chrome. MonkeyScript does
not request, read, or store the account password or session cookie. Existing
Fork scripts use the version-prefill route when their script ID can be recovered;
new scripts use the new-script prefill route. Direct Fork publishing currently
supports JavaScript userscripts. CSS userstyles remain installable and exportable
as `.user.css` files.

#### Patch-time app cloning

The Morphe patch exposes two editable options:

- **App name** — defaults to `Chrome MonkeyScript`.
- **Package name** — defaults to `com.android.chrome.morphe`.

The package ID must differ from stock Chrome. The resource patch qualifies
relative components before changing the manifest package, renames launcher
labels, removes shared-UID metadata, rewrites app-scoped permissions, task and
process identities, provider authorities, authority string resources, and the
injected MonkeyScript provider. This is intended to let the patched build install
beside `com.android.chrome` instead of trying to replace the system-signed app.

Changing Chrome's package and signing certificate can break Google-account sign
in, Chrome Sync, Play-integrity checks, trusted WebAPK relationships, or other
Google services that authorize the official package/signature pair. The browser
and MonkeyScript manager do not depend on those services, but exact behavior
still depends on the Chrome APK being patched.

#### Manager and editor

- Native searchable dashboard with enable/disable switches and JS/CSS badges.
- Full source editor with parsed-metadata inspection and URL-match testing.
- Install from Fork pages, `.user.js`, `.user.css`, JavaScript, CSS, local files,
  clipboard text, or a direct URL.
- Create JavaScript userscripts and CSS userstyles from templates.
- Check `@updateURL` / `@downloadURL` sources for updates.
- Import/export individual scripts and JSON backups of the complete library.
- Global pause, per-site disable rules, and true-black AMOLED UI.

#### Metadata compatibility

MonkeyScript parses the standard monkey metadata block, including:

- `@name`, `@namespace`, `@version`, `@description`, and `@author`
- `@match`, `@include`, `@exclude`, and `@exclude-match`
- `@run-at`, `@noframes`, `@grant`, `@require`, and `@resource`
- `@updateURL`, `@downloadURL`, `@icon`, and tags

Chrome match patterns, wildcard globs, regular-expression includes, and
`<all_urls>` are supported for HTTP/HTTPS pages.

#### Runtime and GM compatibility

Matching scripts are injected into the active Chromium `WebContents`. The
compatibility layer supplies commonly used APIs such as:

- `GM_info` and `GM.info`
- `GM_getValue`, `GM_setValue`, `GM_deleteValue`, and `GM_listValues`
- Promise-style `GM.getValue`, `GM.setValue`, `GM.deleteValue`, and `GM.listValues`
- `GM_addStyle`, `GM_log`, and their `GM.*` counterparts
- `GM_registerMenuCommand`, `GM_openInTab`, clipboard, notification, and download
- A best-effort `GM_xmlhttpRequest`
- `unsafeWindow`

`@require` dependencies are fetched and cached when installing or updating a
script. CSS userstyles are injected directly into matching pages.

#### Important limitations

MonkeyScript is a userscript engine, not Chrome's desktop extension runtime.
Chrome extension service workers, `chrome.tabs`, extension popups, native
messaging, and other desktop extension APIs are not provided.

The patch discovers Chrome's active Chromium `Tab`, `WebContents`, and displayed
app-menu row views at runtime instead of fingerprinting one obfuscated Chrome
release. This is more version-resilient but still needs runtime testing on each
Chrome build. `document-start` is best effort, `GM_xmlhttpRequest` uses page
networking and can be affected by CORS, and script value storage is scoped by
script and page origin. Scripts are deliberately not injected in Incognito.

#### Security behavior

Userscripts execute code in pages you visit. Install only scripts you trust.
MonkeyScript stores its database, cached dependencies, and temporary publish
source in the patched app's private storage. It does not upload the script
library, browser history, credentials, or page contents. Raw scripts without
explicit match rules are disabled until reviewed.

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
- `tools/` — local parser, matcher, and payload tests.
- `.github/workflows/` — CI and controlled GitHub Release publishing.

## License

GPL-3.0. See [`LICENSE`](LICENSE).
