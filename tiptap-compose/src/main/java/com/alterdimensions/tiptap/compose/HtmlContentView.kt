package com.alterdimensions.tiptap.compose

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders rich-text HTML (a Tiptap description) read-only, in a locked-down [android.webkit.WebView].
 *
 * **Safety.** The HTML is sanitised to the description allowlist ([RichTextSanitizer], [kind])
 * before it reaches the WebView, and the document carries a Content Security Policy that blocks
 * every script and every fetch except the app's own fonts. File and content access and mixed
 * content are off, and no JavaScript interface is ever added. Links never navigate the WebView:
 * `http`, `https`, `mailto` and `tel` links go to [onLinkClick], everything else is dropped.
 *
 * **Size.** The view is as tall as its content. Scrolling belongs to the parent.
 *
 * **Line clamp.** With [maxLines] set, the content is clamped to that many whole lines (never a
 * cut line, never a heading as the last visible line; the last line fades) when it has more than
 * [clampThreshold] lines (default: [maxLines]). [onOverflowChanged] reports whether it is clamped,
 * so the app shows "Read more" only when there is more. Set [maxLines] to `null` to expand in
 * place. Measuring lines is the only reason JavaScript is ever enabled, and then only the
 * library's own `evaluateJavascript` calls run: the CSP still blocks every script of the
 * document. Without [maxLines], JavaScript stays off.
 *
 * @param htmlContent        HTML body fragment (or plain text, which is shown as paragraphs).
 * @param modifier           Modifier for the view; usually `Modifier.fillMaxWidth()`.
 * @param style              Font, colours, sizes and theme; build it from the app's theme.
 * @param kind               Which allowlist to sanitise to ([RichTextKind.Event] or [RichTextKind.Ticket]).
 * @param maxLines           Clamp to this many lines, or `null` for the full content.
 * @param clampThreshold     Clamp only when the content has more than this many lines
 *                           (for example 10, with [maxLines] 8). `null` means [maxLines].
 * @param onOverflowChanged  Called with `true` when the content is clamped, `false` otherwise.
 * @param onLinkClick        Opens allowed links. `null` uses [DefaultHtmlLinkHandler]
 *                           (Custom Tabs for web links, the system for `mailto:`/`tel:`).
 */
@Composable
fun HtmlContentView(
    htmlContent: String,
    modifier: Modifier = Modifier,
    style: HtmlContentStyle = HtmlContentStyle.Light,
    kind: RichTextKind = RichTextKind.Event,
    maxLines: Int? = null,
    clampThreshold: Int? = null,
    onOverflowChanged: ((Boolean) -> Unit)? = null,
    onLinkClick: HtmlLinkHandler? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val linkHandler = onLinkClick ?: remember(context) { DefaultHtmlLinkHandler(context) }
    val currentOnOverflow by rememberUpdatedState(onOverflowChanged)

    val clampLines = maxLines?.takeIf { it > 0 }
    val threshold = maxOf(clampThreshold ?: 0, clampLines ?: 0)

    // Measuring stays on once a clamp was asked for, so expanding doesn't reload the document.
    val measureLatch = remember { BooleanArray(1) }
    if (clampLines != null) measureLatch[0] = true
    val measuring = measureLatch[0]

    var measurementFailed by remember(htmlContent, kind) { mutableStateOf(false) }
    val initialClamp = remember(htmlContent, kind) { clampLines }
    val preclamp = if (measurementFailed) null else initialClamp

    val document = remember(htmlContent, kind, style, density, preclamp) {
        HtmlContentDefaults.document(htmlContent, style, density, kind, preclamp)
    }

    var measuredHeight by remember(htmlContent, kind) { mutableStateOf<Dp?>(null) }
    val reportedOverflow = remember { arrayOfNulls<Boolean>(1) }
    val reportOverflow: (Boolean) -> Unit = { value ->
        if (reportedOverflow[0] != value) {
            reportedOverflow[0] = value
            currentOnOverflow?.invoke(value)
        }
    }
    LaunchedEffect(clampLines == null) {
        if (clampLines == null && reportedOverflow[0] == true) reportOverflow(false)
    }

    var renderKey by remember { mutableIntStateOf(0) }

    key(renderKey) {
        val controller = remember { HtmlWebViewController() }
        controller.onMeasured = { measurement ->
            if (measurement == null) {
                measurementFailed = true
                measuredHeight = null
                reportOverflow(false)
            } else {
                measuredHeight = cssPxToDp(
                    cssPx = measurement.heightCssPx,
                    devicePixelRatio = measurement.devicePixelRatio,
                    density = density.density,
                ).dp
                reportOverflow(measurement.clamped)
            }
        }
        controller.onRenderProcessGone = { renderKey++ }
        controller.linkHandler = linkHandler
        controller.fontFaces = (style.font as? HtmlFont.Custom)?.faces.orEmpty()

        val height = measuredHeight
        val sizeModifier = if (measuring && !measurementFailed && height != null) {
            Modifier.height(height.coerceAtLeast(1.dp))
        } else {
            Modifier.heightIn(min = 1.dp)
        }

        AndroidView(
            modifier = modifier.then(sizeModifier),
            factory = { ctx -> controller.create(ctx) },
            update = {
                controller.update(
                    document = document,
                    measure = measuring && !measurementFailed,
                    maxLines = if (measurementFailed) 0 else clampLines ?: 0,
                    threshold = threshold,
                )
            },
            onRelease = { controller.destroy() },
        )
    }
}
