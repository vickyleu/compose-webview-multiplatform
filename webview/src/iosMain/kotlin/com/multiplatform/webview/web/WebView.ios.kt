package com.multiplatform.webview.web

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.platform.LocalDensity
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.setting.PlatformWebSettings.MediaTypesRequiringUserActionForPlayback.ALL
import com.multiplatform.webview.setting.PlatformWebSettings.MediaTypesRequiringUserActionForPlayback.AUDIO
import com.multiplatform.webview.setting.PlatformWebSettings.MediaTypesRequiringUserActionForPlayback.NONE
import com.multiplatform.webview.setting.PlatformWebSettings.MediaTypesRequiringUserActionForPlayback.VIDEO
import com.multiplatform.webview.util.toUIColor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSDate
import platform.Foundation.setValue
import platform.UIKit.UIEdgeInsetsZero
import platform.UIKit.UIResponder
import platform.UIKit.UIScreen
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.WebKit.WKAudiovisualMediaTypeAll
import platform.WebKit.WKAudiovisualMediaTypeAudio
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.WebKit.WKAudiovisualMediaTypeVideo
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.javaScriptEnabled

/**
 * iOS WebView implementation.
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
    IOSWebView(
        state = state,
        modifier = modifier,
        captureBackPresses = captureBackPresses,
        navigator = navigator,
        webViewJsBridge = webViewJsBridge,
        onCreated = onCreated,
        onDispose = onDispose,
        factory = factory,
    )
}

/** iOS WebView factory parameters: configuration created from WebSettings. */
actual data class WebViewFactoryParam(val config: WKWebViewConfiguration)

/** Default WebView factory for iOS. */
@OptIn(ExperimentalForeignApi::class)
actual fun defaultWebViewFactory(param: WebViewFactoryParam) =
    WKWebView(frame = CGRectZero.readValue(), configuration = param.config)

fun Size.isSmallerThan(other: Size): Boolean {
    return this.width * this.height < other.width * other.height
}

fun Size.isLargerThan(other: Size): Boolean {
    return this.width * this.height > other.width * other.height
}

/**
 * iOS WebView implementation.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
fun IOSWebView(
    state: WebViewState,
    modifier: Modifier,
    captureBackPresses: Boolean,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    onCreated: (NativeWebView) -> Unit,
    onDispose: (NativeWebView) -> Unit,
    factory: (WebViewFactoryParam) -> NativeWebView,
) {
    val observer = remember {
        WKWebViewObserver(
            state = state,
            navigator = navigator,
        )
    }
    val scope = rememberCoroutineScope()
    val navigationDelegate = remember { WKNavigationDelegate(state, navigator,scope) }
    val scale = UIScreen.mainScreen.scale.toFloat()
    with(LocalDensity.current) {
        BoxWithConstraints(
            modifier = modifier.then(Modifier),
        ) {
            UIKitView(
                factory = {
                    /*val dataStore = WKWebsiteDataStore.defaultDataStore()
                    val date = NSDate(timeIntervalSinceReferenceDate = 0.0)
                    dataStore.removeDataOfTypes(
                        WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince = date
                    ) {
                        println("Cache cleared")
                    }*/

                    val config = WKWebViewConfiguration().apply {
                        allowsInlineMediaPlayback = true
                        mediaTypesRequiringUserActionForPlayback = when(state.webSettings.iOSWebSettings.mediaTypesRequiringUserActionForPlayback){
                            ALL -> WKAudiovisualMediaTypeAll
                            AUDIO -> WKAudiovisualMediaTypeAudio
                            VIDEO -> WKAudiovisualMediaTypeVideo
                            NONE -> WKAudiovisualMediaTypeNone
                        }
                        defaultWebpagePreferences.allowsContentJavaScript =
                            state.webSettings.isJavaScriptEnabled
                        preferences.apply {
                            setValue(
                                state.webSettings.allowFileAccessFromFileURLs,
                                forKey = "allowFileAccessFromFileURLs",
                            )
                            javaScriptEnabled = state.webSettings.isJavaScriptEnabled
                        }
                        this.setValue(
                            state.webSettings.allowUniversalAccessFromFileURLs,
                            forKey = "allowUniversalAccessFromFileURLs",
                        )
                    }
                    factory(WebViewFactoryParam(config)).apply {
                        this.setInspectable(true)
                        setFrame(CGRectMake(0.0, 0.0,maxWidth.value.toDouble(),120.0))
                        onCreated(this)
                        state.viewState?.let {
                            this.interactionState = it
                        }
                        allowsBackForwardNavigationGestures = captureBackPresses
                        customUserAgent = state.webSettings.customUserAgentString
                        this.addProgressObservers(
                            observer = observer,
                        )
                        this.navigationDelegate = navigationDelegate
                        state.webSettings.let {
                            val backgroundColor =
                                (it.iOSWebSettings.backgroundColor ?: it.backgroundColor).toUIColor()
                            val scrollViewColor = (it.iOSWebSettings.underPageBackgroundColor
                                ?: it.backgroundColor).toUIColor()
                            setOpaque(it.iOSWebSettings.opaque)
                            if (!it.iOSWebSettings.opaque) {
                                setBackgroundColor(backgroundColor)
                                scrollView.setBackgroundColor(scrollViewColor)
                            }
                            scrollView.pinchGestureRecognizer?.enabled = it.supportZoom
                        }
                        state.webSettings.iOSWebSettings.let {
                            with(scrollView) {
                                bounces = it.bounces
                                alwaysBounceHorizontal = bounces
                                alwaysBounceVertical = bounces

                                scrollEnabled = it.scrollEnabled
                                showsHorizontalScrollIndicator = it.showHorizontalScrollIndicator
                                showsVerticalScrollIndicator = it.showVerticalScrollIndicator
                                contentInset = UIEdgeInsetsZero.readValue()
//                                scrollIndicatorInsets = UIEdgeInsetsZero.readValue()
                                setContentInsetAdjustmentBehavior(UIScrollViewContentInsetAdjustmentBehavior.
                                UIScrollViewContentInsetAdjustmentNever)
//                                adjustedContentInset = UIEdgeInsetsZero.readValue()
                            }
                        }
                    }.also {
                        val iosWebView = IOSWebView(it, scope, webViewJsBridge)
                        state.webView = iosWebView
                        webViewJsBridge?.webView = iosWebView
                    }
                },
                background = Color.White,
                modifier = modifier,
                onRelease = {
                    state.webView?.stopLoading()
                    state.webView = null
                    it.removeProgressObservers(
                        observer = observer,
                    )
                    it.navigationDelegate = null
                    onDispose(it)
                },
            )
        }
    }
}

private fun UIView.findViewController(): UIViewController? {
    var nextResponder: UIResponder? = this
    while (nextResponder != null) {
        if (nextResponder is UIViewController) {
            return nextResponder
        }
        nextResponder = nextResponder.nextResponder
    }
    return null
}

private fun performClickAction(offset: Offset, scale: Float): String {
    return """
                    var event = new MouseEvent('click', {
                        clientX: ${offset.x / scale},
                        clientY: ${offset.y / scale},
                        view: window,
                        bubbles: true,
                        cancelable: true
                    });
                    var element = document.elementFromPoint(${offset.x / scale}, ${offset.y / scale});
                    element.dispatchEvent(event);
   """.trimIndent()
}
