package com.alterdimensions.tiptap.compose

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * JavaScript interface exposed to the WebView as `TipTapBridge`.
 *
 * The embedded `ResizeObserver` script calls `TipTapBridge.onHeightChanged(h)`
 * whenever the document body height changes, which is then posted to the main
 * thread so Compose can update the view height.
 */
internal class HtmlContentBridge(
    private val onHeight: (Int) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onHeightChanged(heightPx: Int) {
        mainHandler.post { onHeight(heightPx) }
    }
}
