package com.alterdimensions.tiptap.compose

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HtmlWebViewControllerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun request(url: String) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun newController(): Pair<HtmlWebViewController, WebView> {
        val controller = HtmlWebViewController()
        return controller to controller.create(context)
    }

    @Test
    fun `webview is locked down`() {
        val (_, view) = newController()
        val settings = view.settings
        assertFalse("JavaScript is off by default", settings.javaScriptEnabled)
        assertFalse(settings.allowFileAccess)
        assertFalse(settings.allowContentAccess)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, settings.mixedContentMode)
        assertFalse(settings.domStorageEnabled)
        assertFalse(settings.javaScriptCanOpenWindowsAutomatically)
        assertEquals(100, settings.textZoom)
    }

    @Test
    fun `no javascript interface is ever added`() {
        val (controller, view) = newController()
        controller.update("<p>x</p>", measure = true, maxLines = 8, threshold = 10)
        assertNull(shadowOf(view).getJavascriptInterface("TipTapBridge"))
    }

    @Test
    fun `loads the document on the private origin`() {
        val (controller, view) = newController()
        val document = HtmlContentDefaults.wrapHtml("<p>x</p>", "", darkTheme = false)
        controller.update(document, measure = false, maxLines = 0, threshold = 0)
        val load = shadowOf(view).lastLoadDataWithBaseURL
        assertEquals(HtmlContentDefaults.BaseUrl, load.baseUrl)
        assertEquals(document, load.data)
        assertFalse(view.settings.javaScriptEnabled)
    }

    @Test
    fun `asking for a clamp enables measuring only`() {
        val (controller, view) = newController()
        controller.update("<p>x</p>", measure = true, maxLines = 8, threshold = 10)
        assertTrue(view.settings.javaScriptEnabled)
        assertNull(shadowOf(view).getJavascriptInterface("TipTapBridge"))
    }

    @Test
    fun `does not reload an unchanged document`() {
        val (controller, view) = newController()
        controller.update("<p>a</p>", measure = false, maxLines = 0, threshold = 0)
        shadowOf(view).lastLoadDataWithBaseURL.let { assertEquals("<p>a</p>", it.data) }
        view.loadDataWithBaseURL(null, "marker", "text/html", "UTF-8", null)
        controller.update("<p>a</p>", measure = false, maxLines = 0, threshold = 0)
        assertEquals("marker", shadowOf(view).lastLoadDataWithBaseURL.data)
    }

    @Test
    fun `allowed links go to the handler and never navigate`() {
        val (controller, view) = newController()
        val opened = mutableListOf<String>()
        controller.linkHandler = HtmlLinkHandler { opened += it }
        val client = view.webViewClient
        assertTrue(client.shouldOverrideUrlLoading(view, request("https://beet.app/e/1")))
        assertTrue(client.shouldOverrideUrlLoading(view, request("mailto:hi@beet.app")))
        assertTrue(client.shouldOverrideUrlLoading(view, request("tel:+14155550100")))
        assertEquals(listOf("https://beet.app/e/1", "mailto:hi@beet.app", "tel:+14155550100"), opened)
    }

    @Test
    fun `blocked links are swallowed`() {
        val (controller, view) = newController()
        val opened = mutableListOf<String>()
        controller.linkHandler = HtmlLinkHandler { opened += it }
        val client = view.webViewClient
        listOf("javascript:alert(1)", "data:text/html,x", "file:///etc/hosts", "intent://x#Intent;end").forEach {
            assertTrue(client.shouldOverrideUrlLoading(view, request(it)))
        }
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `network requests are blocked`() {
        val (_, view) = newController()
        val client = view.webViewClient
        val response = client.shouldInterceptRequest(view, request("https://tracker.example/pixel.png"))
        assertNotNull(response)
        assertEquals(403, response!!.statusCode)
        val unknownFont = client.shouldInterceptRequest(view, request("https://appassets.androidplatform.net/tiptap-fonts/9"))
        assertEquals(403, unknownFont!!.statusCode)
    }

    @Test
    fun `font mime types are sniffed`() {
        assertEquals("font/woff2", HtmlWebViewController.fontMimeType("wOF2".toByteArray()))
        assertEquals("font/woff", HtmlWebViewController.fontMimeType("wOFF".toByteArray()))
        assertEquals("font/otf", HtmlWebViewController.fontMimeType("OTTO".toByteArray()))
        assertEquals("font/ttf", HtmlWebViewController.fontMimeType(byteArrayOf(0, 1, 0, 0)))
    }

    @Test
    fun `default link handler opens web links and ignores blocked ones`() {
        val handler = DefaultHtmlLinkHandler(context)
        val app = shadowOf(context)
        handler.onLinkClick("javascript:alert(1)")
        assertNull(app.nextStartedActivity)
        handler.onLinkClick("https://beet.app/e/1")
        val web = app.nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, web.action)
        assertEquals("https://beet.app/e/1", web.dataString)
        handler.onLinkClick("tel:+14155550100")
        val tel = app.nextStartedActivity
        assertEquals("tel:+14155550100", tel.dataString)
    }

    @Test
    fun `default link handler survives a missing app`() {
        shadowOf(context).checkActivities(true)
        DefaultHtmlLinkHandler(context).onLinkClick("mailto:hi@beet.app")
        DefaultHtmlLinkHandler(context).onLinkClick("https://beet.app")
    }
}
