package com.multiplatform.webview.web

import com.multiplatform.webview.jsbridge.WKJsConsoleMessageHandler
import com.multiplatform.webview.jsbridge.WKJsMessageHandler
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.setting.PlatformWebSettings
import com.multiplatform.webview.util.KLogger
import com.multiplatform.webview.util.getPlatformVersionDouble
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.setValue
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.darwin.NSObject
import platform.darwin.NSObjectMeta

/**
 * Created By Kevin Zou On 2023/9/5
 */

actual typealias NativeWebView = WKWebView

/**
 * iOS implementation of [IWebView]
 */
class IOSWebView(
    override val webView: WKWebView,
    override val scope: CoroutineScope,
    override val webViewJsBridge: WebViewJsBridge?,
) : IWebView {
    init {
        initWebView()
    }

    override fun canGoBack() = webView.canGoBack

    override fun canGoForward() = webView.canGoForward

    override fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String>,
    ) {
        KLogger.d { "Load url: $url" }
        val request =
            NSMutableURLRequest.requestWithURL(
                URL = NSURL(string = url),
            )
        additionalHttpHeaders.all { (key, value) ->
            request.setValue(
                value = value,
                forHTTPHeaderField = key,
            )
            true
        }
        webView.loadRequest(
            request = request,
        )
    }

    override fun loadHtml(
        html: String?,
        baseUrl: String?,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) {
        if (html == null) {
            KLogger.e {
                "LoadHtml: html is null"
            }
            return
        }
        webView.loadHTMLString(
            string = html,
            baseURL = baseUrl?.let { NSURL.URLWithString(it) },
        )
    }

    override suspend fun loadHtmlFile(fileName: String) {
        val res = NSBundle.mainBundle.resourcePath + "/compose-resources/assets/" + fileName
        val url = NSURL.fileURLWithPath(res)
        webView.loadFileURL(url, url)
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun postUrl(
        url: String,
        postData: ByteArray,
    ) {
        val request =
            NSMutableURLRequest(
                uRL = NSURL(string = url),
            )
        request.apply {
            HTTPMethod = "POST"
            HTTPBody =
                memScoped {
                    NSData.create(bytes = allocArrayOf(postData), length = postData.size.toULong())
                }
        }
        webView.loadRequest(request = request)
    }

    override fun goBack() {
        webView.goBack()
    }

    override fun goForward() {
        webView.goForward()
    }

    override fun reload() {
        webView.reload()
    }

    override fun stopLoading() {
        webView.stopLoading()
    }

    override fun evaluateJavaScript(
        script: String,
        callback: ((String) -> Unit)?,
    ) {
        val wrapScript = """
                (function() {
                    $script
                })();
            """.trimIndent()
//        val wrapScript = script
        /*wrapScript.apply {
            println("evaluateJavaScript postWebviewDelegateMethod:${this}")
        }*/
        webView.evaluateJavaScript(wrapScript) Call@{ result, error ->
            if (error != null) {
                KLogger.e { "evaluateJavaScript error: $error" }
            }
            if (callback == null) return@Call
            if (error != null) {
                callback.invoke(error.localizedDescription())
            } else {
                KLogger.info { "evaluateJavaScript result: $result" }
                callback.invoke(result?.toString() ?: "")
            }
        }
    }

    override fun injectJsBridge() {
        if (webViewJsBridge == null) return
        KLogger.info {
            "iOS WebView injectJsBridge"
        }
        super.injectJsBridge()
        val callIOS =
            """
            window.${webViewJsBridge.jsBridgeName}.postMessage = function (message) {
                    window.webkit.messageHandlers.iosJsBridge.postMessage(message);
                };
            """.trimIndent()
        evaluateJavaScript(callIOS)
    }

    override fun initJsBridge(webViewJsBridge: WebViewJsBridge) {
        KLogger.info { "injectBridge" }
        val jsMessageHandler = WKJsMessageHandler(webViewJsBridge)
        webView.configuration.userContentController.apply {
            addScriptMessageHandler(jsMessageHandler, "iosJsBridge")
        }
    }

    override fun saveState(): WebViewBundle? {
        // iOS 15- does not support saving state
        if (getPlatformVersionDouble() < 15.0) {
            return null
        }
        val data = webView.interactionState as NSData?
        return data
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun scrollOffset(): Pair<Int, Int> {
        val offset = webView.scrollView.contentOffset
        offset.useContents {
            return Pair(x.toInt(), y.toInt())
        }
    }

    fun setupSettings(settings: PlatformWebSettings.IOSWebSettings) {
        settings.isOpenConsoleLog = true

        /**
         * // inject JS to capture console.log output and send to iOS
         * let source = "function captureLog(msg) { window.webkit.messageHandlers.logHandler.postMessage(msg); } window.console.log = captureLog;"
         * let script = WKUserScript(source: source, injectionTime: .atDocumentEnd, forMainFrameOnly: false)
         * webView.configuration.userContentController.addUserScript(script)
         * // register the bridge script that listens for the output
         * webView.configuration.userContentController.add(self, name: "logHandler")
         */
        val jsMessageHandler = WKJsConsoleMessageHandler()
        webView.configuration.userContentController.apply {
            addScriptMessageHandler(jsMessageHandler,"consoleLog")
            println("注入consoleLog处理器")
            val logScript = WKUserScript(
                """
                (function() {
                    document.addEventListener('click', function(event) {
                         window.webkit.messageHandlers.consoleLog.postMessage('Element clicked: ' + event.target.tagName);
                    });
                    function captureLog(...args) { 
                        window.webkit.messageHandlers.consoleLog.postMessage(args.join(' '));
                    } 
                    window.console.log = captureLog;
                })();
                """.trimIndent(),
                WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd,
                true)
            addUserScript(logScript)
            println("替换console.log方法实现,映射方法到consoleLog")
        }
    }

    private class BundleMarker : NSObject() {
        companion object : NSObjectMeta()
    }
}
