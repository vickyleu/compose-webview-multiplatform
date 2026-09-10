@file:Suppress(
    "PARAMETER_NAME_CHANGED_ON_OVERRIDE",
    "DIFFERENT_NAMES_FOR_THE_SAME_PARAMETER_IN_SUPERTYPES",
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
import platform.CoreGraphics.CGPointMake
import platform.Foundation.HTTPMethod
import platform.Foundation.NSError
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
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

class WKNavigationDelegate(
    private val state: WebViewState,
    private val navigator: WebViewNavigator,
    @Suppress("UNUSED_PARAMETER") private val scope: CoroutineScope,
) : NSObject(), WKNavigationDelegateProtocol, WKUIDelegateProtocol {
    private var isRedirect = false

    override fun webView(
        webView: WKWebView,
        runJavaScriptAlertPanelWithMessage: String,
        initiatedByFrame: WKFrameInfo,
        completionHandler: () -> Unit,
    ) {
        navigator.onJsAlert(runJavaScriptAlertPanelWithMessage) {
            completionHandler()
        }
    }

    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures,
    ): WKWebView? {
        val targetFrame = forNavigationAction.targetFrame
        if (targetFrame == null || !targetFrame.isMainFrame()) {
            webView.loadRequest(forNavigationAction.request)
        }
        return null
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didStartProvisionalNavigation: WKNavigation?,
    ) {
        state.loadingState = LoadingState.Loading(0f)
        state.lastLoadedUrl = webView.URL?.absoluteString
        state.errorsForCurrentRequest.clear()
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didCommitNavigation: WKNavigation?,
    ) {
        val supportZoom = if (state.webSettings.supportZoom) "yes" else "no"
        @Suppress("ktlint:standard:max-line-length")
        val script =
            "var meta=document.querySelector('meta[name=viewport]')||document.createElement('meta');meta.setAttribute('name','viewport');meta.setAttribute('content','width=device-width, initial-scale=${state.webSettings.zoomLevel}, maximum-scale=10.0, minimum-scale=0.1, user-scalable=$supportZoom');if(!meta.parentNode){document.getElementsByTagName('head')[0].appendChild(meta);}"
        webView.evaluateJavaScript(script) { _, error ->
            if (error != null) KLogger.e { "viewport injection failed: $error" }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun webView(
        webView: WKWebView,
        didReceiveAuthenticationChallenge: NSURLAuthenticationChallenge,
        completionHandler: (platform.Foundation.NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
    ) {
        val protectionSpace = didReceiveAuthenticationChallenge.protectionSpace
        if (protectionSpace.authenticationMethod != NSURLAuthenticationMethodServerTrust) {
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            return
        }

        val host = protectionSpace.host.lowercase()
        val allowlisted =
            state.webSettings.sslPiningHosts.any { configured ->
                val allowed = configured.trim().lowercase().trimStart('.').removeSuffix(".")
                allowed.isNotEmpty() && (host == allowed || host.endsWith(".$allowed"))
            }

        val trust = protectionSpace.serverTrust
        if (allowlisted && trust != null) {
            KLogger.w { "Using fork SSL allowlist compatibility for host=$host" }
            completionHandler(
                NSURLSessionAuthChallengeUseCredential,
                NSURLCredential.credentialForTrust(trust),
            )
        } else {
            // Normal hosts keep Apple's default certificate validation. The allowlist never widens
            // trust beyond explicitly configured hosts.
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFinishNavigation: WKNavigation?,
    ) {
        state.pageTitle = webView.title
        state.lastLoadedUrl = webView.URL?.absoluteString
        state.loadingState = LoadingState.Finished
        navigator.canGoBack = webView.canGoBack
        navigator.canGoForward = webView.canGoForward
        if (getPlatformVersionDouble() < 15.0 && state.scrollOffset.notZero()) {
            webView.scrollView.setContentOffset(
                CGPointMake(
                    x = state.scrollOffset.first.toDouble(),
                    y = state.scrollOffset.second.toDouble(),
                ),
                true,
            )
        }
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) {
        KLogger.e { "WebView loading failed: ${withError.localizedDescription}" }
        state.errorsForCurrentRequest.add(
            WebViewError(
                code = withError.code.toInt(),
                description = withError.localizedDescription,
                isFromMainFrame = true,
            ),
        )
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString
        if (
            url != null &&
            !isRedirect &&
            navigator.requestInterceptor != null &&
            decidePolicyForNavigationAction.targetFrame?.mainFrame != false
        ) {
            val request = decidePolicyForNavigationAction.request
            val headerMap = mutableMapOf<String, String>()
            request.allHTTPHeaderFields?.forEach {
                headerMap[it.key.toString()] = it.value.toString()
            }
            val webRequest =
                WebRequest(
                    request.URL?.absoluteString ?: "",
                    headerMap,
                    decidePolicyForNavigationAction.targetFrame?.mainFrame ?: true,
                    isRedirect,
                    request.HTTPMethod ?: "GET",
                )
            when (val result = navigator.requestInterceptor!!.onInterceptUrlRequest(webRequest, navigator)) {
                is WebRequestInterceptResult.Allow ->
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                is WebRequestInterceptResult.Reject ->
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                is WebRequestInterceptResult.Modify -> {
                    isRedirect = true
                    navigator.stopLoading()
                    navigator.loadUrl(result.request.url, result.request.headers)
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                }
            }
        } else {
            isRedirect = false
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        }
    }
}
