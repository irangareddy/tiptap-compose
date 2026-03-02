package com.alterdimensions.tiptap.compose

import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlContentDefaultsTest {

    @Test
    fun `wrapHtml includes doctype`() {
        val result = HtmlContentDefaults.wrapHtml("Hello", "", "light")
        assertTrue(result.startsWith("<!DOCTYPE html>"))
    }

    @Test
    fun `wrapHtml sets data-theme attribute`() {
        val result = HtmlContentDefaults.wrapHtml("content", "", "dark")
        assertTrue(result.contains("data-theme=\"dark\""))
    }

    @Test
    fun `wrapHtml embeds css in style tag`() {
        val css = "body { color: red; }"
        val result = HtmlContentDefaults.wrapHtml("content", css, "light")
        assertTrue(result.contains("<style>body { color: red; }</style>"))
    }

    @Test
    fun `wrapHtml includes content in body`() {
        val result = HtmlContentDefaults.wrapHtml("<p>Hello World</p>", "", "light")
        assertTrue(result.contains("<p>Hello World</p>"))
    }

    @Test
    fun `wrapHtml includes ResizeObserver script`() {
        val result = HtmlContentDefaults.wrapHtml("content", "", "light")
        assertTrue(result.contains("ResizeObserver"))
    }

    @Test
    fun `wrapHtml includes TipTapBridge call`() {
        val result = HtmlContentDefaults.wrapHtml("content", "", "light")
        assertTrue(result.contains("TipTapBridge.onHeightChanged"))
    }

    @Test
    fun `wrapHtml includes viewport meta tag`() {
        val result = HtmlContentDefaults.wrapHtml("content", "", "light")
        assertTrue(result.contains("name=\"viewport\""))
        assertTrue(result.contains("user-scalable=no"))
    }

    @Test
    fun `wrapHtml produces valid html structure`() {
        val result = HtmlContentDefaults.wrapHtml("test", "", "light")
        assertTrue(result.contains("<html"))
        assertTrue(result.contains("</html>"))
        assertTrue(result.contains("<head>"))
        assertTrue(result.contains("</head>"))
        assertTrue(result.contains("<body>"))
        assertTrue(result.contains("</body>"))
    }
}
