package com.multiplatform.webview.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.util.KLogger

/**
 * Created By Kevin Zou On 2023/9/5
 */

/**
 * A wrapper around the Android View WebView to provide a basic WebView composable.
 *
 * If you require more customisation you are most likely better rolling your own and using this
 * wrapper as an example.
 *
 * The WebView attempts to set the layoutParams based on the Compose modifier passed in. If it
 * is incorrectly sizing, use the layoutParams composable function instead.
 *
 * @param state The webview state holder where the Uri to load is defined.
 * @param modifier A compose modifier
 * @param captureBackPresses Set to true to have this Composable capture back presses and navigate
 * the WebView back.
 * @param navigator An optional navigator object that can be used to control the WebView's
 * navigation from outside the composable.
 * @param onCreated Called when the WebView is first created, this can be used to set additional
 * settings on the WebView. WebChromeClient and WebViewClient should not be set here as they will be
 * subsequently overwritten after this lambda is called.
 * @param onDispose Called when the WebView is destroyed. Provides a bundle which can be saved
 * if you need to save and restore state in this WebView.
 * @param client Provides access to WebViewClient via subclassing
 * @param chromeClient Provides access to WebChromeClient via subclassing
 * @param factory An optional WebView factory for using a custom subclass of WebView
 * @sample com.google.accompanist.sample.webview.BasicWebViewSample
 */
@Composable
fun AccompanistWebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    captureBackPresses: Boolean = true,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    webViewJsBridge: WebViewJsBridge? = null,
    onCreated: (WebView) -> Unit = {},
    onDispose: (WebView) -> Unit = {},
    client: AccompanistWebViewClient = remember { AccompanistWebViewClient() },
    chromeClient: AccompanistWebChromeClient = remember { AccompanistWebChromeClient() },
    factory: ((Context) -> WebView)? = null,
) {
    BoxWithConstraints(modifier) {
        // WebView changes it's layout strategy based on
        // it's layoutParams. We convert from Compose Modifier to
        // layout params here.
        val width =
            if (constraints.hasFixedWidth) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
        val height =
            if (constraints.hasFixedHeight) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }

        val layoutParams =
            FrameLayout.LayoutParams(
                width,
                height,
            )

        AccompanistWebView(
            state,
            layoutParams,
            Modifier,
            captureBackPresses,
            navigator,
            webViewJsBridge,
            onCreated,
            onDispose,
            client,
            chromeClient,
            factory,
        )
    }
}

/**
 * A wrapper around the Android View WebView to provide a basic WebView composable.
 *
 * If you require more customisation you are most likely better rolling your own and using this
 * wrapper as an example.
 *
 * The WebView attempts to set the layoutParams based on the Compose modifier passed in. If it
 * is incorrectly sizing, use the layoutParams composable function instead.
 *
 * @param state The webview state holder where the Uri to load is defined.
 * @param layoutParams A FrameLayout.LayoutParams object to custom size the underlying WebView.
 * @param modifier A compose modifier
 * @param captureBackPresses Set to true to have this Composable capture back presses and navigate
 * the WebView back.
 * @param navigator An optional navigator object that can be used to control the WebView's
 * navigation from outside the composable.
 * @param onCreated Called when the WebView is first created, this can be used to set additional
 * settings on the WebView. WebChromeClient and WebViewClient should not be set here as they will be
 * subsequently overwritten after this lambda is called.
 * @param onDispose Called when the WebView is destroyed. Provides a bundle which can be saved
 * if you need to save and restore state in this WebView.
 * @param client Provides access to WebViewClient via subclassing
 * @param chromeClient Provides access to WebChromeClient via subclassing
 * @param factory An optional WebView factory for using a custom subclass of WebView
 */
