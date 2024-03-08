package com.multiplatform.webview.web

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter

class DisablePopupWindowsLifeSpanHandler : CefLifeSpanHandlerAdapter() {
    override fun onBeforePopup(
        browser: CefBrowser?,
        frame: CefFrame?,
        targetUrl: String?,
        targetFrameName: String?,
    ): Boolean {
        if (targetUrl != null) {
            browser?.loadURL(targetUrl)
        }
        return true
    }
}
