package com.multiplatform.webview.web

import kotlinx.serialization.Serializable

@Serializable
actual class WebViewBundle(
    var url: String? = null,
    var title: String? = null,
)
