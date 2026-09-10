package com.multiplatform.webview.web

import com.multiplatform.webview.jsbridge.ConsoleBridge
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.Element
import org.w3c.dom.HTMLIFrameElement
import org.w3c.dom.MessageEvent
import org.w3c.dom.events.Event

actual class NativeWebView(
    val element: Element,
)

class WasmJsWebView(
    private val element: Element,
    override val webView: NativeWebView,
    override val scope: CoroutineScope,
    override val webViewJsBridge: WebViewJsBridge?,
) : IWebView {
    private var bridgeMessageHandler: ((Event) -> Unit)? = null

    override fun canGoBack(): Boolean = runCatching { checkCanGoBackJs(element) }.getOrDefault(false)

    override fun canGoForward(): Boolean = runCatching { checkCanGoForwardJs(element) }.getOrDefault(false)

    override fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String>,
    ) {
        // Browser iframes cannot attach arbitrary request headers. Keep the common API but do not
        // imply that headers are applied on WasmJS.
        webViewJsBridge?.let(::ensureBridgeMessageHandler)
        runCatching { setUrlJs(element, url) }
        if (webViewJsBridge != null) {
            scope.launch {
                delay(500)
                injectJsBridge()
            }
        }
    }

    override fun loadHtml(
        html: String?,
        baseUrl: String?,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
        additionalHttpHeaders: Map<String, String>,
    ) {
        runCatching {
            if (html != null) {
                val content =
                    if (webViewJsBridge != null) {
                        ensureBridgeMessageHandler(webViewJsBridge)
                        injectBridgeIntoHtml(html, webViewJsBridge.jsBridgeName)
                    } else {
                        html
                    }
                setHtmlContentJs(element, content)
            }
        }
    }

    override suspend fun loadHtmlFile(
        fileName: String,
        readType: WebViewFileReadType,
        additionalHttpHeaders: Map<String, String>,
    ) {
        try {
            webViewJsBridge?.let(::ensureBridgeMessageHandler)
            val url =
                when (readType) {
                    WebViewFileReadType.ASSET_RESOURCES -> "assets/$fileName"
                    WebViewFileReadType.COMPOSE_RESOURCE_FILES -> fileName
                }
            setUrlJs(element, url)
            if (webViewJsBridge != null) {
                scope.launch {
                    delay(1000)
                    injectJsBridge()
                }
            }
        } catch (t: Throwable) {
            loadHtml(
                "<html><body><h2>Failed to load file: $fileName</h2><p>${t.message}</p></body></html>",
            )
        }
    }

    override fun postUrl(
        url: String,
        postData: ByteArray,
        additionalHttpHeaders: Map<String, String>,
    ) {
        // Native iframe navigation does not expose an equivalent of Android postUrl.
        // Preserve upstream behavior and navigate to the target URL rather than fabricating a POST.
        loadUrl(url, additionalHttpHeaders)
    }

    override fun goBack() {
        runCatching { navigateBackJs(element) }
    }

    override fun goForward() {
        runCatching { navigateForwardJs(element) }
    }

    override fun reload() {
        runCatching { reloadJs(element) }
    }

    override fun stopLoading() {
        runCatching { stopLoadingJs(element) }
    }

    override fun destroy() {
        stopLoading()
        bridgeMessageHandler?.let { handler ->
            window.removeEventListener("message", handler)
        }
        bridgeMessageHandler = null
        element.parentNode?.removeChild(element)
    }

    override fun evaluateJavaScript(
        script: String,
        callback: ((String) -> Unit)?,
    ) {
        scope.launch {
            try {
                callback?.invoke(evaluateScriptJs(element, script))
            } catch (t: Throwable) {
                callback?.invoke("Error: ${t.message}")
            }
        }
    }

    override fun injectJsBridge() {
        val bridge = webViewJsBridge ?: return
        ensureBridgeMessageHandler(bridge)
        evaluateJavaScript(createJsBridgeScript(bridge.jsBridgeName, true))
    }

    override fun initJsBridge(webViewJsBridge: WebViewJsBridge) {
        ensureBridgeMessageHandler(webViewJsBridge)
    }

    override val consoleBridge: ConsoleBridge? = null

    override fun saveState(): WebViewBundle? = null

    override fun scrollOffset(): Pair<Int, Int> = 0 to 0

    private fun ensureBridgeMessageHandler(bridge: WebViewJsBridge) {
        if (bridgeMessageHandler != null) {
            bridge.webView = this
            return
        }

        val handler: (Event) -> Unit = { event ->
            val messageEvent = event as MessageEvent
            val iframe = element as? HTMLIFrameElement
            if (iframe != null && messageEvent.source == iframe.contentWindow && messageEvent.data != null) {
                runCatching {
                    val dataString = messageEvent.data.toString()
                    if (dataString.contains(bridge.jsBridgeName)) {
                        val action =
                            """action[=:][\s]*['\"](.*?)['\"]"""
                                .toRegex()
                                .find(dataString)
                                ?.groupValues
                                ?.get(1)
                        val params =
                            """params[=:][\s]*['\"](.*?)['\"]"""
                                .toRegex()
                                .find(dataString)
                                ?.groupValues
                                ?.get(1) ?: "{}"
                        val callbackId =
                            """callbackId[=:][\s]*(\d+)"""
                                .toRegex()
                                .find(dataString)
                                ?.groupValues
                                ?.get(1)
                                ?.toIntOrNull() ?: 0
                        if (action != null) {
                            bridge.dispatch(
                                JsMessage(
                                    callbackId = callbackId,
                                    methodName = action,
                                    params = params,
                                ),
                            )
                        }
                    }
                }
            }
        }

        window.addEventListener("message", handler)
        bridgeMessageHandler = handler
        bridge.webView = this
    }

    private fun injectBridgeIntoHtml(
        htmlContent: String,
        jsBridgeName: String,
    ): String {
        val script = "<script>${createJsBridgeScript(jsBridgeName)}</script>"
        return when {
            htmlContent.contains("<head>") -> htmlContent.replace("<head>", "<head>$script")
            "<head[^>]*>".toRegex().containsMatchIn(htmlContent) ->
                "<head[^>]*>".toRegex().replaceFirst(htmlContent) { "${it.value}$script" }
            "<body[^>]*>".toRegex().containsMatchIn(htmlContent) ->
                "<body[^>]*>".toRegex().replaceFirst(htmlContent) { "${it.value}$script" }
            else -> "$script$htmlContent"
        }
    }
}
