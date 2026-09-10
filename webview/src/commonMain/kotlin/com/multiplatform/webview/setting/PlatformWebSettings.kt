package com.multiplatform.webview.setting

import androidx.compose.ui.graphics.Color

sealed class PlatformWebSettings {
    data class AndroidWebSettings(
        var allowFileAccess: Boolean = false,
        var textZoom: Int = 100,
        var useWideViewPort: Boolean = false,
        var standardFontFamily: String = "sans-serif",
        var defaultFontSize: Int = 16,
        var loadsImagesAutomatically: Boolean = true,
        var isAlgorithmicDarkeningAllowed: Boolean = false,
        var safeBrowsingEnabled: Boolean = true,
        var domStorageEnabled: Boolean = false,
        var mediaPlaybackRequiresUserGesture: Boolean = true,
        /** Allow WebView protected-media permission requests (DRM). */
        var allowProtectedMedia: Boolean = false,
        /** Allow MIDI SysEx permission requests. */
        var allowMidiSysexMessages: Boolean = false,
        /** Hide Chromium's default video poster. */
        var hideDefaultVideoPoster: Boolean = false,
        /** Android View layer type. */
        var layerType: Int = LayerType.HARDWARE,
        /** Serve local content through WebViewAssetLoader rather than unrestricted file:// access. */
        var enableSandbox: Boolean = false,
        /** Virtual path handled by the sandbox asset loader. */
        var sandboxSubdomain: String = "/app/",
    ) : PlatformWebSettings() {
        object LayerType {
            const val NONE = 0
            const val SOFTWARE = 1
            const val HARDWARE = 2
        }
    }

    data class DesktopWebSettings(
        var offScreenRendering: Boolean = false,
        var transparent: Boolean = true,
        var disablePopupWindows: Boolean = false,
    ) : PlatformWebSettings()

    data class IOSWebSettings(
        var opaque: Boolean = false,
        var backgroundColor: Color? = null,
        var underPageBackgroundColor: Color? = null,
        var bounces: Boolean = true,
        var scrollEnabled: Boolean = true,
        var showHorizontalScrollIndicator: Boolean = true,
        var showVerticalScrollIndicator: Boolean = true,
        /** Fork-specific console capture switch. */
        var isOpenConsoleLog: Boolean = false,
        /** Fork-specific fine-grained autoplay policy. */
        var mediaTypesRequiringUserActionForPlayback: MediaTypesRequiringUserActionForPlayback =
            MediaTypesRequiringUserActionForPlayback.NONE,
        /** Upstream compatibility switch. Fine-grained policy takes precedence when explicitly set. */
        var mediaPlaybackRequiresUserGesture: Boolean = true,
        /** Upstream per-platform inspectability setting. */
        var isInspectable: Boolean = false,
    ) : PlatformWebSettings()

    enum class MediaTypesRequiringUserActionForPlayback {
        ALL,
        AUDIO,
        VIDEO,
        NONE,
    }

    /** Current upstream WasmJS settings. */
    data class WasmJSWebSettings(
        var backgroundColor: Color? = null,
        var showBorder: Boolean = false,
        var borderStyle: String = "1px solid #ccc",
        var enableSandbox: Boolean = false,
        var sandboxPermissions: String = "allow-scripts allow-same-origin allow-forms",
        var allowFullscreen: Boolean = true,
        var customContainerStyle: String? = null,
        var enableConsoleLogging: Boolean = false,
    ) : PlatformWebSettings()

    /**
     * Historical fork Wasm settings kept so existing source still compiles.
     * The fork did not yet ship a Wasm target; new code should use [WasmJSWebSettings].
     */
    @Deprecated("Use WasmJSWebSettings")
    data class WasmWebSettings(
        var offScreenRendering: Boolean = false,
        var transparent: Boolean = true,
        var disablePopupWindows: Boolean = false,
    ) : PlatformWebSettings()
}
