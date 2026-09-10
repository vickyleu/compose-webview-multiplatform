package com.multiplatform.webview.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.w3c.dom.Element

class HtmlViewNavigator(
    private val coroutineScope: CoroutineScope,
) {
    private val navigationEvents = MutableSharedFlow<NavigationEvent>()

    var canGoBack by mutableStateOf(false)
        internal set
    var canGoForward by mutableStateOf(false)
        internal set

    internal suspend fun handleNavigationEvents(nativeElement: Element) {
        navigationEvents.collect { event ->
            when (event) {
                NavigationEvent.Back -> navigateBackJs(nativeElement)
                NavigationEvent.Forward -> navigateForwardJs(nativeElement)
                NavigationEvent.Reload -> reloadJs(nativeElement)
                NavigationEvent.StopLoading -> stopLoadingJs(nativeElement)
                is NavigationEvent.LoadUrl -> setUrlJs(nativeElement, event.url)
                is NavigationEvent.LoadHtml -> setHtmlContentJs(nativeElement, event.data)
                is NavigationEvent.EvaluateJavaScript -> {
                    try {
                        event.callback?.invoke(evaluateScriptJs(nativeElement, event.script))
                    } catch (t: Throwable) {
                        event.callback?.invoke("Error: ${t.message}")
                    }
                }
            }
            updateNavigationState(nativeElement)
        }
    }

    fun navigateBack() { coroutineScope.launch { navigationEvents.emit(NavigationEvent.Back) } }
    fun navigateForward() { coroutineScope.launch { navigationEvents.emit(NavigationEvent.Forward) } }
    fun reload() { coroutineScope.launch { navigationEvents.emit(NavigationEvent.Reload) } }
    fun stopLoading() { coroutineScope.launch { navigationEvents.emit(NavigationEvent.StopLoading) } }

    fun loadUrl(url: String, additionalHttpHeaders: Map<String, String> = emptyMap()) {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.LoadUrl(url, additionalHttpHeaders)) }
    }

    fun loadHtml(
        data: String,
        baseUrl: String? = null,
        mimeType: String? = null,
        encoding: String? = "utf-8",
        historyUrl: String? = null,
    ) {
        coroutineScope.launch {
            navigationEvents.emit(NavigationEvent.LoadHtml(data, baseUrl, mimeType, encoding, historyUrl))
        }
    }

    fun evaluateJavaScript(script: String, callback: ((String) -> Unit)? = null) {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.EvaluateJavaScript(script, callback)) }
    }

    private fun updateNavigationState(nativeElement: Element) {
        try {
            canGoBack = checkCanGoBackJs(nativeElement)
            canGoForward = checkCanGoForwardJs(nativeElement)
        } catch (t: Throwable) {
            consoleErrorJs("Error updating navigation state: ${t.message}")
        }
    }

    private sealed class NavigationEvent {
        data object Back : NavigationEvent()
        data object Forward : NavigationEvent()
        data object Reload : NavigationEvent()
        data object StopLoading : NavigationEvent()
        data class LoadUrl(val url: String, val additionalHttpHeaders: Map<String, String>) : NavigationEvent()
        data class LoadHtml(
            val data: String,
            val baseUrl: String?,
            val mimeType: String?,
            val encoding: String?,
            val historyUrl: String?,
        ) : NavigationEvent()
        data class EvaluateJavaScript(val script: String, val callback: ((String) -> Unit)?) : NavigationEvent()
    }
}

@Composable
fun rememberHtmlViewNavigator(): HtmlViewNavigator {
    val scope = rememberCoroutineScope()
    return remember { HtmlViewNavigator(scope) }
}
