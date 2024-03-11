package com.multiplatform.webview.web

import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.multiplatform.webview.jsbridge.WebViewJsBridge

/**
 * Android WebView implementation.
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
    val client = remember {
        object : AccompanistWebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return navigator.navigatorTo(
                    url ?: return false
                )
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return navigator.navigatorTo(
                    request?.url?.toString() ?: return super.shouldOverrideUrlLoading(view, request)
                )
            }
        }
    }
    val chromeClient = remember {
        object : AccompanistWebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                val rlt = result ?: return super.onJsAlert(view, url, message, result)
                return navigator.alert(message ?: return super.onJsAlert(view, url, message, rlt)) {
                    if (it) {
                        rlt.confirm()
                    } else {
                        rlt.cancel()
                    }
                }
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                val rlt = result ?: return super.onJsConfirm(view, url, message, result)
                return navigator.alert(
                    message ?: return super.onJsConfirm(view, url, message, rlt)
                ) { it ->
                    if (it) {
                        rlt.confirm()
                    } else {
                        rlt.cancel()
                    }
                }
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                val rlt =
                    result ?: return super.onJsPrompt(view, url, message, defaultValue, result)
                return navigator.prompt(
                    message ?: return super.onJsPrompt(view, url, message, defaultValue, rlt)
                ) { it, msg ->
                    if (it) {
                        rlt.confirm(msg)
                    } else {
                        rlt.cancel()
                    }
                }
            }


        }
    }
    AccompanistWebView(
        state,
        modifier,
        captureBackPresses,
        navigator,
        webViewJsBridge,
        onCreated = { _ -> onCreated() },
        onDispose = { _ -> onDispose() },
        chromeClient = chromeClient,
        client = client,
    )
}
