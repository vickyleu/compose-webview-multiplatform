package com.multiplatform.webview.web

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.multiplatform.webview.jsbridge.ConsoleBridge
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.util.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

actual typealias NativeWebView = WebView

/** Android implementation of [IWebView]. */
class AndroidWebView(
    override val webView: WebView,
    override val scope: CoroutineScope,
    override val webViewJsBridge: WebViewJsBridge?,
    override val consoleBridge: ConsoleBridge? = null,
) : IWebView {
    init {
        initWebView()
    }

    override fun canGoBack() = webView.canGoBack()

    override fun canGoForward() = webView.canGoForward()

    override fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String>,
    ) {
        webView.loadUrl(url, additionalHttpHeaders)
    }

    override fun loadHtml(
        html: String?,
        baseUrl: String?,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
        additionalHttpHeaders: Map<String, String>,
    ) {
        if (html == null) return
        // Android's loadDataWithBaseURL does not expose request headers. Keep the fork parameter
        // for source compatibility rather than pretending the headers can be applied here.
        webView.loadDataWithBaseURL(baseUrl, html, mimeType, encoding, historyUrl)
    }

    override suspend fun loadHtmlFile(
        fileName: String,
        readType: WebViewFileReadType,
        additionalHttpHeaders: Map<String, String>,
    ) {
        KLogger.d { "loadHtmlFile: $fileName, readType: $readType" }
        try {
            val url =
                when (readType) {
                    WebViewFileReadType.ASSET_RESOURCES -> "file:///android_asset/$fileName"
                    WebViewFileReadType.COMPOSE_RESOURCE_FILES -> fileName
                }
            webView.loadUrl(url, additionalHttpHeaders)
        } catch (e: Exception) {
            KLogger.e(e) { "Error loading HTML file: $fileName" }
            val errorHtml =
                "<html><body><h1>Error</h1><p>Could not load file: $fileName. Error: ${e.message}</p></body></html>"
            webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
        }
    }

    override fun postUrl(
        url: String,
        postData: ByteArray,
        additionalHttpHeaders: Map<String, String>,
    ) {
        // Android WebView.postUrl has no headers argument; retained for fork source compatibility.
        webView.postUrl(url, postData)
    }

    override fun goBack() = webView.goBack()

    override fun goForward() = webView.goForward()

    override fun reload() = webView.reload()

    override fun stopLoading() = webView.stopLoading()

    override fun destroy() {
        webView.stopLoading()
        // The actual Android bridge is registered under this fixed interface name.
        webView.removeJavascriptInterface("androidJsBridge")
        // Also remove the historical dynamic name in case an older consumer registered it.
        webViewJsBridge?.let { webView.removeJavascriptInterface(it.jsBridgeName) }
        webView.removeAllViews()
        webView.destroy()
    }

    override fun evaluateJavaScript(
        script: String,
        callback: ((String) -> Unit)?,
    ) {
        val androidScript = "javascript:$script"
        KLogger.d { "evaluateJavaScript: $androidScript" }
        // Keep upstream's direct call so headless/non-attached WebViews do not depend on View.post().
        webView.evaluateJavascript(androidScript, callback)
    }

    override fun injectJsBridge() {
        if (webViewJsBridge == null) return
        super.injectJsBridge()
        val callAndroid =
            """
            window.${webViewJsBridge.jsBridgeName}.postMessage = function (message) {
                window.androidJsBridge.call(message);
            };
            """.trimIndent()
        evaluateJavaScript(callAndroid)
    }

    override fun initJsBridge(webViewJsBridge: WebViewJsBridge) {
        webView.addJavascriptInterface(this, "androidJsBridge")
    }

    @Suppress("unused")
    @JavascriptInterface
    fun call(request: String) {
        KLogger.d { "call from JS: $request" }
        val message = Json.decodeFromString<JsMessage>(request)
        KLogger.d { "call from JS: $message" }
        webViewJsBridge?.dispatch(message)
    }

    @Suppress("unused")
    @JavascriptInterface
    fun callAndroid(
        id: Int,
        method: String,
        params: String,
    ) {
        KLogger.d { "callAndroid call from JS: $id, $method, $params" }
        webViewJsBridge?.dispatch(JsMessage(id, method, params))
    }

    override fun scrollOffset(): Pair<Int, Int> = Pair(webView.scrollX, webView.scrollY)

    override fun saveState(): WebViewBundle? {
        val bundle = WebViewBundle()
        return if (webView.saveState(bundle) != null) bundle else null
    }
}
