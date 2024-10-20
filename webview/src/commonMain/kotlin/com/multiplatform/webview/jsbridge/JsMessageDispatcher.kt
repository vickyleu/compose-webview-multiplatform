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

//    fun postWebviewDelegateMethod(webView: IWebView?, jsBridgeName: String) {
//        jsHandlerMap.forEach { (key, value) ->
//            webView?.evaluateJavaScript(
//                """
//            window.$jsBridgeName.${key} = function (...args) {
//                function isJsonString(str) {
//                     try {
//                         JSON.parse(str);
//                     } catch (e) {
//                         return false;
//                     }
//                     return true;
//                }
//                if (args.length >= ${value.minimalParamCount()} && args.length <= ${value.methodParamCount()}) {
//                    let params;
//                    if (args.length === 1) {
//                        params = isJsonString(args[0]) ? args[0] : JSON.stringify(args[0]);
//                    } else {
//                        params = JSON.stringify(args.reduce((acc, arg, index) => {
//                            acc[`key${'$'}{index}`] = arg;
//                            return acc;
//                        }, {}));
//                    }
//                    ${(if (value.isCallbackMethod()) {
//                        """return new Promise(  (resolve, reject) => {
//                                window.$jsBridgeName.callNative('$key', params, function (res) {
//                                    resolve(res);  // 将返回值通过 resolve 返回
//                                });
//                           }
//                        );""".trimIndent()
//                    } else {
//                        """window.$jsBridgeName.callNative('$key', params);""".trimIndent()
//                    })}
//                    } else {
//                        console.error('Invalid number of arguments for ${key}');
//                    }
//            };""".trimIndent()
//            )
//        }
//    }

    fun postWebviewDelegateMethod(webView: IWebView?, jsBridgeName: String) {
        jsHandlerMap.forEach { (key, value) ->
            webView?.evaluateJavaScript(
                """
            window.$jsBridgeName.${key} = function (...args) {
                function isJsonString(str) {
                    try {
                        JSON.parse(str);
                    } catch (e) {
                        return false;
                    }
                    return true;
                }
                if (args.length >= ${value.minimalParamCount()} && args.length <= ${value.methodParamCount()}) {
                    let params;
                    if (args.length === 1) {
                        params = isJsonString(args[0]) ? args[0] : JSON.stringify(args[0]);
                    } else {
                        params = JSON.stringify(args.reduce((acc, arg, index) => {
                            acc['key' + index] = arg;  // 使用 'key' + index
                            return acc;
                        }, {}));
                    }
                    ${
                    when {
                        value.isAsyncCallbackMethod() -> {
                            // 异步方法：使用 Promise 来处理返回值
                            """return new Promise((resolve, reject) => {
                                        window.$jsBridgeName.callNative('$key', params, function (res) {
                                            resolve(res);  // 通过 resolve 返回异步结果
                                        });
                                    });
                                """.trimIndent()
                        }
                        value.isSyncCallbackMethod() -> {
                            // 同步方法：使用 Promise 和 async/await 模拟同步行为
                            """return new Promise((resolve, reject) => {
                                        window.$jsBridgeName.callNative('$key', params, function (res) {
                                            resolve(res);  // 回调后立即返回结果
                                        });
                                    }).then(result => {
                                        return result;  // 直接返回结果
                                    });
                                """.trimIndent()
                        }
                        else -> {
                            // 无返回值的普通方法
                            """window.$jsBridgeName.callNative('$key', params);""".trimIndent()
                        }
                    }
                }
                } else {
                    console.error('Invalid number of arguments for ${key}');
                }
            };
            """.trimIndent())
        }
    }
}
