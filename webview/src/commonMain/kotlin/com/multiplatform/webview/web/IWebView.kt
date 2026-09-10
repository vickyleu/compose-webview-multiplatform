package com.multiplatform.webview.web

import com.multiplatform.webview.jsbridge.ConsoleBridge
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.util.KLogger
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.ExperimentalResourceApi
import webview.generated.resources.Res

expect class NativeWebView

/** Interface for a platform WebView. */
interface IWebView {
    val webView: NativeWebView

    val scope: CoroutineScope

    val webViewJsBridge: WebViewJsBridge?

    /** Upstream console bridge. Default keeps older platform implementations/source users compatible. */
    val consoleBridge: ConsoleBridge?
        get() = null

    fun canGoBack(): Boolean

    fun canGoForward(): Boolean

    fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String> = emptyMap(),
    )

    /**
     * Loads HTML. Headers are retained for fork API compatibility; platforms that cannot apply
     * request headers to an in-memory HTML load may ignore them.
     */
    fun loadHtml(
        html: String? = null,
        baseUrl: String? = null,
        mimeType: String? = "text/html",
        encoding: String? = "utf-8",
        historyUrl: String? = null,
        additionalHttpHeaders: Map<String, String> = emptyMap(),
    )

    suspend fun loadContent(content: WebContent) {
        when (content) {
            is WebContent.Url -> loadUrl(content.url, content.additionalHttpHeaders)
            is WebContent.Data ->
                loadHtml(
                    content.data,
                    content.baseUrl,
                    content.mimeType,
                    content.encoding,
                    content.historyUrl,
                    content.additionalHttpHeaders,
                )
            is WebContent.File ->
                loadHtmlFile(
                    content.fileName,
                    content.readType,
                    content.additionalHttpHeaders,
                )
            is WebContent.Post ->
                postUrl(content.url, content.postData, content.additionalHttpHeaders)
            is WebContent.NavigatorOnly -> Unit
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadRawHtmlFile(fileName: String) {
        val html = Res.readBytes(fileName).decodeToString().trimIndent()
        loadHtml(html, encoding = "utf-8")
    }

    suspend fun loadHtmlFile(
        fileName: String,
        readType: WebViewFileReadType = WebViewFileReadType.ASSET_RESOURCES,
        additionalHttpHeaders: Map<String, String> = emptyMap(),
    )

    fun postUrl(
        url: String,
        postData: ByteArray,
        additionalHttpHeaders: Map<String, String> = emptyMap(),
    )

    fun goBack()

    fun goForward()

    fun reload()

    fun stopLoading()

    /** Fork extension retained for deterministic resource cleanup. */
    fun destroy()

    fun evaluateJavaScript(
        script: String,
        callback: ((String) -> Unit)? = null,
    )

    /** Injects the common JS bridge bootstrap into the current page. */
    fun injectJsBridge() {
        val bridge = webViewJsBridge ?: return
        val jsBridgeName = bridge.jsBridgeName
        KLogger.d { "IWebView injectJsBridge" }
        val initJs =
            """
            if (typeof window.$jsBridgeName === 'undefined') {
                window.$jsBridgeName = {
                    callbacks: {},
                    callbackId: 0,
                    callNative: function (methodName, params, callback) {
                        var message = {
                            methodName: methodName,
                            params: params,
                            callbackId: callback ? window.$jsBridgeName.callbackId++ : -1
                        };
                        if (callback) {
                            window.$jsBridgeName.callbacks[message.callbackId] = callback;
                        }
                        window.$jsBridgeName.postMessage(JSON.stringify(message));
                    },
                    onCallback: function (callbackId, data) {
                        var callback = window.$jsBridgeName.callbacks[callbackId];
                        if (callback) {
                            callback(data);
                            delete window.$jsBridgeName.callbacks[callbackId];
                        }
                    }
                };
            }
            """.trimIndent()
        evaluateJavaScript(initJs)
        // Fork extension: expose registered native handlers as ergonomic JS methods.
        bridge.registerDelegateMethod()
    }

    fun initJsBridge(webViewJsBridge: WebViewJsBridge)

    fun initWebView() {
        webViewJsBridge?.let { initJsBridge(it) }
    }

    fun saveState(): WebViewBundle?

    fun scrollOffset(): Pair<Int, Int>
}
