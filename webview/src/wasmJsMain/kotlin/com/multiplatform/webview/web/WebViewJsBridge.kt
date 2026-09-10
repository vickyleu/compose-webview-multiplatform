package com.multiplatform.webview.web

internal fun createJsBridgeScript(
    jsBridgeName: String,
    isIife: Boolean = false,
): String {
    val bridgeObjectCode =
        """
        window.$jsBridgeName = {
            _callbacks: {},
            _callbackId: 0,
            postMessage: function(methodName, params, callbackId) {
                var messageData = JSON.stringify({
                    type: '$jsBridgeName',
                    action: methodName,
                    params: params,
                    callbackId: callbackId || 0
                });
                parent.postMessage(messageData, '*');
            },
            onCallback: function(callbackId, message) {
                var callback = this._callbacks[callbackId];
                if (callback) {
                    callback(message);
                    delete this._callbacks[callbackId];
                }
            },
            call: function(action, params, callback) {
                var callbackId = 0;
                if (callback) {
                    callbackId = ++this._callbackId;
                    this._callbacks[callbackId] = callback;
                }
                this.postMessage(action, params, callbackId);
                return callbackId;
            },
            callNative: function(methodName, params, callback) {
                return this.call(methodName, params, callback);
            }
        };
        window.addEventListener('message', function(event) {
            try {
                var data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
                if (data && data.type === '$jsBridgeName') {
                    window.$jsBridgeName.onCallback(data.callbackId, data.message);
                }
            } catch (e) {
                console.error('Error processing callback message:', e);
            }
        });
        """.trimIndent()

    return if (isIife) {
        """
        (function() {
            $bridgeObjectCode
        })();
        """.trimIndent()
    } else {
        bridgeObjectCode
    }
}

fun injectJsBridgeToHtml(
    htmlContent: String,
    jsBridgeName: String,
): String {
    if (
        htmlContent.contains("window.$jsBridgeName") &&
        htmlContent.contains("$jsBridgeName.callNative") &&
        htmlContent.contains("$jsBridgeName._callbacks")
    ) {
        return htmlContent
    }

    val bridgeScript =
        """
        <script>
        ${createJsBridgeScript(jsBridgeName)}
        </script>
        """.trimIndent()

    if (htmlContent.contains("</head>")) {
        return htmlContent.replace("</head>", "$bridgeScript</head>")
    }
    if (htmlContent.contains("<body>") || htmlContent.contains("<body ")) {
        return "<body[^>]*>".toRegex().replace(htmlContent, "$0$bridgeScript")
    }
    return "$bridgeScript$htmlContent"
}