@Composable
@Suppress("DEPRECATION")
fun AccompanistWebView(
    state: WebViewState,
    layoutParams: FrameLayout.LayoutParams,
    modifier: Modifier = Modifier,
    captureBackPresses: Boolean = true,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    webViewJsBridge: WebViewJsBridge? = null,
    onCreated: (WebView) -> Unit = {},
    onDispose: (WebView) -> Unit = {},
    client: AccompanistWebViewClient = remember { AccompanistWebViewClient() },
    chromeClient: AccompanistWebChromeClient = remember { AccompanistWebChromeClient() },
    factory: ((Context) -> WebView)? = null,
) {
    val webView = state.webView
    val scope = rememberCoroutineScope()

    BackHandler(captureBackPresses && navigator.canGoBack) {
        webView?.goBack()
    }
    val context = LocalContext.current
    val windowManager = remember { context.getSystemService<WindowManager>() }
    // Set the state of the client and chrome client
    // This is done internally to ensure they always are the same instance as the
    // parent Web composable
    client.state = state
    client.navigator = navigator
    chromeClient.setWindowManager(windowManager, context)
    chromeClient.state = state
    chromeClient.navigator = navigator

    AndroidView(
        factory = { context ->
            (factory?.invoke(context) ?: WebView(context)).apply {
                val webViewInstance = this
                WebView.setWebContentsDebuggingEnabled(state.webSettings.isInspectable)

                onCreated(this)
                this.layoutParams = layoutParams
                state.viewState?.let {
                    this.restoreState(it)
                }
                webChromeClient = chromeClient
                webViewClient = client

                // Avoid covering other components
                this.setLayerType(state.webSettings.androidWebSettings.layerType, null)

                settings.apply {
                    state.webSettings.let {
                        javaScriptEnabled = it.isJavaScriptEnabled
                        userAgentString = it.customUserAgentString
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = it.allowFileAccessFromFileURLs
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = it.allowUniversalAccessFromFileURLs
                        setSupportZoom(it.supportZoom)
                    }

                    state.webSettings.androidWebSettings.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = it.safeBrowsingEnabled
                        }
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            isAlgorithmicDarkeningAllowed = it.isAlgorithmicDarkeningAllowed
                        }
                        setBackgroundColor(state.webSettings.backgroundColor.toArgb())
                        allowFileAccess = it.allowFileAccess
                        textZoom = it.textZoom
                        useWideViewPort = it.useWideViewPort
                        if (it.useWideViewPort) {
                            setInitialScale(1)
                            loadWithOverviewMode = true
                            this.layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
                            println("useWideViewPort is true")
                        }else{
                            // 页面还可以左右滑动,TODO 需要设置内部的页面不可左右滑动
                            loadWithOverviewMode = false
                        }
                        standardFontFamily = it.standardFontFamily
                        defaultFontSize = it.defaultFontSize
                        loadsImagesAutomatically = it.loadsImagesAutomatically
                        domStorageEnabled = it.domStorageEnabled
                        mediaPlaybackRequiresUserGesture = it.mediaPlaybackRequiresUserGesture
                    }
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    val nightModeFlags =
                        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                        WebSettingsCompat.setForceDark(
                            this.settings,
                            WebSettingsCompat.FORCE_DARK_ON,
                        )
                    } else {
                        WebSettingsCompat.setForceDark(
                            this.settings,
                            WebSettingsCompat.FORCE_DARK_OFF,
                        )
                    }

                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                        WebSettingsCompat.setForceDarkStrategy(
                            this.settings,
                            WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY,
                        )
                    }
                }
            }.also {
                val androidWebView = AndroidWebView(it, scope, webViewJsBridge)
                state.webView = androidWebView
                webViewJsBridge?.webView = androidWebView
            }
        },
        modifier = modifier,
        onReset = {},
        onRelease = {
            onDispose(it)
        },
    )
}

/**
 * AccompanistWebViewClient
 *
 * A parent class implementation of WebViewClient that can be subclassed to add custom behaviour.
 *
 * As Accompanist Web needs to set its own web client to function, it provides this intermediary
 * class that can be overriden if further custom behaviour is required.
 */
open class AccompanistWebViewClient : WebViewClient() {
    open lateinit var state: WebViewState
        internal set
    open lateinit var navigator: WebViewNavigator
        internal set
    private var isRedirect = false

