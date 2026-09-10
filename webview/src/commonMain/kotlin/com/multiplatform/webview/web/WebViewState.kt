package com.multiplatform.webview.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.multiplatform.webview.cookie.CookieManager
import com.multiplatform.webview.cookie.WebViewCookieManager
import com.multiplatform.webview.setting.WebSettings
import com.multiplatform.webview.util.KLogger
import com.multiplatform.webview.util.getPlatform
import com.multiplatform.webview.util.isZero

/** A state holder for the WebView. */
class WebViewState(
    webContent: WebContent,
) {
    var lastLoadedUrl: String? by mutableStateOf(null)
        internal set

    var content: WebContent by mutableStateOf(webContent)

    var loadingState: LoadingState by mutableStateOf(LoadingState.Initializing)
        internal set

    val isLoading: Boolean
        get() = loadingState !is LoadingState.Finished

    /** Fork-specific fullscreen state retained for source/behavior compatibility. */
    var fullscreenState by mutableStateOf(false)
        internal set

    val isFullScreen: Boolean
        get() = fullscreenState

    var pageTitle: String? by mutableStateOf(null)
        internal set

    /** Errors captured in the current page load, including subresources. */
    val errorsForCurrentRequest: SnapshotStateList<WebViewError> = mutableStateListOf()

    val webSettings: WebSettings by mutableStateOf(WebSettings())

    internal var webView by mutableStateOf<IWebView?>(null)

    val nativeWebView get() = webView?.webView ?: error("WebView is not initialized")

    var viewState: WebViewBundle? = null
        internal set

    var scrollOffset: Pair<Int, Int> = 0 to 0
        internal set

    val cookieManager: CookieManager by mutableStateOf(WebViewCookieManager())
}

/**
 * Creates a remembered WebView state.
 *
 * [callback] intentionally remains the last lambda to preserve the fork's historic trailing-lambda
 * source compatibility. Upstream-style settings are available through the named [extraSettings]
 * parameter or [rememberWebViewStateWithSettings].
 */
@Composable
fun rememberWebViewState(
    url: String,
    additionalHttpHeaders: Map<String, String> = emptyMap(),
    extraSettings: WebSettings.() -> Unit = {},
    callback: (WebViewState) -> Unit = {},
): WebViewState =
    remember {
        WebViewState(
            WebContent.Url(
                url = url,
                additionalHttpHeaders = additionalHttpHeaders,
            ),
        )
    }.apply {
        content = WebContent.Url(url = url, additionalHttpHeaders = additionalHttpHeaders)
        extraSettings(webSettings)
        callback(this)
    }

/** Ergonomic upstream-style settings helper without changing the fork's trailing callback semantics. */
@Composable
fun rememberWebViewStateWithSettings(
    url: String,
    additionalHttpHeaders: Map<String, String> = emptyMap(),
    extraSettings: WebSettings.() -> Unit,
): WebViewState =
    rememberWebViewState(
        url = url,
        additionalHttpHeaders = additionalHttpHeaders,
        extraSettings = extraSettings,
    )

@Composable
fun rememberSaveableWebViewState(
    url: String,
    additionalHttpHeaders: Map<String, String> = emptyMap(),
): WebViewState =
    if (getPlatform().isDesktop()) {
        rememberWebViewState(url, additionalHttpHeaders)
    } else {
        rememberSaveable(saver = WebStateSaver) {
            WebViewState(WebContent.NavigatorOnly)
        }
    }

val WebStateSaver: Saver<WebViewState, Any> =
    run {
        val pageTitleKey = "pagetitle"
        val lastLoadedUrlKey = "lastloaded"
        val stateBundleKey = "bundle"
        val scrollOffsetKey = "scrollOffset"

        mapSaver(
            save = {
                val viewState = it.webView?.saveState()
                KLogger.info {
                    "WebViewStateSaver Save: ${it.pageTitle}, ${it.lastLoadedUrl}, ${it.webView?.scrollOffset()}, $viewState"
                }
                mapOf(
                    pageTitleKey to it.pageTitle,
                    lastLoadedUrlKey to it.lastLoadedUrl,
                    stateBundleKey to viewState,
                    scrollOffsetKey to it.webView?.scrollOffset(),
                )
            },
            restore = {
                KLogger.info {
                    "WebViewStateSaver Restore: ${it[pageTitleKey]}, ${it[lastLoadedUrlKey]}, ${it[scrollOffsetKey]}, ${it[stateBundleKey]}"
                }
                @Suppress("UNCHECKED_CAST")
                val scrollOffset = it[scrollOffsetKey] as Pair<Int, Int>? ?: (0 to 0)
                val bundle = it[stateBundleKey] as WebViewBundle?
                WebViewState(WebContent.NavigatorOnly).apply {
                    pageTitle = it[pageTitleKey] as String?
                    lastLoadedUrl = it[lastLoadedUrlKey] as String?
                    bundle?.let { saved -> viewState = saved }
                    if (!scrollOffset.isZero()) {
                        this.scrollOffset = scrollOffset
                    }
                }
            },
        )
    }

@Composable
fun rememberWebViewStateWithHTMLData(
    data: String,
    baseUrl: String? = null,
    encoding: String = "utf-8",
    mimeType: String? = null,
    historyUrl: String? = null,
    additionalHttpHeaders: Map<String, String> = emptyMap(),
    extraSettings: WebSettings.() -> Unit = {},
    callback: (WebViewState) -> Unit = {},
): WebViewState =
    remember {
        WebViewState(
            WebContent.Data(
                data,
                baseUrl,
                encoding,
                mimeType,
                historyUrl,
                additionalHttpHeaders,
            ),
        )
    }.apply {
        content =
            WebContent.Data(
                data,
                baseUrl,
                encoding,
                mimeType,
                historyUrl,
                additionalHttpHeaders,
            )
        extraSettings(webSettings)
        callback(this)
    }

/**
 * Creates a remembered state for an HTML file.
 * [readType] brings in upstream Asset/Compose Resource support while its default preserves the
 * historic one-argument fork API.
 */
@Composable
fun rememberWebViewStateWithHTMLFile(
    fileName: String,
    readType: WebViewFileReadType = WebViewFileReadType.ASSET_RESOURCES,
    additionalHttpHeaders: Map<String, String> = emptyMap(),
    extraSettings: WebSettings.() -> Unit = {},
    callback: (WebViewState) -> Unit = {},
): WebViewState =
    remember {
        WebViewState(WebContent.File(fileName, readType, additionalHttpHeaders))
    }.apply {
        content = WebContent.File(fileName, readType, additionalHttpHeaders)
        extraSettings(webSettings)
        callback(this)
    }
