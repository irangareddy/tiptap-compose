package com.alterdimensions.tiptap.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real WebView: measuring, clamping and the overflow callback. */
@RunWith(AndroidJUnit4::class)
class HtmlContentViewTest {

    @get:Rule
    val rule = createComposeRule()

    private val longParagraph = "<p>" + List(60) { "Garba night with live dhol and friends" }.joinToString(" ") + "</p>"

    private fun lines(n: Int, prefix: String = "line") = (1..n).joinToString("<br>") { "$prefix $it" }

    private class Probe {
        var heightPx = 0
        var overflow: Boolean? = null
    }

    private fun show(
        html: String,
        maxLines: Int?,
        threshold: Int? = null,
        expanded: androidx.compose.runtime.MutableState<Boolean>? = null,
    ): Pair<Probe, Float> {
        val probe = Probe()
        var density = 1f
        rule.setContent {
            density = androidx.compose.ui.platform.LocalDensity.current.density
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                HtmlContentView(
                    htmlContent = html,
                    modifier = Modifier.fillMaxWidth().onSizeChanged { probe.heightPx = it.height },
                    maxLines = if (expanded?.value == true) null else maxLines,
                    clampThreshold = threshold,
                    onOverflowChanged = { probe.overflow = it },
                )
            }
        }
        return probe to density
    }

    private fun Probe.heightDp(density: Float) = heightPx / density

    @Test
    fun longContentClampsToWholeLinesAndReportsOverflow() {
        val (probe, density) = show(longParagraph, maxLines = 8, threshold = 10)
        rule.waitUntil(10_000) { probe.overflow == true }
        rule.waitForIdle()
        val height = probe.heightDp(density)
        assertTrue("8 lines of 24 dp, got $height", height in (8 * 24f - 12f)..(8 * 24f + 4f))
    }

    @Test
    fun contentUnderTheThresholdIsNotClamped() {
        val (probe, density) = show("<p>${lines(10)}</p>", maxLines = 8, threshold = 10)
        rule.waitUntil(10_000) { probe.overflow != null && probe.heightPx > 0 }
        Thread.sleep(500)
        rule.waitForIdle()
        assertEquals(false, probe.overflow)
        val height = probe.heightDp(density)
        assertTrue("10 lines of 24 dp, got $height", height in (10 * 24f - 4f)..(10 * 24f + 4f))
    }

    @Test
    fun headingIsNeverTheLastVisibleLine() {
        val html = "<p>${lines(7)}</p><h3>Schedule</h3><p>${lines(10, "more")}</p>"
        val (probe, density) = show(html, maxLines = 8, threshold = 10)
        rule.waitUntil(10_000) { probe.overflow == true }
        rule.waitForIdle()
        val height = probe.heightDp(density)
        // Line 8 is the heading, so the clamp ends after line 7 (7 × 24 = 168 plus half the gap).
        assertTrue("clamped before the heading, got $height", height in 160f..195f)
    }

    @Test
    fun expandingShowsEverything() {
        val expanded = mutableStateOf(false)
        val (probe, density) = show(longParagraph, maxLines = 8, threshold = 10, expanded = expanded)
        rule.waitUntil(10_000) { probe.overflow == true }
        rule.waitForIdle()
        val collapsed = probe.heightDp(density)
        expanded.value = true
        rule.waitUntil(10_000) { probe.heightDp(density) > collapsed + 48f }
        rule.waitUntil(5_000) { probe.overflow == false }
    }

    @Test
    fun unclampedContentSizesItselfWithJavaScriptOff() {
        val (probe, density) = show("<p>${lines(5)}</p><ul><li>a</li><li>b</li></ul>", maxLines = null)
        rule.waitUntil(10_000) { probe.heightDp(density) > 5 * 24f }
        rule.waitForIdle()
        val height = probe.heightDp(density)
        // 5 lines + 16 gap + 2 items (24 + 8 + 24) = 192.
        assertTrue("about 192 dp, got $height", height in 180f..210f)
    }
}
