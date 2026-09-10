package com.multiplatform.webview.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.multiplatform.webview.jsbridge.ConsoleBridge
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.setting.PlatformWebSettings
import com.multiplatform.webview.util.InternalStoragePathHandler
import com.multiplatform.webview.util.KLogger

@Composable
fun AccompanistWebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    captureBackPresses: Boolean = true,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    webViewJsBridge: WebViewJsBridge? = null,
    consoleBridge: ConsoleBridge? = null,
    onCreated: (WebView) -> Unit = {},
    onDispose: (WebView) -> Unit = {},
    client: AccompanistWebViewClient = remember { AccompanistWebViewClient() },
    chromeClient: AccompanistWebChromeClient = remember { AccompanistWebChromeClient() },
    factory: ((Context) -> WebView)? = null,
) {
    BoxWithConstraints(modifier) {
        val width = if (constraints.hasFixedWidth) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        val height = if (constraints.hasFixedHeight) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        AccompanistWebView(
            state = state,
            layoutParams = FrameLayout.LayoutParams(width, height),
            modifier = Modifier,
            captureBackPresses = captureBackPresses,
            navigator = navigator,
            webViewJsBridge = webViewJsBridge,
            consoleBridge = consoleBridge,
            onCreated = onCreated,
            onDispose = onDispose,
            client = client,
            chromeClient = chromeClient,
            factory = factory,
        )
    }
}

@Composable
@Suppress("DEPRECATION")
fun AccompanistWebView(
    state: WebViewState,
    layoutParams: FrameLayout.LayoutParams,
    modifier: Modifier = Modifier,
    captureBackPresses: Boolean = true,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    webViewJsBridge: WebViewJsBridge? = null,
    consoleBridge: ConsoleBridge? = null,
    onCreated: (WebView) -> Unit = {},
    onDispose: (WebView) -> Unit = {},
    client: AccompanistWebViewClient = remember { AccompanistWebViewClient() },
    chromeClient: AccompanistWebChromeClient = remember { AccompanistWebChromeClient() },
    factory: ((Context) -> WebView)? = null,
) {
    val webView = state.webView
    val scope = rememberCoroutineScope()
    BackHandler(captureBackPresses && navigator.canGoBack) { webView?.goBack() }

    client.state = state
    client.navigator = navigator
    chromeClient.state = state
    chromeClient.navigator = navigator

    AndroidView(
        factory = { context ->
            (factory?.invoke(context) ?: WebView(context)).apply {
                WebView.setWebContentsDebuggingEnabled(state.webSettings.isInspectable)
                onCreated(this)
                this.layoutParams = layoutParams
                state.viewState?.let { restoreState(it) }
                chromeClient.context = context
                webChromeClient = chromeClient
                webViewClient = client

                val desiredLayerType = when (state.webSettings.androidWebSettings.layerType) {
                    PlatformWebSettings.AndroidWebSettings.LayerType.NONE -> View.LAYER_TYPE_NONE
                    PlatformWebSettings.AndroidWebSettings.LayerType.SOFTWARE -> View.LAYER_TYPE_SOFTWARE
                    PlatformWebSettings.AndroidWebSettings.LayerType.HARDWARE -> View.LAYER_TYPE_HARDWARE
                    else -> View.LAYER_TYPE_HARDWARE
                }
                setLayerType(desiredLayerType, null)

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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = it.safeBrowsingEnabled
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) isAlgorithmicDarkeningAllowed = it.isAlgorithmicDarkeningAllowed
                        setBackgroundColor(state.webSettings.backgroundColor.toArgb())
                        allowFileAccess = it.allowFileAccess
                        textZoom = it.textZoom
                        useWideViewPort = it.useWideViewPort
                        if (it.useWideViewPort) {
                            setInitialScale(1)
                            loadWithOverviewMode = true
                            @Suppress("DEPRECATION")
                            layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
                        } else {
                            loadWithOverviewMode = false
                        }
                        standardFontFamily = it.standardFontFamily
                        defaultFontSize = it.defaultFontSize
                        loadsImagesAutomatically = it.loadsImagesAutomatically
                        domStorageEnabled = it.domStorageEnabled
                        mediaPlaybackRequiresUserGesture = it.mediaPlaybackRequiresUserGesture
                        client.assetLoader = if (it.enableSandbox) {
                            WebViewAssetLoader.Builder()
                                .addPathHandler(it.sandboxSubdomain, InternalStoragePathHandler())
                                .build()
                        } else null
                    }
                }

                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    WebSettingsCompat.setForceDark(
                        settings,
                        if (night == Configuration.UI_MODE_NIGHT_YES) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF,
                    )
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                        WebSettingsCompat.setForceDarkStrategy(settings, WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY)
                    }
                }
            }.also {
                val androidWebView = AndroidWebView(it, scope, webViewJsBridge, consoleBridge)
                state.webView = androidWebView
                webViewJsBridge?.webView = androidWebView
            }
        },
        update = { currentWebView ->
            val current = currentWebView.layoutParams
            if (current == null || current.width != layoutParams.width || current.height != layoutParams.height) {
                currentWebView.layoutParams = FrameLayout.LayoutParams(layoutParams)
            }
            currentWebView.requestLayout()
            currentWebView.invalidate()
        },
        modifier = modifier,
        onReset = {},
        onRelease = { releasedWebView ->
            chromeClient.hideCustomViewIfNeeded()
            onDispose(releasedWebView)
        },
    )
}

