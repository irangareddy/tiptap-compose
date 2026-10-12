package com.alterdimensions.tiptap.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Builds the document [HtmlContentView] loads: a strict Content Security Policy, the stylesheet
 * generated from [HtmlContentStyle], and the sanitised content inside one wrapper element.
 *
 * Everything here is pure Kotlin so it can be unit-tested and reused.
 */
object HtmlContentDefaults {

    /** The private origin the document is loaded on; fonts are served from it, nothing else. */
    const val Origin: String = "https://appassets.androidplatform.net"

    /** Base URL passed to `loadDataWithBaseURL`. */
    const val BaseUrl: String = "$Origin/"

    /** Path prefix fonts are served from (see [HtmlFont.Custom]). */
    const val FontPath: String = "/tiptap-fonts/"

    /** The id of the wrapper element that holds the content. */
    const val RootId: String = "tt-root"

    /**
     * Content Security Policy of every document: no script of the document can run (`script-src
     * 'none'` blocks inline scripts, `on*` handlers and `javascript:` links even if one got past
     * the sanitiser), and nothing is fetched except fonts from [Origin].
     */
    const val ContentSecurityPolicy: String =
        "default-src 'none'; script-src 'none'; style-src 'unsafe-inline'; font-src $Origin; " +
            "img-src 'none'; media-src 'none'; frame-src 'none'; connect-src 'none'; " +
            "object-src 'none'; base-uri 'none'; form-action 'none'"

    /**
     * The full document for [html]: sanitised to [kind], styled with [style] (sizes resolved with
     * [density], so the font scale applies), pre-clamped to about [maxLines] lines when set.
     */
    fun document(
        html: String,
        style: HtmlContentStyle,
        density: Density,
        kind: RichTextKind = RichTextKind.Event,
        maxLines: Int? = null,
    ): String = wrapHtml(
        content = RichTextSanitizer.sanitize(html, kind),
        css = css(style, CssMetrics.from(style, density), maxLines),
        darkTheme = style.darkTheme,
    )

