package com.alterdimensions.tiptap.compose

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Even if markup got past the sanitiser, the document's CSP must keep its scripts from running,
 * while the library's own measuring still works.
 */
@RunWith(AndroidJUnit4::class)
class ContentSecurityPolicyTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun scriptsInTheDocumentNeverRunButMeasuringWorks() {
        val unsanitised = "<p id=\"x\">original</p>" +
            "<script>document.getElementById('x').textContent = 'script ran';</script>" +
            "<img src=\"x\" onerror=\"document.getElementById('x').textContent = 'handler ran'\">" +
            "<a id=\"l\" href=\"javascript:document.getElementById('x').textContent='link ran'\">l</a>"
        val document = HtmlContentDefaults.wrapHtml(unsanitised, "", darkTheme = false)

        val measured = CountDownLatch(1)
        var measurement: HtmlMeasurement? = null
        lateinit var controller: HtmlWebViewController
        lateinit var view: WebView
        instrumentation.runOnMainSync {
            controller = HtmlWebViewController(onMeasured = {
                measurement = it
                measured.countDown()
            })
            view = controller.create(instrumentation.targetContext)
            view.layout(0, 0, 800, 400)
            controller.update(document, measure = true, maxLines = 0, threshold = 0)
        }
        assertTrue("measured", measured.await(20, TimeUnit.SECONDS))
        assertTrue((measurement?.heightCssPx ?: 0f) > 0f)

        Thread.sleep(500)
        val text = CountDownLatch(1)
        var result: String? = null
        instrumentation.runOnMainSync {
            view.evaluateJavascript(
                "document.getElementById('l').click(); document.getElementById('x').textContent",
            ) {
                result = it
                text.countDown()
            }
        }
        assertTrue(text.await(10, TimeUnit.SECONDS))
        assertEquals("\"original\"", result)
        instrumentation.runOnMainSync { controller.destroy() }
    }
}
