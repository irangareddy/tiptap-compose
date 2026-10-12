package com.alterdimensions.tiptap.compose

/**
 * Converts a length the WebView reports in CSS px (for example `scrollHeight` or a bounding box)
 * to Compose dp.
 *
 * A CSS px is `devicePixelRatio` physical pixels, and a dp is `density` physical pixels, so
 * `dp = cssPx × devicePixelRatio / density`. In a `width=device-width` page the two ratios are
 * equal and one CSS px is one dp. (v0.1.x treated CSS px as physical px and divided by the
 * density, which made the view too short.)
 *
 * When [devicePixelRatio] is unknown (`<= 0`), it is taken to equal [density].
 */
fun cssPxToDp(cssPx: Float, devicePixelRatio: Float, density: Float): Float {
    if (cssPx <= 0f) return 0f
    if (devicePixelRatio <= 0f || density <= 0f) return cssPx
    return cssPx * devicePixelRatio / density
}

/** One measurement of the content, as returned by [HtmlContentDefaults.measureScript]. */
internal data class HtmlMeasurement(
    /** Height of the (possibly clamped) content in CSS px. */
    val heightCssPx: Float,
    /** Rendered lines in the full content. */
    val lineCount: Int,
    /** `true` when the content is clamped (it overflows the requested lines). */
    val clamped: Boolean,
    val devicePixelRatio: Float,
    /** `false` while web fonts are still loading; the library measures again shortly. */
    val fontsReady: Boolean,
) {
    companion object {
        /**
         * Parses the `evaluateJavascript` result: a JSON string literal such as
         * `"\"312|13|1|2.625|1\""`. Returns `null` for `null`, `"null"` or anything malformed.
         */
        fun parse(result: String?): HtmlMeasurement? {
            if (result == null) return null
            val raw = result.trim().removeSurrounding("\"")
            val parts = raw.split('|')
            if (parts.size != 5) return null
            val height = parts[0].toFloatOrNull() ?: return null
            val lines = parts[1].toIntOrNull() ?: return null
            val dpr = parts[3].toFloatOrNull() ?: return null
            if (height < 0f || lines < 0) return null
            return HtmlMeasurement(
                heightCssPx = height,
                lineCount = lines,
                clamped = parts[2] == "1",
                devicePixelRatio = dpr,
                fontsReady = parts[4] == "1",
            )
        }
    }
}
