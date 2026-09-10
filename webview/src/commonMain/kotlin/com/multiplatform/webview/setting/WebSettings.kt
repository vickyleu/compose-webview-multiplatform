package com.multiplatform.webview.setting

import androidx.compose.ui.graphics.Color
import com.multiplatform.webview.util.KLogSeverity
import com.multiplatform.webview.util.KLogger

/** Web settings shared across platforms plus platform-specific configuration. */
class WebSettings {
    var isJavaScriptEnabled = true

    /** Historical fork-wide inspectability flag. */
    var isInspectable = true

    var customUserAgentString: String? = null

    var zoomLevel: Double = 1.0

    var supportZoom: Boolean = true

    var allowFileAccessFromFileURLs: Boolean = false

    var allowUniversalAccessFromFileURLs: Boolean = false

    var logSeverity: KLogSeverity = KLogSeverity.Info
        set(value) {
            field = value
            KLogger.setMinSeverity(value)
        }

    var backgroundColor = Color.Transparent

    /**
     * Historical fork API. Despite the name this is an SSL-error host allowlist,
     * not conventional certificate/public-key pinning.
     */
    var sslPiningHosts: List<String> = emptyList()

    val androidWebSettings = PlatformWebSettings.AndroidWebSettings()

    val desktopWebSettings = PlatformWebSettings.DesktopWebSettings()

    val iOSWebSettings = PlatformWebSettings.IOSWebSettings()

    /** Current upstream WasmJS settings. */
    val wasmJSWebSettings = PlatformWebSettings.WasmJSWebSettings()

    /** Historical fork property retained for source compatibility. */
    @Deprecated("Use wasmJSWebSettings")
    val wasmWebSettings = PlatformWebSettings.WasmWebSettings()
}
