package com.multiplatform.webview.web

/** WasmJS implementation of WebViewBundle for storing WebView state. */
actual class WebViewBundle {
    var history: List<String> = emptyList()
    var currentIndex: Int = -1

    actual constructor()

    constructor(history: List<String>, currentIndex: Int) {
        this.history = history
        this.currentIndex = currentIndex
    }
}
