package com.alterdimensions.tiptap.compose

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** The bullet that starts each bulleted-list line in [htmlToPlainText]. */
const val PlainTextBullet: String = "• "

/**
 * Converts rich-text HTML to readable plain text, for previews, one-line summaries, host surfaces
 * and plain-text editors. Pure Kotlin; safe to call off the main thread.
 *
 * - The HTML is sanitised first ([RichTextSanitizer], event allowlist), so scripts, styles and
 *   images never leak into the text.
 * - Paragraphs, headings and lists are blocks, separated by [blockSeparator] (a blank line by
 *   default; pass `" "` for a single-line summary).
 * - Bulleted items become `• item` lines, numbered items `1. item` lines.
 * - `<br>` becomes a newline (a space when [blockSeparator] has no newline).
 * - Entities are decoded (`&amp;` → `&`, `&nbsp;` → space) and whitespace is collapsed.
 * - Links keep their text only.
 *
 * Text without any tags is returned trimmed, with line endings normalised and its line breaks kept.
 */
fun htmlToPlainText(html: String?, blockSeparator: String = "\n\n"): String {
    if (html.isNullOrBlank()) return ""
    if (!html.containsHtml()) {
        return html.replace("\r\n", "\n").replace('\r', '\n').trim()
    }
    val singleLine = '\n' !in blockSeparator
    val lineBreak = if (singleLine) " " else "\n"
    val sanitized = RichTextSanitizer.sanitize(html, RichTextKind.Event)
    val body = Jsoup.parseBodyFragment(sanitized).body()

    val blocks = mutableListOf<String>()
    for (child in body.children()) {
        when (child.normalName()) {
            "ul", "ol" -> {
                val numbered = child.normalName() == "ol"
                val lines = child.children().filter { it.normalName() == "li" }.mapIndexed { index, li ->
                    val marker = if (numbered) "${index + 1}. " else PlainTextBullet
                    marker + inlineText(li, lineBreak)
                }.filter { it.isNotBlank() }
                if (lines.isNotEmpty()) blocks += lines.joinToString(lineBreak)
            }
            "blockquote" -> {
                val text = child.children().joinToString(blockSeparator) { inlineText(it, lineBreak) }
                if (text.isNotBlank()) blocks += text
            }
            else -> {
                val text = inlineText(child, lineBreak)
                if (text.isNotBlank()) blocks += text
            }
        }
    }
    return blocks.joinToString(blockSeparator).trim()
}

/** The text of [el] with `<br>` as [lineBreak], nested paragraphs separated by a space. */
private fun inlineText(el: Element, lineBreak: String): String {
    val sb = StringBuilder()
    fun visit(node: Node) {
        when (node) {
            is TextNode -> sb.append(node.text())
            is Element -> when (node.normalName()) {
                "br" -> sb.append(lineBreak)
                "p" -> {
                    if (sb.isNotEmpty() && !sb.endsWith(" ") && !sb.endsWith(lineBreak)) sb.append(' ')
                    node.childNodes().forEach(::visit)
                }
                else -> node.childNodes().forEach(::visit)
            }
        }
    }
    el.childNodes().forEach(::visit)
    return sb.toString()
        .replace(' ', ' ')
        .split('\n')
        .joinToString("\n") { it.replace(Regex("[ \\t\\x0B\\f]+"), " ").trim() }
        .trim()
}

/**
 * Converts plain text (what a plain-text editor produced) to allowlisted HTML.
 *
 * - Blank lines split paragraphs (`<p>`); a single newline inside a paragraph becomes `<br>`.
 * - Lines that start with `• `, `- ` or `* ` become a bulleted list; lines that start with `1. `
 *   (any number) become a numbered list (a bulleted list for [RichTextKind.Ticket]).
 * - Bare `http://` and `https://` URLs become links.
 * - `<`, `>` and `&` (and quotes) are escaped, so typed tags stay text and never reach the HTML.
 *
 * Whitespace-only text returns `""`. The result is stable under [htmlToPlainText]: lists and
 * paragraphs round-trip.
 */
fun plainTextToHtml(text: String?, kind: RichTextKind = RichTextKind.Event): String {
    if (text.isNullOrBlank()) return ""
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    val paragraphs = normalized.split(Regex("\\n[ \\t]*\\n+"))
        .map { it.trim('\n') }
        .filter { it.isNotBlank() }

    val sb = StringBuilder()
    for (paragraph in paragraphs) {
        val lines = paragraph.split('\n')
        var i = 0
        val pending = mutableListOf<String>()
        fun flushParagraph() {
            if (pending.isEmpty()) return
            sb.append("<p>")
            sb.append(pending.joinToString("<br>") { linkify(escapeHtml(it.trim())) })
            sb.append("</p>")
            pending.clear()
        }
        while (i < lines.size) {
            val line = lines[i]
            val listType = listTypeOf(line, kind)
            if (listType == null) {
                if (line.isNotBlank()) pending += line
                i++
                continue
            }
            flushParagraph()
            sb.append('<').append(listType).append('>')
            while (i < lines.size && listTypeOf(lines[i], kind) == listType) {
                val item = stripListMarker(lines[i]).trim()
                if (item.isNotEmpty()) sb.append("<li><p>").append(linkify(escapeHtml(item))).append("</p></li>")
                i++
            }
            sb.append("</").append(listType).append('>')
        }
        flushParagraph()
    }
    return sb.toString()
}

private val bulletMarker = Regex("^\\s*(?:•|-|\\*)\\s+")
private val numberMarker = Regex("^\\s*\\d{1,3}[.)]\\s+")

private fun listTypeOf(line: String, kind: RichTextKind): String? = when {
    bulletMarker.containsMatchIn(line) -> "ul"
    numberMarker.containsMatchIn(line) -> if (kind == RichTextKind.Event) "ol" else "ul"
    else -> null
}

private fun stripListMarker(line: String): String =
    line.replaceFirst(bulletMarker, "").replaceFirst(numberMarker, "")

internal fun escapeHtml(text: String): String {
    val sb = StringBuilder(text.length)
    for (c in text) {
        when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

private val urlPattern = Regex("https?://(?:(?!&(?:lt|gt|quot);)[^\\s<>\"])+", RegexOption.IGNORE_CASE)

/** Links bare URLs in already-escaped text; trailing punctuation stays outside the link. */
private fun linkify(escaped: String): String = urlPattern.replace(escaped) { match ->
    var url = match.value
    var trailing = ""
    while (url.isNotEmpty() && url.last() in ".,;:!?)]'") {
        trailing = url.last() + trailing
        url = url.dropLast(1)
    }
    if (url.length <= "https://".length) return@replace match.value
    "<a href=\"$url\" rel=\"${RichTextSanitizer.LINK_REL}\">$url</a>$trailing"
}
