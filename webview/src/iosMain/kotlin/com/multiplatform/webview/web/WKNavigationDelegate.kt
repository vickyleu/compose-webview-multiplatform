@file:Suppress(
    "PARAMETER_NAME_CHANGED_ON_OVERRIDE",
    "DIFFERENT_NAMES_FOR_THE_SAME_PARAMETER_IN_SUPERTYPES"
)

package com.multiplatform.webview.web

import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.util.KLogger
import com.multiplatform.webview.util.getPlatformVersionDouble
import com.multiplatform.webview.util.notZero
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGPointMake
import platform.Foundation.HTTPMethod
import platform.Foundation.NSError
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.allHTTPHeaderFields
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.WebKit.WKFrameInfo
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject

/**
 * Created By Kevin Zou On 2023/9/13
 */

/**
 * Navigation delegate for the WKWebView
 */
@Suppress("CONFLICTING_OVERLOADS")
class WKNavigationDelegate(
    private val state: WebViewState,
    private val navigator: WebViewNavigator,
    private val scope: CoroutineScope,
) : NSObject(), WKNavigationDelegateProtocol, WKUIDelegateProtocol {
    private var isRedirect = false

    override fun webView(
        webView: WKWebView,
        runJavaScriptAlertPanelWithMessage: String,
        initiatedByFrame: WKFrameInfo,
        completionHandler: () -> Unit
    ) {
        navigator.onJsAlert(runJavaScriptAlertPanelWithMessage) {
            completionHandler()
        }
    }


    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ): WKWebView? {
        /**
         * WKFrameInfo *frameInfo = navigationAction.targetFrame;
         * if (![frameInfo isMainFrame]) {
         * [webView loadRequest:navigationAction.request];
         * }
         */
        KLogger.info {
            "createWebViewWithConfiguration"
        }
        val frameInfo = forNavigationAction.targetFrame
        if (frameInfo?.isMainFrame() == true) {
            webView.loadRequest(forNavigationAction.request)
        }
        return null
    }


    /**
     * Called when the web view begins to receive web content.
     */
    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didStartProvisionalNavigation: WKNavigation?,
    ) {
        state.loadingState = LoadingState.Loading(0f)
        state.lastLoadedUrl = webView.URL.toString()
        state.errorsForCurrentRequest.clear()
        KLogger.info {
            "didStartProvisionalNavigation"
        }
    }

    /**
     * Called when the web view receives a server redirect.
     */
    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didCommitNavigation: WKNavigation?,
    ) {
        val supportZoom = if (state.webSettings.supportZoom) "yes" else "no"

        @Suppress("ktlint:standard:max-line-length")
        val script = """
                            "(function(){
                                var meta = document.createElement('meta');
                                meta.setAttribute('name', 'viewport');
                                meta.setAttribute('content','width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no'); 
                                document.getElementsByTagName('head')[0].appendChild(meta);
                            })();
        """.trimIndent()
        webView.evaluateJavaScript(script) { _, err ->
            println("didCommitNavigation err:::${err}")
        }
        KLogger.info { "didCommitNavigation" }
    }


    @OptIn(ExperimentalForeignApi::class)
    override fun webView(
        webView: WKWebView,
        didReceiveAuthenticationChallenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
    ) {
        KLogger.info {
            "didReceiveAuthenticationChallenge ${didReceiveAuthenticationChallenge.protectionSpace.authenticationMethod}"
        }
        if (didReceiveAuthenticationChallenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    val credential =
                        NSURLCredential.credentialForTrust(didReceiveAuthenticationChallenge.protectionSpace.serverTrust)
                    completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
                    KLogger.info {
                        "didReceiveAuthenticationChallenge completionHandler ${credential}"
                    }
                }
            }
        }
    }

    /**
     * Called when the web view finishes loading.
     */
    @OptIn(ExperimentalForeignApi::class)
    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFinishNavigation: WKNavigation?,
    ) {
        state.pageTitle = webView.title
        state.lastLoadedUrl = webView.URL.toString()
        state.loadingState = LoadingState.Finished
        navigator.canGoBack = webView.canGoBack
        navigator.canGoForward = webView.canGoForward
        // Restore scroll position on iOS 14 and below
        if (getPlatformVersionDouble() < 15.0) {
            if (state.scrollOffset.notZero()) {
                webView.scrollView.setContentOffset(
                    CGPointMake(
                        x = state.scrollOffset.first.toDouble(),
                        y = state.scrollOffset.second.toDouble(),
                    ),
                    true,
                )
            }
        }
        KLogger.info { "didFinishNavigation ${state.lastLoadedUrl}" }
    }

    /**
     * Called when the web view fails to load content.
     */
    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) {
        KLogger.e {
            "WebView Loading Failed with error: ${withError.localizedDescription}"
        }
        state.errorsForCurrentRequest.add(
            WebViewError(
                withError.code.toInt(),
                withError.localizedDescription,
            ),
        )
        KLogger.e {
            "didFailNavigation"
        }
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString
        KLogger.info {
            "Outer decidePolicyForNavigationAction: $url $isRedirect $decidePolicyForNavigationAction"
        }
        if (url != null && !isRedirect &&
            navigator.requestInterceptor != null &&
            decidePolicyForNavigationAction.targetFrame?.mainFrame == true
        ) {
            navigator.requestInterceptor.apply {
                val request = decidePolicyForNavigationAction.request
                val headerMap = mutableMapOf<String, String>()
                request.allHTTPHeaderFields?.forEach {
                    headerMap[it.key.toString()] = it.value.toString()
                }
                KLogger.info {
                    "decidePolicyForNavigationAction: ${request.URL?.absoluteString}, $headerMap"
                }
                val webRequest =
                    WebRequest(
                        request.URL?.absoluteString ?: "",
                        headerMap,
                        decidePolicyForNavigationAction.targetFrame?.mainFrame ?: false,
                        isRedirect,
                        request.HTTPMethod ?: "GET",
                    )
                val interceptResult =
                    navigator.requestInterceptor.onInterceptUrlRequest(
                        webRequest,
                        navigator,
                    )
                when (interceptResult) {
                    is WebRequestInterceptResult.Allow -> {
                        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                    }

                    is WebRequestInterceptResult.Reject -> {
                        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                    }

                    is WebRequestInterceptResult.Modify -> {
                        isRedirect = true
                        interceptResult.request.apply {
                            navigator.stopLoading()
                            navigator.loadUrl(this.url, this.headers)
                        }
                        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                    }
                }
            }
        } else {
            isRedirect = false
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        }
    }
}
