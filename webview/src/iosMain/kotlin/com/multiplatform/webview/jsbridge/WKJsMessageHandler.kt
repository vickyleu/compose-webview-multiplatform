package com.multiplatform.webview.jsbridge

import com.multiplatform.webview.util.KLogger
import kotlinx.serialization.json.Json
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.darwin.NSObject

/**
 * Created By Kevin Zou On 2023/11/1
 */

/**
 * A JS message handler for WKWebView.
 */
class WKJsMessageHandler(private val webViewJsBridge: WebViewJsBridge) :
    WKScriptMessageHandlerProtocol,
    NSObject() {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val body = didReceiveScriptMessage.body
        val method = didReceiveScriptMessage.name
        KLogger.info { "didReceiveScriptMessage: $body, $method" }
        (body as String).apply {
            val message = Json.decodeFromString<JsMessage>(body)
            KLogger.info {
                "WKJsMessageHandler: $message"
            }
            webViewJsBridge.dispatch(message)
        }
    }
}

class WKJsConsoleMessageHandler : WKScriptMessageHandlerProtocol,
    NSObject() {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        println("收到日志: ${didReceiveScriptMessage.body} ${didReceiveScriptMessage.name}")
        if (didReceiveScriptMessage.name == "consoleLog") {
            KLogger.info { "JavaScript log: ${didReceiveScriptMessage.body}" }
        }
    }
}
