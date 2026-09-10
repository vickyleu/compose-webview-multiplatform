package com.multiplatform.webview.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.multiplatform.webview.request.RequestInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class WebViewEvent {
    data class JsAlert(
        val message: String,
        val callback: () -> Unit = {},
    ) : WebViewEvent()
}

/** Allows control over WebView navigation from outside the composable. */
@Stable
class WebViewNavigator(
    val coroutineScope: CoroutineScope,
    val requestInterceptor: RequestInterceptor? = null,
    private val interceptorEvent: ((WebViewEvent) -> Unit)? = null,
) {
    private sealed interface NavigationEvent {
        data object Back : NavigationEvent
        data object Forward : NavigationEvent
        data object Reload : NavigationEvent
        data object StopLoading : NavigationEvent
        data object Destroy : NavigationEvent

        data class LoadUrl(
            val url: String,
            val additionalHttpHeaders: Map<String, String> = emptyMap(),
        ) : NavigationEvent

        data class LoadHtml(
            val html: String,
            val baseUrl: String? = null,
            val mimeType: String? = null,
            val encoding: String? = "utf-8",
            val historyUrl: String? = null,
            val additionalHttpHeaders: Map<String, String> = emptyMap(),
        ) : NavigationEvent

        data class LoadHtmlFile(
            val fileName: String,
            val readType: WebViewFileReadType = WebViewFileReadType.ASSET_RESOURCES,
            val additionalHttpHeaders: Map<String, String> = emptyMap(),
        ) : NavigationEvent

        data class PostUrl(
            val url: String,
            val postData: ByteArray,
            val additionalHttpHeaders: Map<String, String> = emptyMap(),
        ) : NavigationEvent {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false
                other as PostUrl
                return url == other.url &&
                    postData.contentEquals(other.postData) &&
                    additionalHttpHeaders == other.additionalHttpHeaders
            }

            override fun hashCode(): Int {
                var result = url.hashCode()
                result = 31 * result + postData.contentHashCode()
                result = 31 * result + additionalHttpHeaders.hashCode()
                return result
            }
        }

        data class EvaluateJavaScript(
            val script: String,
            val callback: ((String) -> Unit)?,
        ) : NavigationEvent
    }

    private val navigationEvents: MutableSharedFlow<NavigationEvent> = MutableSharedFlow(replay = 1)

    fun onJsAlert(
        message: String,
        callback: () -> Unit,
    ) {
        val handler = interceptorEvent ?: return
        coroutineScope.launch { handler(WebViewEvent.JsAlert(message, callback)) }
    }

    fun callNavigatorEvent(event: suspend () -> Unit) {
        coroutineScope.launch { event() }
    }

    internal suspend fun IWebView.handleNavigationEvents(): Nothing =
        withContext(Dispatchers.Main) {
            navigationEvents.collect { event ->
                when (event) {
                    NavigationEvent.Back -> goBack()
                    NavigationEvent.Forward -> goForward()
                    NavigationEvent.Reload -> reload()
                    NavigationEvent.StopLoading -> stopLoading()
                    NavigationEvent.Destroy -> destroy()
                    is NavigationEvent.LoadHtml ->
                        loadHtml(
                            event.html,
                            event.baseUrl,
                            event.mimeType,
                            event.encoding,
                            event.historyUrl,
                            event.additionalHttpHeaders,
                        )
                    is NavigationEvent.LoadHtmlFile ->
                        loadHtmlFile(event.fileName, event.readType, event.additionalHttpHeaders)
                    is NavigationEvent.LoadUrl -> loadUrl(event.url, event.additionalHttpHeaders)
                    is NavigationEvent.PostUrl ->
                        postUrl(event.url, event.postData, event.additionalHttpHeaders)
                    is NavigationEvent.EvaluateJavaScript ->
                        evaluateJavaScript(event.script, event.callback)
                }
            }
        }

    var canGoBack: Boolean by mutableStateOf(false)
        internal set

    var canGoForward: Boolean by mutableStateOf(false)
        internal set

    fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) {
        coroutineScope.launch {
            navigationEvents.emit(NavigationEvent.LoadUrl(url, additionalHttpHeaders))
        }
    }

    fun loadHtml(
        html: String,
        baseUrl: String? = null,
        mimeType: String? = null,
        encoding: String? = "utf-8",
        historyUrl: String? = null,
        additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) {
        coroutineScope.launch {
            navigationEvents.emit(
                NavigationEvent.LoadHtml(
                    html,
                    baseUrl,
                    mimeType,
                    encoding,
                    historyUrl,
                    additionalHttpHeaders,
                ),
            )
        }
    }

    fun loadHtmlFile(
        fileName: String,
        readType: WebViewFileReadType = WebViewFileReadType.ASSET_RESOURCES,
        additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) {
        coroutineScope.launch {
            navigationEvents.emit(
                NavigationEvent.LoadHtmlFile(fileName, readType, additionalHttpHeaders),
            )
        }
    }

    fun postUrl(
        url: String,
        postData: ByteArray,
        additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) {
        coroutineScope.launch {
            navigationEvents.emit(NavigationEvent.PostUrl(url, postData, additionalHttpHeaders))
        }
    }

    fun evaluateJavaScript(
        script: String,
        callback: ((String) -> Unit)? = null,
    ) {
        coroutineScope.launch {
            navigationEvents.emit(NavigationEvent.EvaluateJavaScript(script, callback))
        }
    }

    fun navigateBack() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.Back) }
    }

    fun navigateForward() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.Forward) }
    }

    fun reload() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.Reload) }
    }

    fun stopLoading() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.StopLoading) }
    }

    fun destroy() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.Destroy) }
    }

    /** Historical misspelling kept for source compatibility. */
    @Deprecated("Use destroy()", ReplaceWith("destroy()"))
    fun destory() = destroy()
}

@Composable
fun rememberWebViewNavigator(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    requestInterceptor: RequestInterceptor? = null,
): WebViewNavigator = remember(coroutineScope, requestInterceptor) {
    WebViewNavigator(coroutineScope, requestInterceptor)
}
