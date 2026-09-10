package com.kevinnzou.sample

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController

actual fun getPlatformName(): String = "iOS"

@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController() = ComposeUIViewController(configure = {
    // 键盘弹出时，不自动调整视图的大小
    this.onFocusBehavior = OnFocusBehavior.DoNothing
    this.opaque = true
//        this.opaque = false  // 设置透明背景
    this.parallelRendering = true
}) { WebViewApp() }
