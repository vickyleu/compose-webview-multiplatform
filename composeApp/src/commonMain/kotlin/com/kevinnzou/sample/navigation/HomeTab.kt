package com.kevinnzou.sample.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.util.KLogSeverity
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberSaveableWebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator

/**
 * Created By Kevin Zou On 2023/12/8
 */
@Composable
fun Home() {
    val url = "https://www.jetbrains.com/lp/compose-multiplatform/"
    val webViewState =
        rememberSaveableWebViewState(url).apply {
            webSettings.logSeverity = KLogSeverity.Debug
        }

    val navigator = rememberWebViewNavigator()

    LaunchedEffect(navigator) {
        val bundle = webViewState.viewState
        if (bundle == null) {
            // This is the first time load, so load the home page.
            navigator.loadUrl(url)
        }
    }

    WebView(
        state = webViewState,
        modifier = Modifier.fillMaxSize().padding(bottom = 45.dp),
        navigator = navigator,
    )
}
