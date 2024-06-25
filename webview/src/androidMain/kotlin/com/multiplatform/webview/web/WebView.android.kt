package com.multiplatform.webview.web

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
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
    val context = LocalContext.current

    val windowManager = remember { context.getSystemService<WindowManager>() }

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

            private var fullScreenView:View?=null
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                val v = view?:return
                val win = windowManager?:return
                // 此处的 view 就是全屏的视频播放界面，需要把它添加到我们的界面上
                win.addView(
                    v,
                    WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_APPLICATION)
                )
                // 去除状态栏和导航按钮
                fullScreen(v)
                fullScreenView = v
            }
            @SuppressLint("ObsoleteSdkInt")
            private fun fullScreen(view: View) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
                    view.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
                } else {
                    view.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
                }
            }
            override fun onHideCustomView() {
                // 退出全屏播放，我们要把之前添加到界面上的视频播放界面移除
                windowManager?.removeViewImmediate(fullScreenView)
                fullScreenView = null
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
        onDispose = { _ ->
            onDispose()
//            windowManager
         },
        chromeClient = chromeClient,
        client = client,
    )
}
