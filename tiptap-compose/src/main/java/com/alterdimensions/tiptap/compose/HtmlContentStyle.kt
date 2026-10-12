package com.alterdimensions.tiptap.compose

import androidx.annotation.AnyRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How [HtmlContentView] draws rich text. The app builds one from its own theme and passes it in;
 * nothing is read from the system (not the dark mode, not a font).
 *
 * Sizes are in `sp` and spacing in `dp`. They are converted with the composition's [Density]
 * (`LocalDensity`), so the text follows the system font scale (including Android 14's non-linear
 * scaling) exactly like the rest of the screen. The WebView's own text zoom stays at 100%, so
 * nothing is scaled twice.
 *
 * The defaults are neutral (system sans-serif, near-black or near-white text, links in the text
 * colour, underlined) and follow the Beet rich-text contract's spacing: 16/24 body, 16/22 heading
 * at weight 600, 16 between blocks, 8 between list items, 24 list indent.
 *
 * @property darkTheme       `true` to draw the dark variant. Pass the app's theme flag, not
 *                           `isSystemInDarkTheme()`, so an in-app theme switch is honoured.
 * @property font            The font family. [HtmlFont.SansSerif] uses the system font.
 * @property textColor       Paragraphs, headings, list markers.
 * @property secondaryTextColor Legacy quotes.
 * @property linkColor       Links. Links are told apart by [linkUnderline], not only by colour.
 * @property linkUnderline   Underline links (1 dp, offset 0.18 em).
 * @property ruleColor       The bar at the left of a legacy quote.
 * @property bodySize        Paragraph and list text size.
 * @property bodyLineHeight  Paragraph and list line height; also the unit of the line clamp fade.
 * @property headingSize     Section heading (`h3`, legacy `h4`) size.
 * @property headingLineHeight Section heading line height.
 * @property headingWeight   Section heading weight.
 * @property boldWeight      `strong` weight.
 * @property blockSpacing    Space after paragraphs, lists and quotes.
 * @property headingSpacingAbove Space above a section heading (not when it is first).
 * @property headingSpacingBelow Space below a section heading.
 * @property listItemSpacing Space between list items.
 * @property listIndent      Indent of list items (the marker sits inside it).
 */
@Immutable
data class HtmlContentStyle(
    val darkTheme: Boolean = false,
    val font: HtmlFont = HtmlFont.SansSerif,
    val textColor: Color = if (darkTheme) NeutralDarkText else NeutralLightText,
    val secondaryTextColor: Color = if (darkTheme) NeutralDarkSecondary else NeutralLightSecondary,
    val linkColor: Color = textColor,
    val linkUnderline: Boolean = true,
    val ruleColor: Color = if (darkTheme) NeutralDarkRule else NeutralLightRule,
    val bodySize: TextUnit = 16.sp,
    val bodyLineHeight: TextUnit = 24.sp,
    val headingSize: TextUnit = 16.sp,
    val headingLineHeight: TextUnit = 22.sp,
    val headingWeight: FontWeight = FontWeight.SemiBold,
    val boldWeight: FontWeight = FontWeight.SemiBold,
    val blockSpacing: Dp = 16.dp,
    val headingSpacingAbove: Dp = 24.dp,
    val headingSpacingBelow: Dp = 8.dp,
    val listItemSpacing: Dp = 8.dp,
    val listIndent: Dp = 24.dp,
) {
    companion object {
        internal val NeutralLightText = Color(0xFF1C1C1E)
        internal val NeutralLightSecondary = Color(0xFF5C5C63)
        internal val NeutralLightRule = Color(0xFFE2E2E6)
        internal val NeutralDarkText = Color(0xFFF2F2F5)
        internal val NeutralDarkSecondary = Color(0xFFB0B0B8)
        internal val NeutralDarkRule = Color(0xFF3A3A3F)

        /** Neutral light style. */
        val Light: HtmlContentStyle = HtmlContentStyle(darkTheme = false)

        /** Neutral dark style. */
        val Dark: HtmlContentStyle = HtmlContentStyle(darkTheme = true)
    }
}

/** The font the WebView uses for rich text. */
@Immutable
sealed interface HtmlFont {

    /** The CSS generic family name or a font already known to the WebView (`sans-serif`, `serif`). */
    data class Generic(val cssFamily: String) : HtmlFont

    /**
     * A font bundled with the app, served to the WebView through a `WebViewAssetLoader` on a
     * private origin (no file access is ever enabled). Give one [HtmlFontFace] per weight/style
     * file; missing styles are synthesised by the WebView (for example italic).
     *
     * @property familyName CSS family name used inside the document; any name works.
     * @property faces      The font files.
     * @property fallback   CSS fallback families for characters the font doesn't have
     *                      (for example Indic scripts), default `sans-serif`.
     */
    data class Custom(
        val familyName: String,
        val faces: List<HtmlFontFace>,
        val fallback: String = "sans-serif",
    ) : HtmlFont

    companion object {
        val SansSerif: HtmlFont = Generic("sans-serif")
    }
}

/**
 * One font file of an [HtmlFont.Custom] family.
 *
 * @property source Where the file lives: [HtmlFontSource.Resource] (`res/font`, `res/raw`) or
 *                  [HtmlFontSource.Asset] (`assets/`).
 * @property weight CSS weight (100–900), e.g. 400 regular, 600 semibold.
 * @property italic `true` for an italic file.
 */
@Immutable
data class HtmlFontFace(
    val source: HtmlFontSource,
    val weight: Int = 400,
    val italic: Boolean = false,
)

@Immutable
sealed interface HtmlFontSource {
    /** A font file in `res/font` or `res/raw` (a `.ttf`/`.otf`/`.woff2` file, not a font-family XML). */
    data class Resource(@AnyRes val resId: Int) : HtmlFontSource

    /** A font file in the app's `assets/`, e.g. `"fonts/Inter-Regular.ttf"`. */
    data class Asset(val path: String) : HtmlFontSource
}
