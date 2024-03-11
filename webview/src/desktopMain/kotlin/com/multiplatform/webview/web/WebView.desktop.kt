package com.multiplatform.webview.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.util.KLogger
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.callback.CefJSDialogCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefJSDialogHandler
import org.cef.handler.CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_ALERT
import org.cef.handler.CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_CONFIRM
import org.cef.handler.CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_PROMPT
import org.cef.handler.CefJSDialogHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes

/**
 * Desktop WebView implementation.
 */
@Composable
actual fun ActualWebView(
    state: WebViewState,
    modifier: Modifier,
    captureBackPresses: Boolean,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    onCreated: () -> Unit,
    onDispose: () -> Unit,
) {
    DesktopWebView(
        state,
        modifier,
        navigator,
        webViewJsBridge,
        onCreated = onCreated,
        onDispose = onDispose,
    )
}

/**
 * Desktop WebView implementation.
 */
@OptIn(ExperimentalResourceApi::class, InternalResourceApi::class)
@Composable
fun DesktopWebView(
    state: WebViewState,
    modifier: Modifier,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    onCreated: () -> Unit,
    onDispose: () -> Unit,
) {
    val currentOnDispose by rememberUpdatedState(onDispose)
    val client =
        remember(state.webSettings.desktopWebSettings.disablePopupWindows) {
            KCEF.newClientOrNullBlocking()?.also {
                if (state.webSettings.desktopWebSettings.disablePopupWindows) {
                    it.addLifeSpanHandler(DisablePopupWindowsLifeSpanHandler())
                    it.addJSDialogHandler(object : CefJSDialogHandlerAdapter() {
                        override fun onJSDialog(
                            browser: CefBrowser?,
                            origin_url: String?,
                            dialog_type: CefJSDialogHandler.JSDialogType?,
                            message_text: String?,
                            default_prompt_text: String?,
                            callback: CefJSDialogCallback?,
                            suppress_message: BoolRef?
                        ): Boolean {
                            val msgText = message_text ?: return false.apply {
                                callback?.Continue(false, null)
                            }
                            when (dialog_type) {
                                JSDIALOGTYPE_ALERT,
                                JSDIALOGTYPE_CONFIRM -> {
                                    navigator.alert(msgText) {
                                        callback?.Continue(it, null)
                                    }
                                }

                                JSDIALOGTYPE_PROMPT -> {
                                    navigator.prompt(msgText) { it, msg ->
                                        callback?.Continue(it, msg)
                                    }
                                }

                                null -> callback?.Continue(false, null)
                            }
                            return false
                        }
                    })
                    it.addDisplayHandler(object : CefDisplayHandlerAdapter() {
                        override fun onConsoleMessage(
                            browser: CefBrowser?,
                            level: CefSettings.LogSeverity?,
                            message: String?,
                            source: String?,
                            line: Int
                        ): Boolean {
                            KLogger.info {
                                "kCFJsMessageHandler: $message"
                            }
                            return false
                        }
                    })
                    it.addRequestHandler(object : CefRequestHandlerAdapter() {
                        override fun onBeforeBrowse(
                            browser: CefBrowser?, frame: CefFrame?,
                            request: CefRequest?, user_gesture: Boolean, is_redirect: Boolean
                        ): Boolean {
                            return navigator.navigatorTo(request?.url ?: return false)
                        }
                    })
                } else {
                    if (it.getLifeSpanHandler() is DisablePopupWindowsLifeSpanHandler) {
                        it.removeLifeSpanHandler()
                        it.removeJSDialogHandler()
                        it.removeDisplayHandler()
                        it.removeRequestHandler()
                    }
                }
            }
        }
    val scope = rememberCoroutineScope()
    val fileContent by produceState("", state.content) {
        value =
            if (state.content is WebContent.File) {
                val res = readResourceBytes("assets/${(state.content as WebContent.File).fileName}")
//                resource()
                res.decodeToString().trimIndent()
            } else {
                ""
            }
    }

    val browser: KCEFBrowser? =
        remember(
            client,
            state.webSettings.desktopWebSettings.offScreenRendering,
            state.webSettings.desktopWebSettings.transparent,
            state.webSettings,
            fileContent,
        ) {
            val rendering =
                if (state.webSettings.desktopWebSettings.offScreenRendering) {
                    CefRendering.OFFSCREEN
                } else {
                    CefRendering.DEFAULT
                }

            when (val current = state.content) {
                is WebContent.Url ->
                    client?.createBrowser(
                        current.url,
                        rendering,
                        state.webSettings.desktopWebSettings.transparent,
                        createModifiedRequestContext(state.webSettings),
                    )

                is WebContent.Data ->
                    client?.createBrowserWithHtml(
                        current.data,
                        current.baseUrl ?: KCEFBrowser.BLANK_URI,
                        rendering,
                        state.webSettings.desktopWebSettings.transparent,
                    )

                is WebContent.File ->
                    client?.createBrowserWithHtml(
                        fileContent,
                        KCEFBrowser.BLANK_URI,
                        rendering,
                        state.webSettings.desktopWebSettings.transparent,
                    )

                else -> {
                    client?.createBrowser(
                        KCEFBrowser.BLANK_URI,
                        rendering,
                        state.webSettings.desktopWebSettings.transparent,
                        createModifiedRequestContext(state.webSettings),
                    )
                }
            }
        }
    val desktopWebView =
        remember(browser) {
            if (browser != null) {
                DesktopWebView(browser, scope, webViewJsBridge)
            } else {
                null
            }
        }

    browser?.let {
        SwingPanel(
            factory = {
                onCreated()
                state.webView = desktopWebView
                webViewJsBridge?.webView = desktopWebView
                browser.apply {
                    addDisplayHandler(state)
                    addLoadListener(state, navigator)
                }
                browser.uiComponent
            },
            modifier = modifier,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            client?.dispose()
            currentOnDispose()
        }
    }
}
