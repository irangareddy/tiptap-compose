package com.alterdimensions.tiptap.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlMeasurementTest {

    @Test
    fun `css px equal dp when the device pixel ratio equals the density`() {
        // 312 CSS px on a 2.625x phone is 312 dp (819 physical px), not 312 / 2.625 = 119 dp.
        assertEquals(312f, cssPxToDp(312f, devicePixelRatio = 2.625f, density = 2.625f), 0.001f)
        assertEquals(312f, cssPxToDp(312f, devicePixelRatio = 3f, density = 3f), 0.001f)
    }

    @Test
    fun `css px convert through physical px when the ratios differ`() {
        // 100 CSS px × 2 = 200 physical px = 100 dp at density 2; at density 2.5 that is 80 dp.
        assertEquals(80f, cssPxToDp(100f, devicePixelRatio = 2f, density = 2.5f), 0.001f)
    }

    @Test
    fun `unknown ratio means css px are dp`() {
        assertEquals(50f, cssPxToDp(50f, devicePixelRatio = 0f, density = 2.75f), 0.001f)
    }

    @Test
    fun `negative or zero heights are zero`() {
        assertEquals(0f, cssPxToDp(-4f, 2f, 2f), 0f)
        assertEquals(0f, cssPxToDp(0f, 2f, 2f), 0f)
    }

    @Test
    fun `parses an evaluateJavascript result`() {
        val m = HtmlMeasurement.parse("\"312|13|1|2.625|1\"")!!
        assertEquals(312f, m.heightCssPx, 0f)
        assertEquals(13, m.lineCount)
        assertTrue(m.clamped)
        assertEquals(2.625f, m.devicePixelRatio, 0f)
        assertTrue(m.fontsReady)
    }

    @Test
    fun `parses an unclamped result while fonts load`() {
        val m = HtmlMeasurement.parse("\"96|4|0|3|0\"")!!
        assertFalse(m.clamped)
        assertFalse(m.fontsReady)
    }

    @Test
    fun `rejects malformed results`() {
        assertNull(HtmlMeasurement.parse(null))
        assertNull(HtmlMeasurement.parse("null"))
        assertNull(HtmlMeasurement.parse("\"\""))
        assertNull(HtmlMeasurement.parse("\"a|b|c|d|e\""))
        assertNull(HtmlMeasurement.parse("\"-1|2|0|2|1\""))
    }
}
