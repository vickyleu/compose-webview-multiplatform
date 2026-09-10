package com.multiplatform.webview.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.multiplatform.webview.jsbridge.ConsoleBridge
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.util.KLogger
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge

/**
 * Provides a basic WebView composable.
 *
 * The original fork parameter order is intentionally retained; new upstream parameters are
 * appended so positional call sites do not silently change meaning.
 */
@Composable
fun WebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    captureBackPresses: Boolean = true,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    webViewJsBridge: WebViewJsBridge? = null,
    onCreated: () -> Unit = {},
    onDispose: () -> Unit = {},
    consoleBridge: ConsoleBridge? = null,
    platformWebViewParams: PlatformWebViewParams? = null,
) {
    WebView(
        state = state,
        modifier = modifier,
        captureBackPresses = captureBackPresses,
        navigator = navigator,
        webViewJsBridge = webViewJsBridge,
        onCreated = { _ -> onCreated() },
        onDispose = { _ -> onDispose() },
        factory = null,
        consoleBridge = consoleBridge,
        platformWebViewParams = platformWebViewParams,
    )
}

/**
 * Provides a WebView with access to the native view/factory.
 * New upstream parameters remain after the historic fork [factory] argument for positional
 * source compatibility.
 */
@Composable
fun WebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    captureBackPresses: Boolean = true,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    webViewJsBridge: WebViewJsBridge? = null,
    onCreated: (NativeWebView) -> Unit = {},
    onDispose: (NativeWebView) -> Unit = {},
    factory: ((WebViewFactoryParam) -> NativeWebView)? = null,
    consoleBridge: ConsoleBridge? = null,
    platformWebViewParams: PlatformWebViewParams? = null,
) {
    val webView = state.webView

    webView?.let { wv ->
        LaunchedEffect(wv, navigator) {
            with(navigator) {
                KLogger.d { "wv.handleNavigationEvents()" }
                wv.handleNavigationEvents()
            }
        }

        // Current upstream implementations can load content through the common IWebView path.
        LaunchedEffect(wv, state) {
            snapshotFlow { state.content }.collect { content ->
                wv.loadContent(content)
            }
        }

        if (webViewJsBridge != null) {
            LaunchedEffect(wv, state) {
                val loadingStateFlow =
                    snapshotFlow { state.loadingState }.filter { it is LoadingState.Finished }
                val lastLoadedUrlFlow =
                    snapshotFlow { state.lastLoadedUrl }.filter { !it.isNullOrEmpty() }

                merge(loadingStateFlow, lastLoadedUrlFlow).collect {
                    if (state.loadingState is LoadingState.Finished) {
                        // IWebView.injectJsBridge() also restores the fork's generated delegate methods.
                        wv.injectJsBridge()
                    }
                }
            }
        }
    }

    ActualWebView(
        state = state,
        modifier = modifier,
        captureBackPresses = captureBackPresses,
        navigator = navigator,
        webViewJsBridge = webViewJsBridge,
        consoleBridge = consoleBridge,
        onCreated = onCreated,
        onDispose = onDispose,
        platformWebViewParams = platformWebViewParams,
        factory = factory ?: ::defaultWebViewFactory,
    )

    DisposableEffect(Unit) {
        onDispose {
            KLogger.d { "WebView DisposableEffect" }
            webViewJsBridge?.clear()
        }
    }
}

/** Platform-specific parameters passed to the native WebView factory. */
expect class WebViewFactoryParam

/** Optional platform-specific WebView client/composable parameters. */
expect class PlatformWebViewParams

expect fun defaultWebViewFactory(param: WebViewFactoryParam): NativeWebView

@Composable
expect fun ActualWebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    captureBackPresses: Boolean = true,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    webViewJsBridge: WebViewJsBridge? = null,
    consoleBridge: ConsoleBridge? = null,
    onCreated: (NativeWebView) -> Unit = {},
    onDispose: (NativeWebView) -> Unit = {},
    platformWebViewParams: PlatformWebViewParams? = null,
    factory: (WebViewFactoryParam) -> NativeWebView = ::defaultWebViewFactory,
)
