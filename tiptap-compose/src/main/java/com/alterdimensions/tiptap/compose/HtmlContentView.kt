package com.alterdimensions.tiptap.compose

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A read-only Composable that renders TipTap HTML content inside a [WebView].
 *
 * The WebView auto-sizes its height to fit the content using a JavaScript
 * `ResizeObserver` that reports back through a `@JavascriptInterface` bridge.
 * Scrolling is disabled — the parent Compose scrollable handles scrolling.
 * Links open externally via `Intent.ACTION_VIEW`.
 *
 * @param htmlContent The TipTap HTML body fragment to render.
 * @param modifier    Modifier applied to the composable.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlContentView(
    htmlContent: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val theme = if (isDark) "dark" else "light"
    val css = remember(context) { HtmlContentDefaults.loadCss(context) }

    var contentHeightPx by remember { mutableIntStateOf(0) }
    val contentHeightDp = with(density) { contentHeightPx.toDp() }

    val fullHtml = remember(htmlContent, theme, css) {
        HtmlContentDefaults.wrapHtml(htmlContent, css, theme)
    }

    AndroidView(
        modifier = modifier.then(
            if (contentHeightPx > 0) Modifier.heightIn(min = contentHeightDp)
            else Modifier.heightIn(min = 1.dp)
        ),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true

                addJavascriptInterface(
                    HtmlContentBridge { heightPx -> contentHeightPx = heightPx },
                    "TipTapBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        request?.url?.let { uri ->
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                        return true
                    }
                }

                loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
        }
    )
}
