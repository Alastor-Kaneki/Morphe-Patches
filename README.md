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

#### Manager and editor

- Floating monkey button with current-page actions.
- Native searchable dashboard with enable/disable switches and JS/CSS badges.
- Full source editor with parsed-metadata inspection and URL-match testing.
- Install from `.user.js`, JavaScript, CSS, clipboard text, or a direct URL.
- Create JavaScript userscripts and CSS userstyles from templates.
- Check `@updateURL` / `@downloadURL` sources for updates.
- Import/export individual scripts and JSON backups of the complete library.
- Global pause, per-site disable rules, floating-button control, and AMOLED UI.

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

The patch discovers Chrome's active Chromium `Tab` and `WebContents` at runtime
instead of fingerprinting one obfuscated Chrome release. This is more
version-resilient but still needs runtime testing on each Chrome build.
`document-start` is best effort, `GM_xmlhttpRequest` uses page networking and can
be affected by CORS, and script value storage is scoped by script and page origin.
Scripts are deliberately not injected in Incognito.

#### Security behavior

Userscripts execute code in pages you visit. Install only scripts you trust.
MonkeyScript stores its database and cached dependencies in the patched app's
private storage. It does not upload the script library, browser history,
credentials, or page contents. Raw scripts without explicit match rules are
disabled until reviewed.

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
