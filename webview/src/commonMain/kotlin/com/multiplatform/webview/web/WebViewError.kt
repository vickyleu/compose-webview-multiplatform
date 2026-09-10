package com.multiplatform.webview.web

import androidx.compose.runtime.Immutable

/** A wrapper class to hold errors from the WebView. */
@Immutable
data class WebViewError(
    val code: Int,
    val description: String,
    /** True if the failed request belongs to the main frame. */
    val isFromMainFrame: Boolean = false,
)
