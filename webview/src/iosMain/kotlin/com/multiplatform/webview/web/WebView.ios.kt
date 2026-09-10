package com.multiplatform.webview.web

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import com.multiplatform.webview.jsbridge.ConsoleBridge
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.setting.PlatformWebSettings.MediaTypesRequiringUserActionForPlayback.ALL
import com.multiplatform.webview.setting.PlatformWebSettings.MediaTypesRequiringUserActionForPlayback.AUDIO
import com.multiplatform.webview.setting.PlatformWebSettings.MediaTypesRequiringUserActionForPlayback.NONE
import com.multiplatform.webview.setting.PlatformWebSettings.MediaTypesRequiringUserActionForPlayback.VIDEO
import com.multiplatform.webview.util.toUIColor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.Foundation.setValue
import platform.UIKit.UIEdgeInsetsZero
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.UIKit.UIScreen
import platform.WebKit.WKAudiovisualMediaTypeAll
import platform.WebKit.WKAudiovisualMediaTypeAudio
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.WebKit.WKAudiovisualMediaTypeVideo
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.javaScriptEnabled

@Composable
actual fun ActualWebView(
    state: WebViewState,
    modifier: Modifier,
    captureBackPresses: Boolean,
    navigator: WebViewNavigator,
    webViewJsBridge: WebViewJsBridge?,
    consoleBridge: ConsoleBridge?,
    onCreated: (NativeWebView) -> Unit,
    onDispose: (NativeWebView) -> Unit,
    platformWebViewParams: PlatformWebViewParams?,
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

actual data class WebViewFactoryParam(
    val config: WKWebViewConfiguration,
)

actual class PlatformWebViewParams

@OptIn(ExperimentalForeignApi::class)
actual fun defaultWebViewFactory(param: WebViewFactoryParam) =
    WKWebView(frame = CGRectZero.readValue(), configuration = param.config)

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
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
    val observer = remember { WKWebViewObserver(state = state, navigator = navigator) }
    val scope = rememberCoroutineScope()
    val navigationDelegate = remember { WKNavigationDelegate(state, navigator, scope) }

    with(LocalDensity.current) {
        BoxWithConstraints(modifier = modifier) {
            UIKitView(
                factory = {
                    val config =
                        WKWebViewConfiguration().apply {
                            allowsInlineMediaPlayback = true
                            mediaTypesRequiringUserActionForPlayback =
                                when (state.webSettings.iOSWebSettings.mediaTypesRequiringUserActionForPlayback) {
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
                            setValue(
                                state.webSettings.allowUniversalAccessFromFileURLs,
                                forKey = "allowUniversalAccessFromFileURLs",
                            )
                        }

                    factory(WebViewFactoryParam(config)).apply {
                        val minInspectableVersion =
                            cValue<NSOperatingSystemVersion> {
                                majorVersion = 16
                                minorVersion = 4
                                patchVersion = 0
                            }
                        if (NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(minInspectableVersion)) {
                            // Keep common fork setting as the compatibility source of truth.
                            setInspectable(state.webSettings.isInspectable || state.webSettings.iOSWebSettings.isInspectable)
                        }

                        setFrame(
                            CGRectMake(
                                0.0,
                                0.0,
                                maxWidth.value.toDouble(),
                                maxHeight.value.toDouble().coerceAtLeast(120.0),
                            ),
                        )
                        onCreated(this)
                        state.viewState?.let { interactionState = it }
                        allowsBackForwardNavigationGestures = captureBackPresses
                        customUserAgent = state.webSettings.customUserAgentString
                        addProgressObservers(observer)
                        this.navigationDelegate = navigationDelegate
                        this.UIDelegate = navigationDelegate

                        state.webSettings.let {
                            val backgroundColor =
                                (it.iOSWebSettings.backgroundColor ?: it.backgroundColor).toUIColor()
                            val scrollViewColor =
                                (it.iOSWebSettings.underPageBackgroundColor ?: it.backgroundColor).toUIColor()
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
                                alwaysBounceHorizontal = it.bounces
                                alwaysBounceVertical = it.bounces
                                scrollEnabled = it.scrollEnabled
                                showsHorizontalScrollIndicator = it.showHorizontalScrollIndicator
                                showsVerticalScrollIndicator = it.showVerticalScrollIndicator
                                contentInset = UIEdgeInsetsZero.readValue()
                                contentInsetAdjustmentBehavior =
                                    UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
                            }
                        }
                    }.also {
                        val iosWebView = IOSWebView(it, scope, webViewJsBridge)
                        state.webView = iosWebView
                        webViewJsBridge?.webView = iosWebView
                        it.backgroundColor = Color.Transparent.toUIColor()
                    }
                },
                modifier = modifier,
                onRelease = {
                    val wrapper = state.webView
                    it.removeProgressObservers(observer)
                    it.navigationDelegate = null
                    it.UIDelegate = null
                    wrapper?.destroy()
                    state.webView = null
                    onDispose(it)
                },
                // Preserve the fork's cooperative touch behavior; changing it alters gesture delivery.
                properties =
                    UIKitInteropProperties(
                        interactionMode = UIKitInteropInteractionMode.Cooperative(delayMillis = 1),
                        isNativeAccessibilityEnabled = false,
                    ),
            )
        }
    }
}
