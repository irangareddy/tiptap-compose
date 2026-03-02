package com.alterdimensions.tiptap.compose

import android.content.Context

/**
 * Helpers for building the full HTML document that wraps TipTap content.
 */
object HtmlContentDefaults {

    /**
     * Loads the CSS from the bundled `tiptap-content.css` asset file.
     * Returns the CSS text, or an empty string if the asset cannot be read.
     */
    fun loadCss(context: Context): String =
        try {
            context.assets.open("tiptap-content.css").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }

    /**
     * Wraps the given HTML [content] in a full HTML document with theme and CSS.
     *
     * @param content  The TipTap HTML body fragment.
     * @param css      The stylesheet text (from [loadCss]).
     * @param theme    `"light"` or `"dark"`.
     */
    fun wrapHtml(content: String, css: String, theme: String): String = buildString {
        append("<!DOCTYPE html>")
        append("<html data-theme=\"").append(theme).append("\">")
        append("<head>")
        append("<meta charset=\"UTF-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">")
        append("<style>").append(css).append("</style>")
        append("</head>")
        append("<body>")
        append(content)
        append("<script>")
        append("function reportHeight(){var h=document.body.scrollHeight;TipTapBridge.onHeightChanged(h);}")
        append("new ResizeObserver(reportHeight).observe(document.body);")
        append("reportHeight();")
        append("</script>")
        append("</body></html>")
    }
}
