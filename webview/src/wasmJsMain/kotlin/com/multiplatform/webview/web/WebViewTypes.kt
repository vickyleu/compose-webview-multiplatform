package com.multiplatform.webview.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class WasmJsWebViewState(
    initialUrl: String = "",
    initialContent: String = "",
) {
    var url: String by mutableStateOf(initialUrl)
    var content: String by mutableStateOf(initialContent)
    var lastLoadedUrl: String? by mutableStateOf(null)
    var isLoading: Boolean by mutableStateOf(false)
    var pageTitle: String? by mutableStateOf(null)

    internal fun getHtmlContent(): HtmlContent =
        if (content.isNotBlank()) {
            HtmlContent.Data(content)
        } else if (url.isNotBlank()) {
            HtmlContent.Url(url)
        } else {
            HtmlContent.NavigatorOnly
        }
}

sealed class HtmlContent {
    data class Url(
        val url: String,
        val additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) : HtmlContent()

    data class Data(
        val data: String,
        val baseUrl: String? = null,
        val mimeType: String? = null,
        val encoding: String? = "utf-8",
        val historyUrl: String? = null,
    ) : HtmlContent()

    data class Post(
        val url: String,
        val postData: ByteArray,
    ) : HtmlContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Post) return false
            return url == other.url && postData.contentEquals(other.postData)
        }

        override fun hashCode(): Int = 31 * url.hashCode() + postData.contentHashCode()
    }

    data object NavigatorOnly : HtmlContent()
}

sealed class HtmlLoadingState {
    data object Initializing : HtmlLoadingState()
    data object Loading : HtmlLoadingState()
    data class Finished(
        val isError: Boolean = false,
        val errorMessage: String? = null,
    ) : HtmlLoadingState()
}

class HtmlViewState {
    var htmlElement: Any? by mutableStateOf(null)
        internal set
    var content: HtmlContent by mutableStateOf(HtmlContent.NavigatorOnly)
        internal set
    var loadingState: HtmlLoadingState by mutableStateOf(HtmlLoadingState.Initializing)
        internal set
    var lastLoadedUrl: String? by mutableStateOf(null)
        internal set
    var pageTitle: String? by mutableStateOf(null)
        internal set
    var error: Throwable? by mutableStateOf(null)
        internal set
}
