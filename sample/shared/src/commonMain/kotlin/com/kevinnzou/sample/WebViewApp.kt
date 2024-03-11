package com.kevinnzou.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.BaseNavigator
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState

@Composable
internal fun WebViewApp() {
//    WebViewSample()
    BasicWebViewSample()
//    BasicWebViewWithHTMLSample()
}

@Composable
internal fun WebViewSample() {
    MaterialTheme {
        val webViewState =
            rememberWebViewState("https://github.com/KevinnZou/compose-webview-multiplatform")
        val navigator=  rememberWebViewNavigator()
        webViewState.webSettings.apply {
            isJavaScriptEnabled = true
            customUserAgentString =
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 11_1) AppleWebKit/625.20 (KHTML, like Gecko) Version/14.3.43 Safari/625.20"
            androidWebSettings.apply {
                isAlgorithmicDarkeningAllowed = true
                safeBrowsingEnabled = true
            }
        }
        navigator.setNavigatorListener(object :BaseNavigator{
            override suspend fun alert(message: String, callback: (Boolean) -> Unit) {

            }

            override suspend fun prompt(message: String, callback: (Boolean, String?) -> Unit) {

            }

            /**
             * Navigate to the given url.
             * return true will intercept the navigation.
             */
            override fun navigatorTo(url: String): Boolean {
                return true
            }

        })
        Column(Modifier.fillMaxSize()) {
            val text =
                webViewState.let {
                    "${it.pageTitle ?: ""} ${it.loadingState} ${it.lastLoadedUrl ?: ""}"
                }
            Text(text)
            WebView(
                state = webViewState,
                navigator = navigator,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

expect fun getPlatformName(): String