open class AccompanistWebViewClient : WebViewClient() {
    open lateinit var state: WebViewState
        internal set
    open lateinit var navigator: WebViewNavigator
        internal set
    private var isRedirect = false
    var assetLoader: WebViewAssetLoader? = null

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        state.loadingState = LoadingState.Loading(0.0f)
        state.errorsForCurrentRequest.clear()
        state.pageTitle = null
        state.lastLoadedUrl = url
        val supportZoom = if (state.webSettings.supportZoom) "yes" else "no"
        @Suppress("ktlint:standard:max-line-length")
        val script = "var meta=document.createElement('meta');meta.setAttribute('name','viewport');meta.setAttribute('content','width=device-width, initial-scale=${state.webSettings.zoomLevel}, maximum-scale=${state.webSettings.zoomLevel}, minimum-scale=${state.webSettings.zoomLevel},user-scalable=$supportZoom');document.getElementsByTagName('head')[0].appendChild(meta);"
        navigator.evaluateJavaScript(script)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val requestUrl = request?.url
        return requestUrl?.let { assetLoader?.shouldInterceptRequest(it) } ?: super.shouldInterceptRequest(view, request)
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        if (error?.primaryError != SslError.SSL_IDMISMATCH) {
            super.onReceivedSslError(view, handler, error)
            return
        }
        val sslHandler = handler ?: return
        val requestHost = runCatching { Uri.parse(error.url).host?.lowercase() }.getOrNull()
        val allowed = requestHost != null && state.webSettings.sslPiningHosts.any { configured ->
            val host = configured.trim().trim('.').lowercase()
            when {
                host.isEmpty() -> false
                requestHost == host -> true
                requestHost.endsWith(".$host") -> requestHost.removeSuffix(".$host").let { it.isNotEmpty() && !it.contains('.') }
                else -> false
            }
        }
        if (allowed) sslHandler.proceed() else sslHandler.cancel()
    }

    @Deprecated("Deprecated in Java")
    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
        when {
            errorCode == ERROR_HOST_LOOKUP && description == "INTERNET_DISCONNECTED" -> state.loadingState = LoadingState.ErrorLoading("网络加载失败，请重新检查网络")
            description == "ADDRESS_UNREACHABLE" -> state.loadingState = LoadingState.ErrorLoading("网络加载失败，请重新检查网络")
        }
        super.onReceivedError(view, errorCode, description, failingUrl)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        state.loadingState = LoadingState.Finished
        state.lastLoadedUrl = url
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        navigator.canGoBack = view.canGoBack()
        navigator.canGoForward = view.canGoForward()
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || error == null) return
        state.errorsForCurrentRequest.add(WebViewError(error.errorCode, error.description.toString(), request?.isForMainFrame ?: false))
        if (request?.isForMainFrame == true && (error.errorCode == ERROR_HOST_LOOKUP || error.errorCode == ERROR_CONNECT)) {
            state.loadingState = LoadingState.ErrorLoading("网络加载失败，请重新检查网络")
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (isRedirect || request == null || navigator.requestInterceptor == null) {
            isRedirect = false
            return super.shouldOverrideUrlLoading(view, request)
        }
        val webRequest = WebRequest(
            request.url.toString(),
            request.requestHeaders?.toMutableMap() ?: mutableMapOf(),
            request.isForMainFrame,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) request.isRedirect else false,
            request.method ?: "GET",
        )
        return when (val result = navigator.requestInterceptor!!.onInterceptUrlRequest(webRequest, navigator)) {
            is WebRequestInterceptResult.Allow -> false
            is WebRequestInterceptResult.Reject -> true
            is WebRequestInterceptResult.Modify -> {
                isRedirect = true
                navigator.stopLoading()
                navigator.loadUrl(result.request.url, result.request.headers)
                true
            }
        }
    }
}

