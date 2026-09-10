package com.multiplatform.webview.jsbridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Captures platform WebView console logs in a common JSON format. */
class ConsoleBridge(
    var onLog: ((String) -> Unit)? = null,
) {
    fun emitFromPlatform(
        level: String,
        content: String,
        sourceId: String?,
        lineNumber: Int,
        timestamp: String,
    ) {
        val normalizedLevel = level.lowercase()
        val type =
            when (normalizedLevel) {
                "error" -> "error"
                "exception" -> "exception"
                else -> "normal"
            }
        val cause =
            when (normalizedLevel) {
                "warn", "warning" -> "warning"
                "error" -> "error"
                "debug" -> "debug"
                else -> "user_code"
            }
        val emoji =
            when (normalizedLevel) {
                "error" -> "❌"
                "warn", "warning" -> "⚠️"
                "debug" -> "🔍"
                "info" -> "ℹ️"
                else -> "📝"
            }
        val filePath = sourceId.orEmpty()
        val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')
        val json: JsonObject =
            buildJsonObject {
                put("emoji", emoji)
                put("type", type)
                put("level", normalizedLevel)
                put("content", content)
                put("cause", cause)
                put("lineNumber", lineNumber)
                put("fileName", fileName)
                put("filePath", filePath)
                put("timestamp", timestamp)
            }
        onLog?.invoke(Json.encodeToString(JsonObject.serializer(), json))
    }
}
