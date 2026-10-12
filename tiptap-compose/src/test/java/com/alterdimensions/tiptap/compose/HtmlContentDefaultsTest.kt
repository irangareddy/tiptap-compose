package com.alterdimensions.tiptap.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlContentDefaultsTest {

    private val density = Density(density = 2.625f, fontScale = 1f)

    private fun doc(
        html: String = "<p>Hello</p>",
        style: HtmlContentStyle = HtmlContentStyle.Light,
        maxLines: Int? = null,
        d: Density = density,
    ) = HtmlContentDefaults.document(html, style, d, RichTextKind.Event, maxLines)

    @Test
    fun `document starts with a doctype and has one wrapper`() {
        val result = doc()
        assertTrue(result.startsWith("<!DOCTYPE html>"))
        assertTrue(result.contains("<div id=\"tt-root\" class=\"tt-rich-text\"><p>Hello</p></div>"))
    }

    @Test
    fun `document has a strict content security policy`() {
        val result = doc()
        assertTrue(result.contains("http-equiv=\"Content-Security-Policy\""))
        assertTrue(result.contains("script-src 'none'"))
        assertTrue(result.contains("default-src 'none'"))
        assertTrue(result.contains("img-src 'none'"))
    }

    @Test
    fun `document contains no script and no bridge`() {
        val result = doc("<p>Hi</p><script>alert(1)</script><p onclick=\"x()\">there</p>")
        assertFalse(result.contains("<script"))
        assertFalse(result.contains("onclick"))
        assertFalse(result.contains("TipTapBridge"))
        assertFalse(result.contains("alert"))
    }

    @Test
    fun `document content is sanitised`() {
        val result = doc("<h1>Title</h1><p><a href=\"javascript:x()\">bad</a></p>")
        assertTrue(result.contains("<h3>Title</h3>"))
        assertTrue(result.contains("<p>bad</p>"))
    }

    @Test
    fun `theme comes from the style, not the system`() {
        assertTrue(doc(style = HtmlContentStyle.Dark).contains("data-theme=\"dark\""))
        assertTrue(doc(style = HtmlContentStyle.Dark).contains("color-scheme:dark"))
        assertTrue(doc(style = HtmlContentStyle.Light).contains("data-theme=\"light\""))
    }

    @Test
    fun `defaults are neutral`() {
        val result = doc()
        assertFalse(result.contains("Roboto"))
        assertFalse(result.contains("1a73e8", ignoreCase = true))
        assertFalse(result.contains("#000000"))
        assertFalse(result.contains("color:#000;"))
        assertTrue(result.contains("font-family:sans-serif"))
    }

    @Test
    fun `colours come from the style`() {
        val style = HtmlContentStyle(
            textColor = Color(0xFF2A1620),
            secondaryTextColor = Color(0xFF6B5560),
            linkColor = Color(0xFF2A1620),
        )
        val result = doc(style = style)
        assertTrue(result.contains(".tt-rich-text{font-family:sans-serif;font-size:16px;line-height:24px;font-weight:400;color:rgba(42,22,32,1);"))
        assertTrue(result.contains(".tt-rich-text a{color:rgba(42,22,32,1);font-weight:inherit;text-decoration-line:underline;"))
        assertTrue(result.contains("color:rgba(107,85,96,1)"))
    }

    @Test
    fun `link colour defaults to the text colour and can drop the underline`() {
        val style = HtmlContentStyle(textColor = Color.Red, linkUnderline = false)
        val result = doc(style = style)
        assertTrue(result.contains(".tt-rich-text a{color:rgba(255,0,0,1);font-weight:inherit;text-decoration:none;}"))
    }

    @Test
    fun `sizes and spacing follow the style`() {
        val style = HtmlContentStyle(
            bodySize = 15.sp,
            bodyLineHeight = 22.sp,
            headingSize = 17.sp,
            headingLineHeight = 22.sp,
            headingWeight = FontWeight.Bold,
            listItemSpacing = 8.dp,
            blockSpacing = 12.dp,
        )
        val result = doc(style = style)
        assertTrue(result.contains("font-size:15px;line-height:22px;"))
        assertTrue(result.contains("font-size:17px;line-height:22px;font-weight:700;"))
        assertTrue(result.contains(".tt-rich-text li+li{margin-top:8px;}"))
        assertTrue(result.contains(".tt-rich-text p{margin:0 0 12px;}"))
    }

    @Test
    fun `default list spacing is 8 between items`() {
        assertTrue(doc().contains(".tt-rich-text li+li{margin-top:8px;}"))
    }

    @Test
    fun `font scale 1 keeps the spec sizes`() {
        val metrics = CssMetrics.from(HtmlContentStyle.Light, Density(density = 3f, fontScale = 1f))
        assertEquals(16f, metrics.bodySize, 0f)
        assertEquals(24f, metrics.bodyLineHeight, 0f)
        assertEquals(22f, metrics.headingLineHeight, 0f)
    }

    @Test
    fun `text sizes follow the font scale`() {
        val scaled = CssMetrics.from(HtmlContentStyle.Light, Density(density = 2f, fontScale = 1.5f))
        assertTrue("body grows with the font scale: ${scaled.bodySize}", scaled.bodySize > 16f)
        // Line height keeps the 24/16 ratio, even with non-linear scaling.
        assertEquals(scaled.bodySize * 1.5f, scaled.bodyLineHeight, 0.01f)
        assertEquals(scaled.headingSize * 22f / 16f, scaled.headingLineHeight, 0.01f)
        val result = doc(d = Density(density = 2f, fontScale = 1.5f))
        assertTrue(result, result.contains("font-size:${HtmlContentDefaults.px(scaled.bodySize)};line-height:${HtmlContentDefaults.px(scaled.bodyLineHeight)};"))
        // Spacing is in dp and does not scale with the font.
        assertTrue(result.contains(".tt-rich-text p{margin:0 0 16px;}"))
    }

    @Test
    fun `custom fonts are served from the private origin`() {
        val style = HtmlContentStyle(
            font = HtmlFont.Custom(
                familyName = "Instrument Sans",
                faces = listOf(
                    HtmlFontFace(HtmlFontSource.Resource(1), weight = 400),
                    HtmlFontFace(HtmlFontSource.Asset("fonts/x-italic.ttf"), weight = 400, italic = true),
                    HtmlFontFace(HtmlFontSource.Resource(3), weight = 600),
                ),
            ),
        )
        val result = doc(style = style)
        assertTrue(result.contains("@font-face{font-family:\"Instrument Sans\";src:url(\"https://appassets.androidplatform.net/tiptap-fonts/0\");font-weight:400;font-style:normal;"))
        assertTrue(result.contains("tiptap-fonts/1\");font-weight:400;font-style:italic;"))
        assertTrue(result.contains("tiptap-fonts/2\");font-weight:600;"))
        assertTrue(result.contains("font-family:\"Instrument Sans\",sans-serif;"))
        assertFalse(result.contains("file://"))
    }

    @Test
    fun `font names cannot break out of css`() {
        val style = HtmlContentStyle(font = HtmlFont.Custom("x\";}</style><script>", emptyList(), fallback = "serif;}"))
        val result = doc(style = style)
        assertFalse(result.contains("<script>"))
        assertTrue(result.contains("font-family:\"xstylescript\",serif;"))
    }

    @Test
    fun `max lines pre-clamps until measured`() {
        val result = doc(maxLines = 8)
        assertTrue(result.contains("#tt-root:not([data-measured]){overflow:hidden;max-height:192px;}"))
        assertFalse(doc().contains(":not([data-measured])"))
    }

    @Test
    fun `collapsed content fades its last line`() {
        assertTrue(doc().contains(".tt-rich-text[data-collapsed=\"true\"]{overflow:hidden;-webkit-mask-image:linear-gradient(to bottom,#000 calc(100% - 24px),transparent)"))
    }

    @Test
    fun `measure script clamps whole lines and never ends on a heading`() {
        val script = HtmlContentDefaults.measureScript(maxLines = 8, threshold = 10)
        assertTrue(script.endsWith("})(8, 10);"))
        assertTrue(script.contains("lines.length > Math.max(threshold, maxLines)"))
        assertTrue(script.contains("while (idx > 0 && lines[idx].h) idx--;"))
        assertTrue(script.contains("getElementById('tt-root')"))
    }

    @Test
    fun `cssColor converts colours`() {
        assertEquals("rgba(0,0,0,1)", HtmlContentDefaults.cssColor(Color.Black))
        assertEquals("rgba(255,255,255,0.5)", HtmlContentDefaults.cssColor(Color.White.copy(alpha = 0.5f)))
        assertEquals("rgba(0,0,0,0)", HtmlContentDefaults.cssColor(Color.Transparent))
    }

    @Test
    fun `wrapHtml keeps css inside the style element`() {
        val result = HtmlContentDefaults.wrapHtml("<p>x</p>", "p{}</style><script>", darkTheme = false)
        assertFalse(result.contains("</style><script>"))
    }
}
