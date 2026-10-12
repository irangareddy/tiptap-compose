package com.alterdimensions.tiptap.compose

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Owns the WebView behind [HtmlContentView]: locked-down settings, link routing, font serving,
 * and (only when a line clamp is requested) measuring with `evaluateJavascript`.
 *
 * Main-thread only.
 */
internal class HtmlWebViewController(
    /** Called with every measurement, or `null` when measuring is impossible. */
    var onMeasured: (HtmlMeasurement?) -> Unit = {},
    /** Called when the renderer process died; the view must be replaced. */
    var onRenderProcessGone: () -> Unit = {},
) {
    var linkHandler: HtmlLinkHandler = HtmlLinkHandler { }
    var fontFaces: List<HtmlFontFace> = emptyList()

    var webView: WebView? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var document: String? = null
    private var maxLines = 0
    private var threshold = 0
    private var pageLoaded = false
    private var generation = 0
    private var failures = 0
    private var fontPolls = 0
    private var lastWidth = -1

    fun create(context: Context): WebView {
        val view = WebView(context)
        configure(view, context.applicationContext ?: context)
        view.addOnLayoutChangeListener { v, left, _, right, _, _, _, _, _ ->
            val width = right - left
            if (width != lastWidth) {
                lastWidth = width
                if (v.isMeasuring()) measure()
            }
        }
        webView = view
        return view
    }

    private fun View.isMeasuring(): Boolean = (this as? WebView)?.settings?.javaScriptEnabled == true

    /**
     * Loads [document] if it changed. [measure] turns on measuring (JavaScript for the library's
     * own `evaluateJavascript` calls; the document's CSP still blocks every script in it).
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun update(document: String, measure: Boolean, maxLines: Int, threshold: Int) {
        val view = webView ?: return
        var reload = document != this.document
        if (measure && !view.settings.javaScriptEnabled) {
            view.settings.javaScriptEnabled = true
            reload = true
        }
        val clampChanged = maxLines != this.maxLines || threshold != this.threshold
        this.maxLines = maxLines
        this.threshold = threshold
        if (reload) {
            this.document = document
            pageLoaded = false
            generation++
            failures = 0
            fontPolls = 0
            handler.removeCallbacksAndMessages(null)
            view.loadDataWithBaseURL(HtmlContentDefaults.BaseUrl, document, "text/html", "UTF-8", null)
        } else if (clampChanged) {
            generation++ // results for the old clamp are stale
            measure()
        }
    }

    internal fun onPageFinished() {
        pageLoaded = true
        measure()
        // Layout can still move after the load event (fonts, first frame); measuring is cheap.
        val gen = generation
        handler.postDelayed({ if (gen == generation) measure() }, 250)
        handler.postDelayed({ if (gen == generation) measure() }, 1000)
    }

    fun measure() {
        val view = webView ?: return
        if (!pageLoaded || !view.settings.javaScriptEnabled) return
        val gen = generation
        view.evaluateJavascript(HtmlContentDefaults.measureScript(maxLines, threshold)) { result ->
            if (gen != generation || webView == null) return@evaluateJavascript
            val measurement = HtmlMeasurement.parse(result)
            if (measurement == null) {
                failures++
                if (failures <= MAX_FAILURES) {
                    handler.postDelayed({ if (gen == generation) measure() }, RETRY_DELAY_MS * failures)
                } else {
                    onMeasured(null)
                }
                return@evaluateJavascript
            }
            failures = 0
            onMeasured(measurement)
            if (!measurement.fontsReady && fontPolls++ < MAX_FONT_POLLS) {
                handler.postDelayed({ if (gen == generation) measure() }, FONT_POLL_MS)
            }
        }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        generation++
        webView?.let { view ->
            view.stopLoading()
            view.webViewClient = WebViewClient()
            view.destroy()
        }
        webView = null
    }

    /** Routes a navigation the reader started: allowed schemes to [linkHandler], the rest nowhere. */
    internal fun handleNavigation(url: String?): Boolean {
        if (url != null && HtmlLinkPolicy.isAllowed(url)) linkHandler.onLinkClick(url.trim())
        return true // the WebView itself never navigates
    }

    @Suppress("DEPRECATION")
    private fun configure(view: WebView, appContext: Context) {
        view.setBackgroundColor(Color.TRANSPARENT)
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.overScrollMode = View.OVER_SCROLL_NEVER

        with(view.settings) {
            javaScriptEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            domStorageEnabled = false
            databaseEnabled = false
            setGeolocationEnabled(false)
            loadsImagesAutomatically = false
            blockNetworkImage = true
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            // Sizes are already scaled by the font scale (HtmlContentStyle → CssMetrics).
            textZoom = 100
        }
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                // Colours come from HtmlContentStyle; never let the WebView recolour them.
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, false)
            }
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(HtmlContentDefaults.Origin.removePrefix("https://"))
            .addPathHandler(HtmlContentDefaults.FontPath) { path -> fontResponse(appContext, path) }
            .build()

        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                handleNavigation(request?.url?.toString())

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                handleNavigation(url)

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url ?: return blockedResponse()
                // The document itself (loadDataWithBaseURL arrives as a main-frame data: URL).
                if (request.isForMainFrame && url.scheme == "data") return null
                // Fonts from the private origin; nothing else is ever fetched.
                return assetLoader.shouldInterceptRequest(url) ?: blockedResponse()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (view === webView) onPageFinished()
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                if (view === webView) {
                    // Stop using this view; the composable swaps in a new one and destroys this.
                    handler.removeCallbacksAndMessages(null)
                    generation++
                    pageLoaded = false
                    onRenderProcessGone()
                }
                return true // never crash the app
            }
        }
    }

    private fun fontResponse(context: Context, path: String): WebResourceResponse {
        val index = path.trim('/').toIntOrNull() ?: return blockedResponse()
        val face = fontFaces.getOrNull(index) ?: return blockedResponse()
        val stream: InputStream = try {
            when (val source = face.source) {
                is HtmlFontSource.Resource -> context.resources.openRawResource(source.resId)
                is HtmlFontSource.Asset -> context.assets.open(source.path.trimStart('/'))
            }
        } catch (_: Exception) {
            return blockedResponse()
        }
        val buffered = stream.buffered()
        return WebResourceResponse(fontMimeType(buffered), null, buffered)
    }

    companion object {
        private const val MAX_FAILURES = 3
        private const val RETRY_DELAY_MS = 150L
        private const val MAX_FONT_POLLS = 30
        private const val FONT_POLL_MS = 100L

        internal fun blockedResponse(): WebResourceResponse =
            WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

        /** Sniffs the font format from its first bytes (resources have no extension). */
        internal fun fontMimeType(stream: java.io.BufferedInputStream): String {
            stream.mark(4)
            val header = ByteArray(4)
            val read = stream.read(header)
            stream.reset()
            if (read < 4) return "font/ttf"
            return fontMimeType(header)
        }

        internal fun fontMimeType(header: ByteArray): String = when (String(header, Charsets.ISO_8859_1)) {
            "wOF2" -> "font/woff2"
            "wOFF" -> "font/woff"
            "OTTO" -> "font/otf"
            else -> "font/ttf"
        }
    }
}
