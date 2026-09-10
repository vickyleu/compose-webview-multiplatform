package com.multiplatform.webview.util

internal actual fun getPlatform(): Platform = Platform.Wasm

internal actual fun getPlatformVersion(): String = "wasm-js"

internal actual fun getPlatformVersionDouble(): Double = 0.0
