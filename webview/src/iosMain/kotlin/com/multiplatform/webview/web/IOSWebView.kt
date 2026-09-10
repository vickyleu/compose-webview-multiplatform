package com.multiplatform.webview.web

import com.multiplatform.webview.jsbridge.WKJsConsoleMessageHandler
import com.multiplatform.webview.jsbridge.WKJsMessageHandler
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.util.KLogger
import com.multiplatform.webview.util.getPlatformVersionDouble
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSArray
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.setValue
import platform.Foundation.stringByDeletingLastPathComponent
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.darwin.NSObject
import platform.darwin.NSObjectMeta

actual typealias NativeWebView = WKWebView

/** iOS implementation of [IWebView]. */
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

        if (url.startsWith("file://")) {
            val fileUrl = NSURL(string = url)
            if (fileUrl != null && fileUrl.isFileURL()) {
                val documentPaths =
                    NSSearchPathForDirectoriesInDomains(
                        NSDocumentDirectory,
                        NSUserDomainMask,
                        true,
                    ) as NSArray
                val readAccessUrl =
                    if (documentPaths.count > 0u) {
                        (documentPaths.objectAtIndex(0u) as? String)?.let { NSURL.fileURLWithPath(it) }
                    } else {
                        null
                    }
                if (readAccessUrl != null) {
                    webView.loadFileURL(fileUrl, readAccessUrl)
                    return
                }
            }
        }

        val request = NSMutableURLRequest.requestWithURL(NSURL(string = url))
        additionalHttpHeaders.forEach { (key, value) ->
            request.setValue(value, forHTTPHeaderField = key)
        }
        webView.loadRequest(request)
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
            KLogger.e { "LoadHtml: html is null" }
            return
        }
        // WKWebView.loadHTMLString has no request-header parameter. Keep the fork API for source
        // compatibility without pretending the headers can be applied to an in-memory HTML load.
        webView.loadHTMLString(html, baseUrl?.let { NSURL.URLWithString(it) })
    }

    override suspend fun loadHtmlFile(
        fileName: String,
        readType: WebViewFileReadType,
        additionalHttpHeaders: Map<String, String>,
    ) {
        try {
            val fileUrl: NSURL
            val readAccessUrl: NSURL?
            when (readType) {
                WebViewFileReadType.ASSET_RESOURCES -> {
                    val resourcePath =
                        (NSBundle.mainBundle.resourcePath ?: "") + "/compose-resources/assets/" + fileName
                    fileUrl = NSURL.fileURLWithPath(resourcePath)
                    val parent = (resourcePath as NSString).stringByDeletingLastPathComponent()
                    readAccessUrl =
                        if (parent.isNotBlank()) {
                            NSURL.fileURLWithPath(parent)
                        } else {
                            NSBundle.mainBundle.resourcePath?.let { NSURL.fileURLWithPath(it) }
                        }
                }

                WebViewFileReadType.COMPOSE_RESOURCE_FILES -> {
                    fileUrl = NSURL(string = fileName)
                    val parent = (fileName as NSString).stringByDeletingLastPathComponent()
                    readAccessUrl = NSURL(string = parent)
                }
            }

            if (!fileUrl.isFileURL()) {
                KLogger.e { "Not a valid file URL: ${fileUrl.absoluteString}" }
                loadHtml("<html><body>Error: Not a file URL</body></html>")
                return
            }
            if (readAccessUrl?.path.isNullOrEmpty()) {
                KLogger.e { "Unable to determine read access URL for ${fileUrl.absoluteString}" }
                loadHtml("<html><body>Error: Cannot determine read access URL</body></html>")
                return
            }
            webView.loadFileURL(fileUrl, readAccessUrl!!)
        } catch (t: Throwable) {
            KLogger.e(t) { "Error loading HTML file: $fileName ($readType)" }
            loadHtml(
                "<html><body><h1>Error Loading File</h1><p>${t.message ?: "Unknown error"}</p></body></html>",
            )
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun postUrl(
        url: String,
        postData: ByteArray,
        additionalHttpHeaders: Map<String, String>,
    ) {
        val request = NSMutableURLRequest(uRL = NSURL(string = url))
        request.HTTPMethod = "POST"
        additionalHttpHeaders.forEach { (key, value) ->
            request.setValue(value, forHTTPHeaderField = key)
        }
        request.HTTPBody =
            postData.usePinned {
                NSData.create(bytes = it.addressOf(0), length = postData.size.convert())
            }
        webView.loadRequest(request)
    }

    override fun goBack() = webView.goBack()

    override fun goForward() = webView.goForward()

    override fun reload() = webView.reload()

    override fun stopLoading() = webView.stopLoading()

    override fun destroy() {
        val pauseScript =
            """
            document.querySelectorAll('video').forEach(function(video) { video.pause(); });
            document.querySelectorAll('audio').forEach(function(audio) { audio.pause(); });
            """.trimIndent()
        webView.evaluateJavaScript(pauseScript) { _, _ ->
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
        webView.evaluateJavaScript(script) { result, error ->
            if (error != null) {
                KLogger.e { "evaluateJavaScript error: $error" }
                callback?.invoke(error.localizedDescription())
            } else {
                callback?.invoke(result?.toString() ?: "")
            }
        }
    }

    override fun injectJsBridge() {
        val bridge = webViewJsBridge ?: return
        super.injectJsBridge()
        val callIOS =
            """
            window.${bridge.jsBridgeName}.postMessage = function (message) {
                window.webkit.messageHandlers.iosJsBridge.postMessage(message);
            };
            """.trimIndent()
        evaluateJavaScript(callIOS)
    }

    override fun initJsBridge(webViewJsBridge: WebViewJsBridge) {
        val jsMessageHandler = WKJsMessageHandler(webViewJsBridge)
        val jsConsoleHandler = WKJsConsoleMessageHandler()
        val controller = webView.configuration.userContentController
        controller.addScriptMessageHandler(jsMessageHandler, "iosJsBridge")
        controller.addScriptMessageHandler(jsConsoleHandler, "consoleLog")

        // Preserve the fork's console capture and double-tap suppression behavior.
        controller.addUserScript(
            WKUserScript(
                source =
                    """
                    (function() {
                        var lastTouchEnd = 0;
                        document.documentElement.addEventListener('touchend', function(event) {
                            var now = (new Date()).getTime();
                            if (now - lastTouchEnd <= 300) event.preventDefault();
                            lastTouchEnd = now;
                        }, false);
                        function captureLog() {
                            window.webkit.messageHandlers.consoleLog.postMessage(
                                Array.prototype.slice.call(arguments).join(' ')
                            );
                        }
                        window.console.log = captureLog;
                    })();
                    """.trimIndent(),
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd,
                forMainFrameOnly = true,
            ),
        )
    }

    override fun saveState(): WebViewBundle? {
        if (getPlatformVersionDouble() < 15.0) return null
        return webView.interactionState as NSData?
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun scrollOffset(): Pair<Int, Int> {
        val offset = webView.scrollView.contentOffset
        offset.useContents { return x.toInt() to y.toInt() }
    }

    private class BundleMarker : NSObject() {
        companion object : NSObjectMeta()
    }
}
