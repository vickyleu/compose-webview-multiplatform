package com.multiplatform.webview.web

import android.content.Context
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.runtime.Composable
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
    onCreated: (NativeWebView) -> Unit,
    onDispose: (NativeWebView) -> Unit,
    factory: (WebViewFactoryParam) -> NativeWebView,
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
                // 进入视频全屏
                (context as Activity).window.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
                (context as Activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; // 横屏
                (context as Activity).window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                // 此处的 view 就是全屏的视频播放界面，需要把它添加到我们的界面上
                win.addView(
                    v,
                    WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_APPLICATION)
                )
                // 去除状态栏和导航按钮
                fullScreen(v)
//                callback?.onCustomViewHidden()
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
                (context as Activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; // 横屏
                (context as Activity).window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                (context as Activity).window.clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
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
        onCreated = onCreated,
        onDispose = onDispose,
        factory = { factory(WebViewFactoryParam(it)) },
        onCreated = { _ -> onCreated() },
        onDispose = { _ ->
            onDispose()
//            windowManager
         },
        chromeClient = chromeClient,
        client = client,
    )
}

/** Android WebView factory parameters: a context. */
actual data class WebViewFactoryParam(val context: Context)

/** Default WebView factory for Android. */
actual fun defaultWebViewFactory(param: WebViewFactoryParam) = android.webkit.WebView(param.context)