@Suppress("DEPRECATION")
open class AccompanistWebChromeClient : WebChromeClient() {
    open lateinit var navigator: WebViewNavigator
        internal set
    open lateinit var state: WebViewState
        internal set
    lateinit var context: Context
        internal set

    private var lastLoadedUrl = ""
    private var fullScreenView: ViewGroup? = null
    private var fullScreenCallback: CustomViewCallback? = null
    private var previousOrientation: Int? = null
    private var previousSystemUiVisibility: Int? = null
    private var hadFullscreenFlag = false

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        runCatching {
            val msg = consoleMessage ?: return super.onConsoleMessage(consoleMessage)
            val level = when (msg.messageLevel()) {
                ConsoleMessage.MessageLevel.ERROR -> "error"
                ConsoleMessage.MessageLevel.WARNING -> "warn"
                ConsoleMessage.MessageLevel.DEBUG -> "debug"
                ConsoleMessage.MessageLevel.TIP -> "info"
                else -> "log"
            }
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            state.webView?.consoleBridge?.emitFromPlatform(level, msg.message(), msg.sourceId(), msg.lineNumber(), formatter.format(java.util.Date()))
        }
        return super.onConsoleMessage(consoleMessage)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        state.pageTitle = title
        state.lastLoadedUrl = view.url ?: ""
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        if (state.loadingState is LoadingState.Finished && view.url == lastLoadedUrl) return
        state.loadingState = if (newProgress == 100) LoadingState.Finished else LoadingState.Loading(newProgress / 100.0f)
        lastLoadedUrl = view.url ?: ""
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        val alertMessage = message ?: return super.onJsAlert(view, url, message, result)
        navigator.onJsAlert(alertMessage) { result?.confirm() }
        return true
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val granted = mutableListOf<String>()
        request.resources.forEach { resource ->
            var permission: String? = null
            when (resource) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> permission = android.Manifest.permission.RECORD_AUDIO
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> permission = android.Manifest.permission.CAMERA
                PermissionRequest.RESOURCE_MIDI_SYSEX -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && state.webSettings.androidWebSettings.allowMidiSysexMessages) granted += resource
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> if (state.webSettings.androidWebSettings.allowProtectedMedia) granted += resource
            }
            if (permission != null && ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) granted += resource
        }
        if (granted.isNotEmpty()) request.grant(granted.toTypedArray()) else request.deny()
    }

    override fun getDefaultVideoPoster(): Bitmap? = if (state.webSettings.androidWebSettings.hideDefaultVideoPoster) createBitmap(50, 50) else super.getDefaultVideoPoster()

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        val customView = view ?: return
        val activity = context as? Activity ?: run { callback?.onCustomViewHidden(); return }
        if (fullScreenView != null) { callback?.onCustomViewHidden(); return }
        val decor = activity.window.decorView as? ViewGroup ?: run { callback?.onCustomViewHidden(); return }
        (customView.parent as? ViewGroup)?.removeView(customView)
        val root = FrameLayout(activity).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(customView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        previousOrientation = activity.requestedOrientation
        previousSystemUiVisibility = activity.window.decorView.systemUiVisibility
        hadFullscreenFlag = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0
        fullScreenCallback = callback
        fullScreenView = root

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        decor.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.bringToFront()
        state.fullscreenState = true
    }

    override fun onHideCustomView() = hideCustomViewIfNeeded()

    internal fun hideCustomViewIfNeeded() {
        val root = fullScreenView ?: return
        fullScreenView = null
        val callback = fullScreenCallback
        fullScreenCallback = null
        (root.parent as? ViewGroup)?.removeView(root)
        root.removeAllViews()
        val activity = (if (::context.isInitialized) context else null) as? Activity
        if (activity != null) {
            previousOrientation?.let { activity.requestedOrientation = it }
            previousSystemUiVisibility?.let { activity.window.decorView.systemUiVisibility = it }
            if (hadFullscreenFlag) activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        previousOrientation = null
        previousSystemUiVisibility = null
        hadFullscreenFlag = false
        state.fullscreenState = false
        callback?.onCustomViewHidden()
    }
}
