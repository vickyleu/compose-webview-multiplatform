package com.multiplatform.webview.web

import org.w3c.dom.Element

@JsFun("(element) => { try { return element.contentWindow && element.contentWindow.history && element.contentWindow.history.length > 1; } catch(e) { return false; } }")
external fun checkCanGoBackJs(element: Element): Boolean

@JsFun("(element) => { try { return false; } catch(e) { return false; } }")
external fun checkCanGoForwardJs(element: Element): Boolean

@JsFun("(element) => { try { if (element.contentWindow && element.contentWindow.history) { element.contentWindow.history.back(); } } catch(e) { console.error('Error going back:', e); } }")
external fun navigateBackJs(element: Element)

@JsFun("(element) => { try { if (element.contentWindow && element.contentWindow.history) { element.contentWindow.history.forward(); } } catch(e) { console.error('Error going forward:', e); } }")
external fun navigateForwardJs(element: Element)

@JsFun("(element) => { try { if (element.contentWindow && element.contentWindow.location) { element.contentWindow.location.reload(); } } catch(e) { console.error('Error reloading:', e); } }")
external fun reloadJs(element: Element)

@JsFun("(element) => { try { if (element.contentWindow && element.contentWindow.stop) { element.contentWindow.stop(); } } catch(e) { console.error('Error stopping load:', e); } }")
external fun stopLoadingJs(element: Element)

@JsFun("(element, url) => { try { element.src = url; } catch(e) { console.error('Error setting URL:', e); } }")
external fun setUrlJs(element: Element, url: String)

@JsFun("(element, content) => { try { element.srcdoc = content; } catch(e) { console.error('Error setting HTML:', e); } }")
external fun setHtmlContentJs(element: Element, content: String)

@JsFun("(element, script) => { try { return element.contentWindow && element.contentWindow.eval ? String(element.contentWindow.eval(script)) : ''; } catch(err) { return 'Error: ' + err.message; } }")
external fun evaluateScriptJs(element: Element, script: String): String

@JsFun("(iframe) => { try { return iframe.contentDocument ? iframe.contentDocument.title : null; } catch(e) { return null; } }")
external fun getIframeTitleJs(iframe: Element): String?

@JsFun("(iframe) => { try { return iframe.contentWindow && iframe.contentWindow.location ? iframe.contentWindow.location.href : null; } catch(e) { return iframe.src || null; } }")
external fun getIframeUrlJs(iframe: Element): String?

@JsFun("(element, property, value) => { element.style[property] = value; }")
external fun setStyleJs(element: Element, property: String, value: String)

@JsFun("""(iframe) => { try { if (iframe.contentWindow) { const uniqueId = Math.random().toString(36).substring(2, 15); iframe.contentWindow.history.replaceState({id: uniqueId}, '', iframe.contentWindow.location.href); } } catch(e) { console.error('Error adding content identifier:', e); } }""")
external fun addContentIdentifierJs(iframe: Element)

@JsFun("(message) => { console.log(message); }")
external fun consoleLogJs(message: String)

@JsFun("(message) => { console.info(message); }")
external fun consoleInfoJs(message: String)

@JsFun("(message) => { console.error(message); }")
external fun consoleErrorJs(message: String)

@JsFun("""(element, width, height, x, y) => { element.style.width = width + 'px'; element.style.height = height + 'px'; element.style.left = x + 'px'; element.style.top = y + 'px'; }""")
external fun changeCoordinates(element: Element, width: Float, height: Float, x: Float, y: Float)

@JsFun("""(element) => { element.style.position = 'absolute'; element.style.margin = '0px'; }""")
external fun initializingElement(element: Element)

@JsFun("(element) => { element.focus(); }")
external fun requestFocus(element: Element)
