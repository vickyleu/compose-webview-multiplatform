package com.multiplatform.webview.web

import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.util.KLogger
import dev.datlag.kcef.KCEFBrowser
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandler
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest

internal fun CefBrowser.getCurrentUrl(): String? = url

internal fun CefBrowser.addDisplayHandler(state: WebViewState) {
    client.addDisplayHandler(
        object : CefDisplayHandler {
            override fun onAddressChange(browser: CefBrowser?, frame: CefFrame?, url: String?) {
                KLogger.d { "onAddressChange: $url" }
                state.lastLoadedUrl = getCurrentUrl()
            }

            override fun onTitleChange(browser: CefBrowser?, title: String?) {
                val percentage = state.webSettings.zoomLevel * 100.0
                val realZoomLevel = (percentage - 100.0) / 25.0
                zoomLevel = realZoomLevel
                state.pageTitle = title
            }

            override fun onFullscreenModeChange(p0: CefBrowser?, p1: Boolean) = Unit
            override fun onTooltip(browser: CefBrowser?, text: String?) = false
            override fun onStatusMessage(browser: CefBrowser?, value: String?) = Unit
            override fun onConsoleMessage(
                browser: CefBrowser?,
                level: CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int,
            ) = false
            override fun onCursorChange(browser: CefBrowser?, cursorType: Int) = false
        },
    )
}

internal fun CefBrowser.addLoadListener(state: WebViewState, navigator: WebViewNavigator) {
    client.addLoadHandler(
        object : CefLoadHandler {
            private var lastLoadedUrl = "null"

            override fun onLoadingStateChange(
                browser: CefBrowser?,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean,
            ) {
                if (isLoading) {
                    state.loadingState = LoadingState.Initializing
                } else {
                    state.loadingState = LoadingState.Finished
                    if (url != null && url != lastLoadedUrl) {
                        state.webView?.injectJsBridge()
                        lastLoadedUrl = url
                    }
                }
                navigator.canGoBack = canGoBack
                navigator.canGoForward = canGoForward
            }

            override fun onLoadStart(browser: CefBrowser?, frame: CefFrame?, transitionType: CefRequest.TransitionType?) {
                lastLoadedUrl = "null"
                state.loadingState = LoadingState.Loading(0F)
                state.errorsForCurrentRequest.clear()
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                state.loadingState = LoadingState.Finished
                navigator.canGoBack = canGoBack()
                navigator.canGoForward = canGoForward()
                state.lastLoadedUrl = getCurrentUrl()
            }

            override fun onLoadError(
                browser: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?,
            ) {
                state.loadingState = LoadingState.Finished
                KLogger.i { "Failed to load url: $errorCode $failedUrl\n$errorText" }
                state.errorsForCurrentRequest.add(
                    WebViewError(
                        code = errorCode?.code ?: 404,
                        description = "Failed to load url: $failedUrl\n$errorText",
                        isFromMainFrame = frame?.isMain ?: false,
                    ),
                )
            }
        },
    )
}

internal fun KCEFBrowser.addRequestHandler(state: WebViewState, navigator: WebViewNavigator) {
    client.addRequestHandler(
        object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
                userGesture: Boolean,
                isRedirect: Boolean,
            ): Boolean {
                navigator.requestInterceptor?.let { interceptor ->
                    val headers = mutableMapOf<String, String>()
                    request?.getHeaderMap(headers)
                    val webRequest =
                        WebRequest(
                            request?.url.toString(),
                            headers,
                            isForMainFrame = frame?.isMain ?: false,
                            isRedirect = isRedirect,
                            request?.method ?: "GET",
                        )
                    return when (val result = interceptor.onInterceptUrlRequest(webRequest, navigator)) {
                        is WebRequestInterceptResult.Allow ->
                            super.onBeforeBrowse(browser, frame, request, userGesture, isRedirect)
                        is WebRequestInterceptResult.Reject -> true
                        is WebRequestInterceptResult.Modify -> {
                            navigator.loadUrl(result.request.url, result.request.headers)
                            true
                        }
                    }
                }
                return super.onBeforeBrowse(browser, frame, request, userGesture, isRedirect)
            }
        },
    )
}
