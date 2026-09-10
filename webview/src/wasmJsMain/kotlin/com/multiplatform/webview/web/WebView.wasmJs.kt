package com.multiplatform.webview.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.multiplatform.webview.jsbridge.ConsoleBridge
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import kotlinx.browser.document
import kotlinx.coroutines.delay
import org.w3c.dom.Element
import org.w3c.dom.HTMLIFrameElement

actual class WebViewFactoryParam {
    var container: Element = document.body!!
    var existingElement: HTMLIFrameElement? = null
}

actual class PlatformWebViewParams

actual fun defaultWebViewFactory(param: WebViewFactoryParam): NativeWebView {
    val iframe = param.existingElement ?: document.createElement("iframe") as HTMLIFrameElement
    iframe.style.apply {
        border = "none"
        width = "100%"
        height = "100%"
    }
    return NativeWebView(iframe)
}

private fun createWebViewWithSettings(
    param: WebViewFactoryParam,
    settings: com.multiplatform.webview.setting.WebSettings,
): NativeWebView {
    val iframe = param.existingElement ?: document.createElement("iframe") as HTMLIFrameElement
    val wasmSettings = settings.wasmJSWebSettings
    iframe.style.apply {
        width = "100%"
        height = "100%"
        border = if (wasmSettings.showBorder) wasmSettings.borderStyle else "none"
        wasmSettings.customContainerStyle?.let { cssText += "; $it" }
    }
    if (wasmSettings.enableSandbox) iframe.setAttribute("sandbox", wasmSettings.sandboxPermissions)
    if (wasmSettings.allowFullscreen) iframe.setAttribute("allowfullscreen", "true")
    if (wasmSettings.enableConsoleLogging) consoleLogJs("WasmJS WebView created")
    return NativeWebView(iframe)
}

@Composable
actual fun ActualWebView(
    state: WebViewState,
    modifier: Modifier,
    captureBackPresses: Boolean,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    consoleBridge: ConsoleBridge?,
    onCreated: (NativeWebView) -> Unit,
    onDispose: (NativeWebView) -> Unit,
    platformWebViewParams: PlatformWebViewParams?,
    factory: (WebViewFactoryParam) -> NativeWebView,
) {
    val scope = rememberCoroutineScope()
    val htmlNavigator = rememberHtmlViewNavigator()
    val htmlViewState = remember { HtmlViewState() }

    LaunchedEffect(navigator, htmlNavigator) {
        while (true) {
            navigator.canGoBack = htmlNavigator.canGoBack
            navigator.canGoForward = htmlNavigator.canGoForward
            delay(100)
        }
    }

    LaunchedEffect(htmlViewState.lastLoadedUrl, htmlViewState.pageTitle, htmlViewState.loadingState) {
        state.lastLoadedUrl = htmlViewState.lastLoadedUrl
        state.pageTitle = htmlViewState.pageTitle
        state.loadingState = when (val loading = htmlViewState.loadingState) {
            HtmlLoadingState.Loading -> LoadingState.Loading(0f)
            is HtmlLoadingState.Finished -> if (loading.isError) {
                LoadingState.ErrorLoading(loading.errorMessage ?: "Failed to load content")
            } else {
                LoadingState.Finished
            }
            HtmlLoadingState.Initializing -> LoadingState.Initializing
        }
    }

    HtmlView(
        state = htmlViewState,
        modifier = modifier,
        navigator = htmlNavigator,
        onCreated = { element ->
            val param = WebViewFactoryParam().apply { existingElement = element as? HTMLIFrameElement }
            val native =
                if (state.webSettings.wasmJSWebSettings.let {
                        it.backgroundColor != null ||
                            it.showBorder ||
                            it.enableSandbox ||
                            it.customContainerStyle != null ||
                            it.enableConsoleLogging
                    }
                ) {
                    createWebViewWithSettings(param, state.webSettings)
                } else {
                    factory(param)
                }
            val wrapper = WasmJsWebView(element, native, scope, webViewJsBridge)
            state.webView = wrapper
            webViewJsBridge?.webView = wrapper
            wrapper.initWebView()
            onCreated(native)
        },
        onDispose = {
            state.webView?.let { wrapper ->
                onDispose(wrapper.webView)
                wrapper.destroy()
            }
            state.webView = null
        },
    )
}
