package com.multiplatform.webview.web

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.platform.LocalDensity
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.util.toUIColor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSDate
import platform.Foundation.setValue
import platform.UIKit.UIDevice
import platform.UIKit.UIResponder
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewController
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
    val navigationDelegate = remember { WKNavigationDelegate(state, navigator) }
    val scope = rememberCoroutineScope()

    val scale = UIScreen.mainScreen.scale.toFloat()
    with(LocalDensity.current) {
        UIKitView(
            factory = {
                val dataStore = WKWebsiteDataStore.defaultDataStore()
                val date = NSDate(timeIntervalSinceReferenceDate = 0.0)
                dataStore.removeDataOfTypes(
                    WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince = date
                ) {
                    println("Cache cleared")
                }

                val config = WKWebViewConfiguration().apply {
                    allowsInlineMediaPlayback = true
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
                    onCreated(this)
                    state.viewState?.let {
                        this.interactionState = it
                    }
                    allowsBackForwardNavigationGestures = captureBackPresses
                    customUserAgent = state.webSettings.customUserAgentString
                    this.addProgressObservers(
                        observer = observer,
                    )
                    this.inspectable = true
//                        this.UIDelegate = navigationDelegate
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
                            scrollEnabled = it.scrollEnabled
                            showsHorizontalScrollIndicator = it.showHorizontalScrollIndicator
                            showsVerticalScrollIndicator = it.showVerticalScrollIndicator
                        }
                    }
                }.also {
                    val iosWebView = IOSWebView(it, scope, webViewJsBridge)
                    state.webSettings.let {
                        iosWebView.setupSettings(it)
                    }
                    state.webView = iosWebView
                    webViewJsBridge?.webView = iosWebView
                }
            },
            background = Color.White,
            modifier = modifier.then(Modifier.pointerInput(Unit) {
                // 拦截触摸
                detectTapGestures(
                    onPress = {
                        val offset = it
                        state.webView?.evaluateJavaScript(
                            performClickAction(offset, scale),
                        ){
                            println("evaluateJavaScript $it")
                        }
                    },
                    onLongPress = { }
                )
            }),
            /* .then(Modifier.pointerInput(Unit) {
             detectTapGestures(onPress = {
                 onTapTransformer.value = it
             })
         })*/
//                update = {
//                    if(it.superview!=null){
//                        it.superview!!.apply {
//                            println("$this  ${this.findViewController()}")
//                        }
//                    }
////                    it.setFrame(CGRectMake(0.0, 0.0, maxWidth.value.toDouble(), heightMeasure.toDouble()))
//                },
//                onResize = { view, rect ->
//
//                },
            onRelease = {
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
