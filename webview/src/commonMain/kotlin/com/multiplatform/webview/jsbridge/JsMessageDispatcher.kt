package com.multiplatform.webview.jsbridge

import androidx.compose.runtime.Immutable
import com.multiplatform.webview.web.IWebView
import com.multiplatform.webview.web.WebViewNavigator

/**
 * Created By Kevin Zou On 2023/10/31
 */

/**
 * A message dispatched from JS to native.
 */
@Immutable
internal class JsMessageDispatcher {
    private val jsHandlerMap = mutableMapOf<String, IJsMessageHandler>()

    fun registerJSHandler(handler: IJsMessageHandler) {
        jsHandlerMap[handler.methodName()] = handler
    }

    fun dispatch(
        message: JsMessage,
        navigator: WebViewNavigator? = null,
        callback: (String) -> Unit,
    ) {
        jsHandlerMap[message.methodName]?.handle(message, navigator, callback)
    }

    fun canHandle(id: String) = jsHandlerMap.containsKey(id)

    fun unregisterJSHandler(handler: IJsMessageHandler) {
        jsHandlerMap.remove(handler.methodName())
    }

    fun clear() {
        jsHandlerMap.clear()
    }

    fun postWebviewDelegateMethod(webView: IWebView?, jsBridgeName: String) {
        jsHandlerMap.forEach { (key, value) ->
            webView?.evaluateJavaScript("""
            window.$jsBridgeName.${key} = function (...args) {
                if (args.length >= ${value.minimalParamCount()} && args.length <= ${value.methodParamCount()}) {
                    const params = args.length > 1 ? { ${List(value.methodParamCount()){"'key$it': args[$it]"}.joinToString(", ")} } : args[0];
                    window.$jsBridgeName.callNative('$key', params);
                } else {
                    console.error('Invalid number of arguments for ${key}');
                }
            };
        """.trimIndent())
        }
//        jsHandlerMap.forEach { (key, value) ->
//            webView?.evaluateJavaScript("""
//            window.$jsBridgeName.${key} = function (${List(value.methodParamCount()){"params$it"}.joinToString(",")})" {
//                    ${
//                        if(value.methodParamCount()>1){
//                            "const params = { ${List(value.methodParamCount()){"'key$it':params$it"}.joinToString(", ")} };"
//                            ""
//                        }else {
//                           "window.$jsBridgeName.callNative('${key}',params);"
//                        }
//                    }
//            };
//        """.trimIndent().apply {
//            println("evaluateJavaScript:$this")
//            })
//        }
    }
}
