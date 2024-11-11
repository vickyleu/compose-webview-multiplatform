package com.multiplatform.webview.web

import com.multiplatform.webview.jsbridge.WKJsConsoleMessageHandler
import com.multiplatform.webview.jsbridge.WKJsMessageHandler
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.util.KLogger
import com.multiplatform.webview.util.getPlatformVersionDouble
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
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
        additionalHttpHeaders: Map<String, String>,
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

    override suspend fun loadHtmlFile(
        fileName: String,
        additionalHttpHeaders: Map<String, String>
    ) {
        val res = NSBundle.mainBundle.resourcePath + "/compose-resources/assets/" + fileName
        val url = NSURL.fileURLWithPath(res)
        webView.loadFileURL(url, url)
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun postUrl(
        url: String,
        postData: ByteArray, additionalHttpHeaders: Map<String, String>
    ) {
        val request =
            NSMutableURLRequest(
                uRL = NSURL(string = url),
            )
        val req = request
        req.HTTPMethod = "POST"

        req.HTTPBody =  postData.usePinned {
            NSData.create(bytes = it.addressOf(0), length = postData.size.convert())
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

    override fun destroy() {
        // 执行 JavaScript 脚本来暂停所有音视频元素
        val pauseScript = """
        var videos = document.querySelectorAll('video');
        var audios = document.querySelectorAll('audio');
        videos.forEach(function(video) {
            video.pause();
        });
        audios.forEach(function(audio) {
            audio.pause();
        });
    """.trimIndent()
        webView.evaluateJavaScript(pauseScript) { _, _ ->
            // 确保所有音视频都已暂停后再执行清理操作
            // 停止加载并移除 WKWebView
            webView.stopLoading()
            webView.configuration.userContentController.removeAllUserScripts()
            webView.configuration.userContentController.removeAllScriptMessageHandlers()
            webView.removeFromSuperview()
        }
    }

    override fun evaluateJavaScript(
        script: String,
        callback: ((String) -> Unit)?,
    ) {
        webView.evaluateJavaScript("""(function() {
            $script
        })()""".trimIndent()) Call@{ result, error ->
            if (error != null) {
                KLogger.e { "evaluateJavaScript error: $error  script:  $script" }
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
        val jsConsoleHandler = WKJsConsoleMessageHandler()
        val jsMessageHandler = WKJsMessageHandler(webViewJsBridge)

        val ucc = webView.configuration.userContentController
        ucc.addScriptMessageHandler(jsMessageHandler, "iosJsBridge")
        ucc.addScriptMessageHandler(jsConsoleHandler, "consoleLog")
        println("注入consoleLog处理器")
        val logScript = WKUserScript(
            """(function() {
                    var lastTouchEnd = 0;
                    document.documentElement.addEventListener('touchend', function(event) {
                        var now = (new Date()).getTime();
                        if (now - lastTouchEnd <= 300) {
                            event.preventDefault();
                        }
                        lastTouchEnd = now;
                    }, false);
                    
                    function captureLog(...args) { 
                        window.webkit.messageHandlers.consoleLog.postMessage(args.join(' '));
                    };
                    window.console.log = captureLog;
                })();""".trimIndent(),
            WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd,
            true
        )
        ucc.addUserScript(logScript)
        println("替换console.log方法实现,映射方法到consoleLog")

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

    private class BundleMarker : NSObject() {
        companion object : NSObjectMeta()
    }
}
