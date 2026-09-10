# Upstream merge preservation checklist

This branch exists to integrate `KevinnZou/compose-webview-multiplatform:main` without losing fork-specific behavior.

## Rules

- Do not modify or merge into `main` until this branch builds and the compatibility checks below pass.
- Treat upstream as the new implementation base, but preserve externally visible fork APIs and behaviors unless explicitly marked for replacement.
- Do not resolve conflicts mechanically with `ours` or `theirs` for core WebView files.
- Verify behavior from the final code, not commit messages alone.

## Fork features that must not disappear

- Android custom/fullscreen video support and fullscreen state (`isFullScreen` / `fullscreenState`, `onShowCustomView`, `onHideCustomView`).
- JS alert -> application/WebView event bridging on Android/iOS where currently supported.
- Dynamic JS bridge method generation (`registerDelegateMethod` and equivalent `window.<bridge>.<method>()` behavior).
- Sync/async JS callback distinction (`isSyncCallbackMethod`, `isAsyncCallbackMethod`).
- JS method parameter metadata (`minimalParamCount`, `methodParamCount`).
- SSL host allowlist behavior currently exposed as `sslPiningHosts` (preserve API/behavior compatibility; implementation may be hardened).
- `LoadingState.ErrorLoading` or equivalent public error-loading state.
- Explicit WebView destruction API (`destroy()` and any legacy `destory()` compatibility if present).
- Extended request/header APIs used by the fork, including extra headers for supported `WebContent` / navigator entry points.
- `rememberWebViewState(... callback)` compatibility if still exposed by the fork.
- Common `WebSettings.isInspectable` compatibility.
- iOS media autoplay policy granularity (AUDIO / VIDEO / ALL / NONE or equivalent behavior).
- Android mixed-content behavior must not silently change; if upstream differs, preserve compatibility through an explicit setting/default.
- Android viewport/overview/initial-scale behavior currently relied on by the fork.
- iOS console.log bridge/capture behavior.
- iOS cleanup that pauses media before destroy, if still present in current fork.
- iOS cooperative touch handling currently relied on by the fork.
- Fork Maven coordinates and publication metadata must remain under the vickyleu namespace.
- `.github/workflows/publish.yml` must remain available unless replaced by a verified equivalent.

## Fork behavior that should be preserved externally but may be reimplemented

### Android fullscreen

Preserve the public fullscreen behavior/state, but do not blindly preserve the current implementation if it uses a second `WindowManager` application window and runtime hardware-acceleration flag toggling.

When resolving this area, prefer the Activity/decor view hierarchy and stable WebView/Surface lifecycle. Do not clear `FLAG_HARDWARE_ACCELERATED` when exiting fullscreen.

### SSL allowlist

The existing name `sslPiningHosts` is compatibility-sensitive even though the behavior is not conventional certificate/public-key pinning. Preserve source compatibility, document the actual behavior, and do not silently widen trust.

### Additional HTTP headers

Preserve source-level APIs even if some current Android implementations do not actually apply headers for every content type. Do not claim functionality that the underlying WebView API cannot provide. Where possible, implement correctly; otherwise retain compatibility and document limitations.

## Important upstream functionality to bring in

Review upstream `main` at merge time and include at least the currently present improvements where compatible:

- Android `WebViewAssetLoader` / sandboxed local content support.
- `WebViewFileReadType` / Asset / Compose Resources support.
- Android camera, microphone, DRM, MIDI permission handling where upstream provides it.
- Android/upstream console bridge improvements.
- Layer type mapping using actual Android `View.LAYER_TYPE_*` values.
- `evaluateJavascript` fixes for headless/non-attached WebViews.
- `WebViewError.isFromMainFrame` or current upstream equivalent.
- WasmJS WebView support.
- iOS real-device local-file loading fixes.
- iOS safe-area/inset/fullscreen improvements.
- iOS `isInspectable` availability handling.
- Desktop CEF/JCEF, CookieManager and User-Agent fixes.

The exact list must be refreshed against the current upstream HEAD before merge.

## Publication constraints

The current fork publication config before upstream integration uses:

- group: `io.github.vickyleu.webview`
- artifact: `webview`
- version: `2.0.0`

The consumer `KMMCompose` currently contains an older catalog entry using `com.vickyleu.webview:webview:1.0.3`. Do not silently assume these are the same coordinate. Before publishing the integrated build, inspect Maven Central / existing local credentials and decide which coordinate is the intended canonical coordinate. Update `KMMCompose` explicitly to the actually published coordinate/version and prove resolution with Gradle.

Upstream currently uses `VERSION_NAME=2.0.4` under its own namespace. For the integrated fork, use a new, unpublished fork version greater than the current fork/upstream lineage (for example `2.0.5` if available), never overwrite an already-published Maven Central version.

## Required validation before PR to main

1. Clean Gradle build succeeds for the library.
2. Android target compiles.
3. iOS source sets compile where the machine/toolchain permits.
4. Desktop source set compiles where supported.
5. Maven local publication succeeds.
6. Inspect the generated POM/module metadata and verify the intended group/artifact/version.
7. `KMMCompose` resolves the newly published/local Maven artifact and compiles.
8. Build the Android App artifact used by the team.
9. Test at minimum:
   - normal WebView page load/navigation;
   - JS bridge sync and async calls;
   - JS alert/event bridge;
   - Android fullscreen video enter/exit/rotation;
   - Xiaomi/HyperOS fullscreen regression if a device is available;
   - WebView destroy/recreate;
   - local file/content loading;
   - iOS basic WebView path if available.
10. Review `git diff main...sync/upstream-preserve-features` for accidental API deletions.
11. Only then create/update the PR to `main`; do not merge automatically.
