package com.multiplatform.webview.web

interface BaseNavigator {
    suspend fun alert(message: String, callback: (Boolean) -> Unit)
    suspend fun prompt(message: String, callback: (Boolean,String?) -> Unit)
    fun navigatorTo(url: String):Boolean
}