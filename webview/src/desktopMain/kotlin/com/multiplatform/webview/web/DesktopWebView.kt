package com.multiplatform.webview.web

import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.util.KLogger
import com.multiplatform.webview.util.tempDirectory
import dev.datlag.kcef.KCEFBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.network.CefPostData
import org.cef.network.CefPostDataElement
import org.cef.network.CefRequest
import org.jetbrains.compose.resources.InternalResourceApi
import java.io.File
import java.net.JarURLConnection
import java.net.URI

actual typealias NativeWebView = KCEFBrowser

class DesktopWebView(
    override val webView: KCEFBrowser,
    override val scope: CoroutineScope,
    override val webViewJsBridge: WebViewJsBridge?,
) : IWebView {
    init {
        initWebView()
    }

    override fun canGoBack() = webView.canGoBack()

    override fun canGoForward() = webView.canGoForward()

    override fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String>,
    ) {
        if (additionalHttpHeaders.isNotEmpty()) {
            val request =
                CefRequest.create().apply {
                    this.url = url
                    setHeaderMap(additionalHttpHeaders)
                }
            webView.loadRequest(request)
        } else {
            webView.loadURL(url)
        }
    }

    override fun loadHtml(
        html: String?,
        baseUrl: String?,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
        additionalHttpHeaders: Map<String, String>,
    ) {
        if (html == null) {
            KLogger.e { "DesktopWebView loadHtml: HTML content is null" }
            return
        }
        try {
            webView.loadHtml(html, baseUrl ?: KCEFBrowser.BLANK_URI)
        } catch (t: Throwable) {
            KLogger.e(t) { "DesktopWebView loadHtml failed" }
        }
    }

    @OptIn(InternalResourceApi::class)
    override suspend fun loadHtmlFile(
        fileName: String,
        readType: WebViewFileReadType,
        additionalHttpHeaders: Map<String, String>,
    ) {
        var attemptedPath = fileName
        try {
            when (readType) {
                WebViewFileReadType.ASSET_RESOURCES -> {
                    val path = fileName.removePrefix("/")
                    attemptedPath = "assets/$path"
                    val input = this::class.java.classLoader.getResourceAsStream(attemptedPath)
                        ?: error("Resource not found: $attemptedPath")
                    val outFile = File(tempDirectory, path.substringAfterLast('/'))
                    input.use { source -> outFile.outputStream().use(source::copyTo) }

                    val baseFolder = attemptedPath.substringBeforeLast("/", "")
                    val basePath = if (baseFolder.isEmpty()) "" else "$baseFolder/"
                    val resources = this::class.java.classLoader.getResources(basePath)
                    while (resources.hasMoreElements()) {
                        val connection = resources.nextElement().openConnection()
                        if (connection is JarURLConnection) {
                            val jar = connection.jarFile
                            for (entry in jar.entries()) {
                                if (entry.name.startsWith(basePath) && !entry.isDirectory) {
                                    val target = File(tempDirectory, entry.name.substringAfterLast('/'))
                                    if (!target.exists()) {
                                        jar.getInputStream(entry).use { source ->
                                            target.outputStream().use(source::copyTo)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    delay(100)
                    loadUrl(outFile.toURI().toString(), additionalHttpHeaders)
                }

                WebViewFileReadType.COMPOSE_RESOURCE_FILES -> {
                    val parts = fileName.split("!/")
                    if (parts.size != 2) error("Invalid JAR URI format: $fileName")
                    val pathInJar = parts[1].removePrefix("/")
                    attemptedPath = pathInJar
                    val jarFileUrl = parts[0].removePrefix("jar:")
                    val jarUrl = URI("jar", "$jarFileUrl!/", null).toURL()
                    val jar = (jarUrl.openConnection() as JarURLConnection).jarFile
                    val parent = pathInJar.substringBeforeLast("/", "")
                    for (entry in jar.entries()) {
                        if (entry.name.startsWith(parent) && !entry.isDirectory) {
                            val target = File(tempDirectory, entry.name.substringAfterLast('/'))
                            target.outputStream().use { output ->
                                jar.getInputStream(entry).use { it.copyTo(output) }
                            }
                        }
                    }
                    val html = File(tempDirectory, pathInJar.substringAfterLast('/'))
                    if (!html.exists()) error("Extracted HTML file not found: ${html.absolutePath}")
                    delay(100)
                    loadUrl(html.toURI().toString(), additionalHttpHeaders)
                }
            }
        } catch (t: Throwable) {
            KLogger.e(t) { "DesktopWebView loadHtmlFile failed: $fileName ($readType)" }
            loadHtml(
                "<html><body><h2>Error Loading File</h2><p>$attemptedPath</p><p>${t.message}</p></body></html>",
            )
        }
    }

    override fun postUrl(
        url: String,
        postData: ByteArray,
        additionalHttpHeaders: Map<String, String>,
    ) {
        val request =
            CefRequest.create().apply {
                this.url = url
                if (additionalHttpHeaders.isNotEmpty()) setHeaderMap(additionalHttpHeaders)
                this.postData =
                    CefPostData.create().apply {
                        addElement(
                            CefPostDataElement.create().apply {
                                setToBytes(postData.size, postData)
                            },
                        )
                    }
            }
        webView.loadRequest(request)
    }

    override fun goBack() = webView.goBack()

    override fun goForward() = webView.goForward()

    override fun reload() = webView.reload()

    override fun stopLoading() = webView.stopLoad()

    override fun destroy() {
        runCatching { stopLoading() }
        runCatching { webView.close(true) }
    }

    override fun evaluateJavaScript(
        script: String,
        callback: ((String) -> Unit)?,
    ) {
        webView.evaluateJavaScript(script) { result ->
            if (result != null) callback?.invoke(result)
        }
    }

    override fun injectJsBridge() {
        val bridge = webViewJsBridge ?: return
        super.injectJsBridge()
        evaluateJavaScript(
            """
            window.${bridge.jsBridgeName}.postMessage = function (message) {
                window.cefQuery({request:message});
            };
            """.trimIndent(),
        )
    }

    override fun initJsBridge(webViewJsBridge: WebViewJsBridge) {
        val router = CefMessageRouter.create()
        val handler =
            object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    queryId: Long,
                    request: String?,
                    persistent: Boolean,
                    callback: CefQueryCallback?,
                ): Boolean {
                    if (request == null) return false
                    val message = Json.decodeFromString<JsMessage>(request)
                    webViewJsBridge.dispatch(message)
                    return true
                }
            }
        router.addHandler(handler, false)
        webView.client.addMessageRouter(router)
    }

    override fun saveState(): WebViewBundle? = null

    override fun scrollOffset(): Pair<Int, Int> = 0 to 0
}
