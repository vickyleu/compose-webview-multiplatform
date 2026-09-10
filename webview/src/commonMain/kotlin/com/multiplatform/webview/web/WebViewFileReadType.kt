package com.multiplatform.webview.web

/** Source location used when loading an HTML file into the WebView. */
enum class WebViewFileReadType {
    /** Platform assets/resources directory. */
    ASSET_RESOURCES,

    /** Compose Multiplatform composeResources/files URI. */
    COMPOSE_RESOURCE_FILES,
}
