package com.multiplatform.webview.jsbridge

import com.multiplatform.webview.web.WebViewNavigator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The interface for handling JS messages.
 *
 * The callback/parameter metadata below is a fork extension used to generate
 * strongly-shaped JavaScript delegate methods. Defaults keep upstream handlers
 * source-compatible while preserving the extended API for existing consumers.
 */
interface IJsMessageHandler {
    /** The name of the method that will be called on the JS side. */
    fun methodName(): String

    /** Whether this handler expects a callback with synchronous-style semantics. */
    fun isSyncCallbackMethod(): Boolean = false

    /** Whether this handler expects an asynchronous callback. */
    fun isAsyncCallbackMethod(): Boolean = false

    fun canHandle(methodName: String) = methodName() == methodName

    /** Maximum number of parameters accepted by the generated JS delegate. */
    fun methodParamCount(): Int = 1

    /** Minimum number of parameters accepted by the generated JS delegate. */
    fun minimalParamCount(): Int = methodParamCount()

    /**
     * Handle a message dispatched from JavaScript.
     *
     * @param message The message dispatched from JS.
     * @param navigator Navigator that can control the WebView.
     * @param callback Callback used to return data to JS.
     */
    fun handle(
        message: JsMessage,
        navigator: WebViewNavigator?,
        callback: (String) -> Unit,
    )
}

/** Decode [JsMessage.params] to [T]. */
inline fun <reified T : Any> IJsMessageHandler.processParams(message: JsMessage): T =
    Json.decodeFromString(message.params)

/** Encode [res] to a JSON string. */
inline fun <reified T : Any> IJsMessageHandler.dataToJsonString(res: T): String =
    Json.encodeToString(res)
