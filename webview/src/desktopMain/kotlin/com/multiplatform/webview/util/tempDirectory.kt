package com.multiplatform.webview.util

import kotlin.io.path.createTempDirectory

val tempDirectory: java.io.File = createTempDirectory("webview-temp").toFile()

fun addTempDirectoryRemovalHook() {
    Runtime.getRuntime().addShutdownHook(
        Thread {
            tempDirectory.deleteRecursively()
        },
    )
}