    override fun onPageStarted(
        view: WebView,
        url: String?,
        favicon: Bitmap?,
    ) {
        super.onPageStarted(view, url, favicon)
        KLogger.d {
            "onPageStarted: $url"
        }
        state.loadingState = LoadingState.Loading(0.0f)
        state.errorsForCurrentRequest.clear()
        state.pageTitle = null
        state.lastLoadedUrl = url
        val supportZoom = if (state.webSettings.supportZoom) "yes" else "no"

        // set scale level
        @Suppress("ktlint:standard:max-line-length")
        val script =
            "var meta = document.createElement('meta');" +
                    "meta.setAttribute('name', 'viewport');" +
                    "meta.setAttribute('content', 'width=device-width, initial-scale=${state.webSettings.zoomLevel}, maximum-scale=${state.webSettings.zoomLevel}, minimum-scale=${state.webSettings.zoomLevel},user-scalable=$supportZoom');" +
                    "document.getElementsByTagName('head')[0].appendChild(meta);"
        navigator.evaluateJavaScript(script)
    }


    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        if (error?.primaryError == SslError.SSL_IDMISMATCH) {
            val handlerImp = handler ?: return kotlin.run {
                try {
                    super.onReceivedSslError(view, handler, error)
                } catch (ignored: Exception) {
                }
            }
            val url = error.url
            if (state.webSettings.sslPiningHosts.isNotEmpty()) {
                val str = state.webSettings.sslPiningHosts.joinToString("|") {
                    it.split(".").joinToString("\\.") // 使用单个反斜杠转义正则中的点
                }
                val pattern = "^(https?://)?([a-zA-Z0-9_-]+\\.)?($str)(/.*)?$".toRegex()
                if (pattern.matches(url)) {
                    handlerImp.proceed()
                } else {
                    handlerImp.cancel() // 取消不匹配的URL
                }
            } else {
                handlerImp.cancel() // 取消不匹配的URL
            }
        } else {
            try {
                super.onReceivedSslError(view, handler, error)
            } catch (ignored: Exception) {
            }
        }
    }


    @Deprecated("Deprecated in Java")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        when {
            errorCode == ERROR_HOST_LOOKUP && description == "INTERNET_DISCONNECTED" -> {
                state.loadingState = LoadingState.ErrorLoading("网络加载失败，请重新检查网络")
            }

            description == "ADDRESS_UNREACHABLE" -> {
                state.loadingState = LoadingState.ErrorLoading("网络加载失败，请重新检查网络")
            }
        }
        super.onReceivedError(view, errorCode, description, failingUrl)
    }


    override fun onPageFinished(
        view: WebView,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        KLogger.d {
            "onPageFinished: $url"
        }
        state.loadingState = LoadingState.Finished
        state.lastLoadedUrl = url
    }

    override fun doUpdateVisitedHistory(
        view: WebView,
        url: String?,
        isReload: Boolean,
    ) {
        KLogger.d {
            "doUpdateVisitedHistory: $url"
        }
        super.doUpdateVisitedHistory(view, url, isReload)

        navigator.canGoBack = view.canGoBack()
        navigator.canGoForward = view.canGoForward()
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        super.onReceivedError(view, request, error)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            KLogger.e {
                "onReceivedError: $error"
            }
            return
        }
        KLogger.e {
            "onReceivedError: ${error?.description}"
        }
        if (error != null) {
            state.errorsForCurrentRequest.add(
                WebViewError(
                    error.errorCode,
                    error.description.toString(),
                ),
            )
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        KLogger.d {
            "shouldOverrideUrlLoading: ${request?.url} ${request?.isForMainFrame} ${request?.isRedirect} ${request?.method}"
        }
        if (isRedirect || request == null || navigator.requestInterceptor == null) {
            isRedirect = false
            return super.shouldOverrideUrlLoading(view, request)
        }
        val isRedirectRequest =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                request.isRedirect
            } else {
                false
            }
        val webRequest =
            WebRequest(
                request.url.toString(),
                request.requestHeaders?.toMutableMap() ?: mutableMapOf(),
                request.isForMainFrame,
                isRedirectRequest,
                request.method ?: "GET",
            )
        val interceptResult =
            navigator.requestInterceptor!!.onInterceptUrlRequest(
                webRequest,
                navigator,
            )
        return when (interceptResult) {
            is WebRequestInterceptResult.Allow -> {
                false
            }

            is WebRequestInterceptResult.Reject -> {
                true
            }

            is WebRequestInterceptResult.Modify -> {
                isRedirect = true
                interceptResult.request.apply {
                    navigator.stopLoading()
                    navigator.loadUrl(this.url, this.headers)
                }
                true
            }
        }
    }
}

