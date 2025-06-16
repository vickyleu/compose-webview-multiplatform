package com.kevinnzou.sample.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewState

/**
 * Created By Kevin Zou On 2023/12/8
 */
@Composable
fun Personal() {
    val state = rememberWebViewState("https://www.jetbrains.com/lp/compose-multiplatform/")
    WebView(state = state, modifier = Modifier.fillMaxSize().padding(bottom = 45.dp))
}