    /**
     * Wraps already-sanitised [content] in a document with the CSP, viewport, theme and [css].
     * The document contains no script.
     */
    fun wrapHtml(content: String, css: String, darkTheme: Boolean): String = buildString {
        val theme = if (darkTheme) "dark" else "light"
        append("<!DOCTYPE html>")
        append("<html data-theme=\"").append(theme).append("\" data-platform=\"android\">")
        append("<head>")
        append("<meta charset=\"UTF-8\">")
        append("<meta http-equiv=\"Content-Security-Policy\" content=\"").append(ContentSecurityPolicy).append("\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">")
        append("<meta name=\"color-scheme\" content=\"").append(theme).append("\">")
        append("<style>").append(css.replace("</", "<\\/")).append("</style>")
        append("</head>")
        append("<body><div id=\"").append(RootId).append("\" class=\"tt-rich-text\">")
        append(content)
        append("</div></body></html>")
    }

    /** The stylesheet for [style], with sizes already resolved to CSS px in [metrics]. */
    fun css(style: HtmlContentStyle, metrics: CssMetrics, maxLines: Int? = null): String = buildString {
        val text = cssColor(style.textColor)
        val link = cssColor(style.linkColor)
        val secondary = cssColor(style.secondaryTextColor)
        val rule = cssColor(style.ruleColor)

        fontFaces(style.font).forEach { append(it) }
        append(":root{color-scheme:").append(if (style.darkTheme) "dark" else "light").append(";}")
        append("html,body{margin:0;padding:0;background:transparent;overflow:hidden;")
        append("-webkit-text-size-adjust:100%;text-size-adjust:100%;-webkit-tap-highlight-color:transparent;}")
        append(".tt-rich-text{font-family:").append(fontFamily(style.font)).append(";")
        append("font-size:").append(px(metrics.bodySize)).append(";")
        append("line-height:").append(px(metrics.bodyLineHeight)).append(";")
        append("font-weight:400;color:").append(text).append(";")
        append("overflow-wrap:anywhere;word-break:normal;}")
        append(".tt-rich-text>:first-child{margin-top:0;}")
        append(".tt-rich-text>:last-child{margin-bottom:0;}")
        append(".tt-rich-text p{margin:0 0 ").append(px(metrics.blockSpacing)).append(";}")
        append(".tt-rich-text h1,.tt-rich-text h2,.tt-rich-text h3,.tt-rich-text h4,.tt-rich-text h5,.tt-rich-text h6{")
        append("font-size:").append(px(metrics.headingSize)).append(";")
        append("line-height:").append(px(metrics.headingLineHeight)).append(";")
        append("font-weight:").append(style.headingWeight.weight).append(";letter-spacing:0;color:").append(text).append(";")
        append("margin:").append(px(metrics.headingSpacingAbove)).append(" 0 ")
        append(px(metrics.headingSpacingBelow)).append(";}")
        append(".tt-rich-text strong,.tt-rich-text b{font-weight:").append(style.boldWeight.weight).append(";}")
        append(".tt-rich-text em,.tt-rich-text i{font-style:italic;}")
        append(".tt-rich-text ul,.tt-rich-text ol{margin:0 0 ").append(px(metrics.blockSpacing)).append(";")
        append("padding-left:").append(px(metrics.listIndent)).append(";}")
        append(".tt-rich-text ul{list-style:disc;}.tt-rich-text ol{list-style:decimal;}")
        append(".tt-rich-text li{padding-left:2px;}")
        append(".tt-rich-text li+li{margin-top:").append(px(metrics.listItemSpacing)).append(";}")
        append(".tt-rich-text li>p{margin:0;}")
        append(".tt-rich-text li::marker{color:").append(text).append(";font-variant-numeric:tabular-nums;}")
        append(".tt-rich-text a{color:").append(link).append(";font-weight:inherit;")
        if (style.linkUnderline) {
            append("text-decoration-line:underline;text-decoration-thickness:1px;text-underline-offset:0.18em;")
        } else {
            append("text-decoration:none;")
        }
        append("}")
        append(".tt-rich-text blockquote{margin:0 0 ").append(px(metrics.blockSpacing)).append(";")
        append("padding-left:12px;border-left:3px solid ").append(rule).append(";color:").append(secondary).append(";}")
        append(".tt-rich-text[data-collapsed=\"true\"]{overflow:hidden;")
        val fade = "linear-gradient(to bottom,#000 calc(100% - ${px(metrics.bodyLineHeight)}),transparent)"
        append("-webkit-mask-image:").append(fade).append(";mask-image:").append(fade).append(";}")
        if (maxLines != null && maxLines > 0) {
            // Until the first measurement, show at most about maxLines lines so the page doesn't jump.
            append("#").append(RootId).append(":not([data-measured]){overflow:hidden;max-height:")
            append(px(metrics.bodyLineHeight * maxLines)).append(";}")
        }
    }

    /** CSS `rgba()` of [color], converted to sRGB. */
    fun cssColor(color: Color): String {
        val c = if (color == Color.Unspecified) Color.Black else color.convert(ColorSpaces.Srgb)
        val r = (c.red * 255).roundToInt()
        val g = (c.green * 255).roundToInt()
        val b = (c.blue * 255).roundToInt()
        val a = String.format(Locale.US, "%.2f", c.alpha).trimEnd('0').trimEnd('.')
        return "rgba($r,$g,$b,${a.ifEmpty { "0" }})"
    }

    internal fun px(value: Float): String {
        val rounded = (value * 100).roundToInt() / 100f
        val text = if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
        return "${text}px"
    }

    internal fun fontFamily(font: HtmlFont): String = when (font) {
        is HtmlFont.Generic -> cssFallback(font.cssFamily)
        is HtmlFont.Custom -> "\"${cssName(font.familyName)}\"," + cssFallback(font.fallback)
    }

    internal fun fontFaces(font: HtmlFont): List<String> {
        if (font !is HtmlFont.Custom) return emptyList()
        val name = cssName(font.familyName)
        return font.faces.mapIndexed { index, face ->
            "@font-face{font-family:\"$name\";src:url(\"$Origin$FontPath$index\");" +
                "font-weight:${face.weight.coerceIn(1, 1000)};font-style:${if (face.italic) "italic" else "normal"};" +
                "font-display:block;}"
        }
    }

    /** Keeps a family name safe inside a CSS string. */
    private fun cssName(name: String): String =
        name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.ifBlank { "tt-font" }

    /** Keeps a fallback list safe (`sans-serif`, `"Noto Sans", serif`). */
    private fun cssFallback(list: String): String =
        list.filter { it.isLetterOrDigit() || it in " ,-_\"'" }.ifBlank { "sans-serif" }

    /**
     * JavaScript the library itself runs with `evaluateJavascript` (never the content, whose
     * scripts the CSP blocks) to measure the wrapper and apply the line clamp. It counts the
     * rendered lines, clamps to [maxLines] whole lines when there are more than [threshold] lines
     * (a heading is never the last visible line), and returns
     * `"height|lines|clamped|devicePixelRatio|fontsReady"` (height in CSS px).
     */
    internal fun measureScript(maxLines: Int, threshold: Int): String = """
        (function(maxLines, threshold) {
          var root = document.getElementById('$RootId');
          if (!root) return '';
          root.setAttribute('data-measured', 'true');
          root.removeAttribute('data-collapsed');
          root.style.maxHeight = '';
          var top = root.getBoundingClientRect().top;
          var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
          var range = document.createRange();
          var rects = [];
          var node;
          while ((node = walker.nextNode())) {
            if (!/\S/.test(node.nodeValue)) continue;
            var parent = node.parentElement;
            var heading = !!(parent && parent.closest('h1,h2,h3,h4,h5,h6'));
            range.selectNodeContents(node);
            var list = range.getClientRects();
            for (var i = 0; i < list.length; i++) {
              var r = list[i];
              if (r.width <= 0 || r.height <= 0) continue;
              rects.push({ t: r.top - top, b: r.bottom - top, h: heading });
            }
          }
          rects.sort(function(a, b) { return a.t - b.t; });
          var lines = [];
          for (var j = 0; j < rects.length; j++) {
            var c = rects[j];
            var last = lines[lines.length - 1];
            if (last) {
              var overlap = Math.min(last.b, c.b) - Math.max(last.t, c.t);
              if (overlap > 0.5 * Math.min(last.b - last.t, c.b - c.t)) {
                last.t = Math.min(last.t, c.t);
                last.b = Math.max(last.b, c.b);
                last.h = last.h || c.h;
                continue;
              }
            }
            lines.push({ t: c.t, b: c.b, h: c.h });
          }
          var full = root.getBoundingClientRect().height;
          var height = full;
          var clamped = maxLines > 0 && lines.length > Math.max(threshold, maxLines);
          if (clamped) {
            var idx = Math.min(maxLines, lines.length) - 1;
            while (idx > 0 && lines[idx].h) idx--;
            var next = lines[idx + 1];
            var cut = next ? (lines[idx].b + next.t) / 2 : lines[idx].b;
            cut = Math.min(Math.ceil(cut), Math.ceil(full));
            root.style.maxHeight = cut + 'px';
            root.setAttribute('data-collapsed', 'true');
            height = cut;
          }
          var fontsReady = !document.fonts || document.fonts.status === 'loaded';
          return Math.ceil(height) + '|' + lines.length + '|' + (clamped ? 1 : 0) + '|' +
            (window.devicePixelRatio || 0) + '|' + (fontsReady ? 1 : 0);
        })($maxLines, $threshold);
    """.trimIndent()
}

/**
 * [HtmlContentStyle] sizes resolved to CSS px (equal to dp in a `width=device-width` WebView),
 * with the font scale applied.
 */
data class CssMetrics(
    val bodySize: Float,
    val bodyLineHeight: Float,
    val headingSize: Float,
    val headingLineHeight: Float,
    val blockSpacing: Float,
    val headingSpacingAbove: Float,
    val headingSpacingBelow: Float,
    val listItemSpacing: Float,
    val listIndent: Float,
) {
    companion object {
        /** Resolves [style] with [density] (its `fontScale` scales the text sizes). */
        fun from(style: HtmlContentStyle, density: Density): CssMetrics = with(density) {
            val body = style.bodySize.toDp().value
            fun TextUnit.css(): Float = if (type == TextUnitType.Em) value * body else toDp().value

            // Line heights keep their ratio to the font size, as Compose text does under Android
            // 14's non-linear font scaling (otherwise large text would get cramped lines).
            fun lineHeight(line: TextUnit, size: TextUnit, sizeCss: Float): Float = when {
                line.type == TextUnitType.Em -> line.value * sizeCss
                line.type == TextUnitType.Sp && size.type == TextUnitType.Sp && size.value > 0f ->
                    line.value / size.value * sizeCss
                else -> line.css()
            }
            val heading = style.headingSize.css()
            CssMetrics(
                bodySize = body,
                bodyLineHeight = lineHeight(style.bodyLineHeight, style.bodySize, body),
                headingSize = heading,
                headingLineHeight = lineHeight(style.headingLineHeight, style.headingSize, heading),
                blockSpacing = style.blockSpacing.value,
                headingSpacingAbove = style.headingSpacingAbove.value,
                headingSpacingBelow = style.headingSpacingBelow.value,
                listItemSpacing = style.listItemSpacing.value,
                listIndent = style.listIndent.value,
            )
        }
    }
}
