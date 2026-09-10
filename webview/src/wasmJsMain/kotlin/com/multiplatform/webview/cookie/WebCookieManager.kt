package com.multiplatform.webview.cookie

actual fun getCookieExpirationDate(expiresDate: Long): String = jsDateToUTCString(expiresDate)

@JsFun("timestamp => new Date(timestamp).toUTCString()")
private external fun jsDateToUTCString(timestamp: Long): String

@JsFun("cookieStr => { document.cookie = cookieStr; }")
private external fun setJsCookie(cookieStr: String)

@JsFun("() => document.cookie")
private external fun getJsCookies(): String

@Suppress("FunctionName")
actual fun WebViewCookieManager(): CookieManager = WasmJsCookieManager

object WasmJsCookieManager : CookieManager {
    override suspend fun setCookie(url: String, cookie: Cookie) {
        setJsCookie(cookie.toString())
    }

    override suspend fun getCookies(url: String): List<Cookie> {
        val value = getJsCookies()
        if (value.isBlank()) return emptyList()
        return value.split(";").mapNotNull { raw ->
            val parts = raw.trim().split("=", limit = 2)
            val name = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Cookie(name = name, value = parts.getOrElse(1) { "" })
        }
    }

    override suspend fun removeAllCookies() {
        getCookies("").forEach { cookie ->
            setJsCookie("${cookie.name}=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT")
        }
    }

    override suspend fun removeCookies(url: String) {
        // document.cookie cannot enumerate arbitrary cross-origin cookies; clear cookies visible
        // to the current document, matching upstream browser limitations.
        removeAllCookies()
    }
}