/**
 * AccompanistWebChromeClient
 *
 * A parent class implementation of WebChromeClient that can be subclassed to add custom behaviour.
 *
 * As Accompanist Web needs to set its own web client to function, it provides this intermediary
 * class that can be overriden if further custom behaviour is required.
 */
@Suppress("DEPRECATION")
open class AccompanistWebChromeClient : WebChromeClient() {
    private var windowManager: WindowManager? = null
    private var context: Context? = null

    fun setWindowManager(windowManager: WindowManager?, context: Context) {
        this.windowManager = windowManager
        this.context = context

    }

    open lateinit var navigator: WebViewNavigator
        internal set
    open lateinit var state: WebViewState
        internal set
    private var lastLoadedUrl = ""

    override fun onReceivedTitle(
        view: WebView,
        title: String?,
    ) {
        super.onReceivedTitle(view, title)
        KLogger.d {
            "onReceivedTitle: $title url:${view.url}"
        }
        state.pageTitle = title
        state.lastLoadedUrl = view.url ?: ""
    }

    override fun onReceivedIcon(
        view: WebView,
        icon: Bitmap?,
    ) {
        super.onReceivedIcon(view, icon)
//        state.pageIcon = icon
    }


    override fun onProgressChanged(
        view: WebView,
        newProgress: Int,
    ) {
        super.onProgressChanged(view, newProgress)
        if (state.loadingState is LoadingState.Finished && view.url == lastLoadedUrl) return
        state.loadingState =
            if (newProgress == 100) {
                LoadingState.Finished
            } else {
                LoadingState.Loading(newProgress / 100.0f)
            }
        lastLoadedUrl = view.url ?: ""
    }


    override fun onJsAlert(
        view: WebView?,
        url: String?,
        message: String?,
        result: JsResult?
    ): Boolean {
        navigator.onJsAlert(message ?: return super.onJsAlert(view, url, message, result)) {
            result?.confirm()
        }
        return true
    }

    private var fullScreenView: ViewGroup? = null
    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        val v = view ?: return
        val ctx = context as? Activity ?: return
        val win = windowManager ?: return

        val rootView = FrameLayout(ctx).apply {
            addView(
                v,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    this.gravity = android.view.Gravity.CENTER
                }
            )
        }
        // 进入视频全屏
        ctx.window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        ctx.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; // 横屏
        @Suppress("DEPRECATION")
        ctx.window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        // 此处的 view 就是全屏的视频播放界面，需要把它添加到我们的界面上
        // 设置全屏参数
        val layoutParams = WindowManager.LayoutParams();
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.gravity = android.view.Gravity.CENTER
        @Suppress("DEPRECATION")
        layoutParams.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LOW_PROFILE

        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION
        @Suppress("DEPRECATION")
        layoutParams.flags = (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_FULLSCREEN
                or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        layoutParams.format = android.graphics.PixelFormat.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            layoutParams.blurBehindRadius = 5
        }
        win.addView(rootView, layoutParams)
        state.fullscreenState = true
        fullScreenView = rootView
    }


    override fun onHideCustomView() {
        val f = fullScreenView
        fullScreenView = null
        val ctx = context as? Activity ?: return kotlin.run {
            // 退出全屏播放，我们要把之前添加到界面上的视频播放界面移除
            windowManager?.removeViewImmediate(f)
            f?.removeAllViews()
            state.fullscreenState = false
        }
        ctx.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; // 横屏
        ctx.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        ctx.window.clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        // 退出全屏播放，我们要把之前添加到界面上的视频播放界面移除
        windowManager?.removeViewImmediate(f)
        f?.removeAllViews()
        state.fullscreenState = false
    }

}
