package com.multiplatform.webview.web

/** Sealed class for constraining possible web content. */
sealed class WebContent {
    data class Url(
        val url: String,
        val additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) : WebContent()

    data class Data(
        val data: String,
        val baseUrl: String? = null,
        val encoding: String = "utf-8",
        val mimeType: String? = null,
        val historyUrl: String? = null,
        val additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) : WebContent()

    /**
     * File content.
     *
     * [readType] comes from upstream and defaults to assets to keep the fork's historic
     * `WebContent.File(fileName)` call sites source-compatible.
     */
    data class File(
        val fileName: String,
        val readType: WebViewFileReadType = WebViewFileReadType.ASSET_RESOURCES,
        val additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) : WebContent()

    data class Post(
        val url: String,
        val postData: ByteArray,
        val additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) : WebContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Post
            if (url != other.url) return false
            if (!postData.contentEquals(other.postData)) return false
            if (additionalHttpHeaders != other.additionalHttpHeaders) return false
            return true
        }

        override fun hashCode(): Int {
            var result = url.hashCode()
            result = 31 * result + postData.contentHashCode()
            result = 31 * result + additionalHttpHeaders.hashCode()
            return result
        }
    }

    @Deprecated("Use state.lastLoadedUrl instead")
    fun getCurrentUrl(): String? =
        when (this) {
            is Url -> url
            is Data -> baseUrl
            is File -> throw IllegalStateException("Unsupported")
            is Post -> url
            is NavigatorOnly -> throw IllegalStateException("Unsupported")
        }

    data object NavigatorOnly : WebContent()
}

internal fun WebContent.withUrl(url: String) =
    when (this) {
        is WebContent.Url -> copy(url = url)
        else -> WebContent.Url(url)
    }
