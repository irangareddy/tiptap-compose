package com.alterdimensions.tiptap.compose

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Which allowlist a description follows.
 *
 * - [Event]: paragraphs, line breaks, bold, italic, one section heading (`h3`), bulleted and
 *   numbered lists, and links.
 * - [Ticket]: paragraphs, line breaks, bold, italic, bulleted lists and links. Headings become a
 *   bold paragraph and numbered lists become bulleted lists.
 */
enum class RichTextKind { Event, Ticket }

/**
 * Sanitises rich-text HTML to the description allowlist before it is rendered.
 *
 * Kept (event): `p`, `br`, `strong`, `em`, `h3`, `ul`, `ol`, `li` and `a[href]` with an `http:`,
 * `https:`, `mailto:` or `tel:` link. Legacy content that is rendered but never offered is kept
 * too: `blockquote`, and `h4` (rendered exactly like `h3`).
 *
 * Mapped: `b` → `strong`, `i` → `em`, `h1`/`h2`/`h5`/`h6` → `h3` (event) or a bold paragraph
 * (ticket), `ol` → `ul` (ticket), nested lists are flattened to one level, table cells become
 * paragraphs.
 *
 * Unwrapped (the text stays, the formatting goes): `u`, `s`, `del`, `code`, `pre`, `mark`, `sub`,
 * `sup`, `span`, `font`, `div` and any other unknown element; links with any other scheme
 * (`javascript:`, `data:`, relative).
 *
 * Removed with their content: `script`, `style`, `img`, `picture`, `video`, `audio`, `iframe`,
 * `embed`, `object`, `svg`, `form` and the like; `hr`; comments.
 *
 * Every attribute is dropped except a vetted `href`; links get
 * `rel="noopener noreferrer nofollow ugc"`. Empty paragraphs at the start and end are removed and
 * runs of empty paragraphs collapse to one. Content that is only whitespace becomes `""`.
 */
object RichTextSanitizer {

    /** Link schemes that survive sanitising and that the renderer will open. */
    val AllowedLinkSchemes: Set<String> = setOf("http", "https", "mailto", "tel")

    internal const val LINK_REL = "noopener noreferrer nofollow ugc"

    private const val MAX_DEPTH = 128

    private val removedWithContent = setOf(
        "script", "style", "noscript", "template", "head", "title", "meta", "link", "base",
        "img", "picture", "source", "track", "video", "audio", "iframe", "frame", "frameset",
        "embed", "object", "param", "applet", "svg", "math", "canvas", "map", "area",
        "form", "input", "button", "select", "option", "optgroup", "textarea", "datalist",
        "output", "progress", "meter", "dialog", "portal", "slot", "hr",
    )

    private val headingTags = setOf("h1", "h2", "h3", "h4", "h5", "h6")

    /** Elements that start a new block; their inline content becomes a paragraph. */
    private val blockContainers = setOf(
        "div", "section", "article", "header", "footer", "main", "aside", "nav", "figure",
        "figcaption", "address", "details", "summary", "center", "pre", "dl", "dt", "dd",
        "fieldset", "legend", "caption", "table", "thead", "tbody", "tfoot", "tr", "body", "html",
    )

    private val tableCells = setOf("td", "th")

    /**
     * Returns [html] reduced to the allowlist of [kind]. Never throws; plain text without tags is
     * returned escaped and wrapped in paragraphs (blank lines split paragraphs, single newlines
     * become `<br>`).
     */
    fun sanitize(html: String?, kind: RichTextKind = RichTextKind.Event): String {
        if (html.isNullOrBlank()) return ""
        if (!html.containsHtml()) return plainTextToHtml(html, kind)
        val source = Jsoup.parseBodyFragment(html).body()
        val out = Document.createShell("").body()
        Converter(kind).convertBlockChildren(source, out, depth = 0)
        normalizeEmptyParagraphs(out)
        if (out.text().isBlank()) return ""
        return serialize(out)
    }

    /** `true` if [url] has one of the [AllowedLinkSchemes] (case-insensitive). */
    fun isAllowedLink(url: String?): Boolean {
        val scheme = linkScheme(url) ?: return false
        return scheme in AllowedLinkSchemes
    }

    /**
     * The lower-cased scheme of [url], or `null` when there is none (a relative link) or the
     * scheme is malformed (control characters or whitespace inside it).
     */
    fun linkScheme(url: String?): String? {
        if (url == null) return null
        val trimmed = url.trim()
        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        val scheme = trimmed.substring(0, colon)
        if (!scheme[0].isAsciiLetter()) return null
        if (!scheme.all { it.isAsciiLetter() || it.isAsciiDigit() || it == '+' || it == '-' || it == '.' }) return null
        return scheme.lowercase()
    }

    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'
    private fun Char.isAsciiDigit() = this in '0'..'9'

    internal fun serialize(body: Element): String {
        val doc = body.ownerDocument() ?: Document.createShell("")
        doc.outputSettings()
            .prettyPrint(false)
            .charset(Charsets.UTF_8)
            .escapeMode(Entities.EscapeMode.base)
        return body.html()
    }

    private class Converter(private val kind: RichTextKind) {

        /** Children of [src] go into a block container [dst] (body, blockquote, li). */
        fun convertBlockChildren(src: Element, dst: Element, depth: Int) {
            var paragraph: Element? = null
            fun openParagraph(): Element = paragraph ?: dst.appendElement("p").also { paragraph = it }

            for (node in src.childNodes().toList()) {
                when {
                    node is TextNode -> {
                        if (node.isBlank) {
                            paragraph?.appendText(" ")
                        } else {
                            openParagraph().appendText(node.text())
                        }
                    }
                    node is Element -> {
                        val tag = node.normalName()
                        when {
                            tag in removedWithContent -> Unit
                            isBlock(tag) -> {
                                paragraph = null
                                convertBlock(node, dst, depth + 1)
                            }
                            tag == "br" -> {
                                // A <br> at block level is a line break inside the current paragraph.
                                paragraph?.appendElement("br")
                            }
                            else -> convertInline(node, openParagraph(), depth + 1)
                        }
                    }
                    else -> Unit // comments, data nodes
                }
            }
            paragraph?.let(::trimParagraph)
        }

        private fun isBlock(tag: String) =
            tag == "p" || tag in headingTags || tag == "ul" || tag == "ol" || tag == "li" ||
                tag == "blockquote" || tag in blockContainers || tag in tableCells

        private fun convertBlock(el: Element, dst: Element, depth: Int) {
            if (depth > MAX_DEPTH) {
                appendTextParagraph(el, dst)
                return
            }
            when (val tag = el.normalName()) {
                "p" -> {
                    val p = dst.appendElement("p")
                    convertInlineChildren(el, p, depth)
                    trimParagraph(p)
                }
                in headingTags -> convertHeading(el, tag, dst, depth)
                "ul", "ol" -> convertList(el, dst, depth)
                "li" -> {
                    // A list item outside a list: wrap it so it still reads as a list.
                    val list = dst.appendElement("ul")
                    convertListItems(listOf(el), list, depth)
                }
                "blockquote" -> {
                    val quote = dst.appendElement("blockquote")
                    convertBlockChildren(el, quote, depth)
                    if (quote.text().isBlank()) quote.remove()
                }
                else -> convertBlockChildren(el, dst, depth) // div, table, cells, …
            }
        }

        private fun convertHeading(el: Element, tag: String, dst: Element, depth: Int) {
            if (kind == RichTextKind.Ticket) {
                val p = dst.appendElement("p")
                val strong = p.appendElement("strong")
                convertInlineChildren(el, strong, depth)
                trimParagraph(p)
                return
            }
            // h4 is legacy content rendered exactly like the section heading; the rest map to h3.
            val heading = dst.appendElement(if (tag == "h4") "h4" else "h3")
            convertInlineChildren(el, heading, depth)
            trimParagraph(heading)
        }

        private fun convertList(el: Element, dst: Element, depth: Int) {
            val listTag = if (el.normalName() == "ol" && kind == RichTextKind.Event) "ol" else "ul"
            val list = dst.appendElement(listTag)
            convertListItems(collectListItems(el), list, depth)
            if (list.childrenSize() == 0) list.remove()
        }

        /** Every `li` of [list] and of lists nested in it, in reading order (flattened). */
        private fun collectListItems(list: Element): List<Element> {
            val items = mutableListOf<Element>()
            fun visit(container: Element, level: Int) {
                for (child in container.children()) {
                    when (child.normalName()) {
                        "li" -> {
                            items.add(child)
                            if (level < MAX_DEPTH) nestedLists(child).forEach { visit(it, level + 1) }
                        }
                        "ul", "ol" -> if (level < MAX_DEPTH) visit(child, level + 1)
                        in removedWithContent -> Unit
                        else -> if (child.text().isNotBlank()) items.add(child)
                    }
                }
            }
            visit(list, 0)
            return items
        }

        /** The lists inside [item] that are not inside another list of [item]. */
        private fun nestedLists(item: Element): List<Element> {
            val found = mutableListOf<Element>()
            fun walk(el: Element) {
                for (child in el.children()) {
                    if (child.normalName() == "ul" || child.normalName() == "ol") found.add(child) else walk(child)
                }
            }
            walk(item)
            return found
        }

        private fun convertListItems(items: List<Element>, list: Element, depth: Int) {
            for (item in items) {
                val li = Element("li")
                // Nested lists were hoisted by collectListItems; drop them from this item.
                val content = item.clone().also { clone -> clone.select("ul, ol").remove() }
                convertBlockChildren(content, li, depth + 1)
                if (li.text().isNotBlank()) list.appendChild(li)
            }
        }

        private fun convertInlineChildren(src: Element, dst: Element, depth: Int) {
            for (node in src.childNodes().toList()) {
                when (node) {
                    is TextNode -> dst.appendText(node.text())
                    is Element -> convertInline(node, dst, depth + 1)
                    else -> Unit
                }
            }
        }

        private fun convertInline(el: Element, dst: Element, depth: Int) {
            if (depth > MAX_DEPTH) {
                dst.appendText(el.text())
                return
            }
            when (val tag = el.normalName()) {
                in removedWithContent -> Unit
                "br" -> dst.appendElement("br")
                "strong", "b" -> wrapInline("strong", el, dst, depth)
                "em", "i" -> wrapInline("em", el, dst, depth)
                "a" -> {
                    val href = el.attr("href").trim()
                    if (isAllowedLink(href) && !insideLink(dst)) {
                        val a = dst.appendElement("a")
                        a.attr("href", href)
                        a.attr("rel", LINK_REL)
                        convertInlineChildren(el, a, depth)
                        if (a.text().isBlank()) a.unwrap()
                    } else {
                        convertInlineChildren(el, dst, depth)
                    }
                }
                else -> {
                    // Unknown inline (span, u, code…) or a block inside inline content: keep the text.
                    val startsBlock = isBlock(tag) && dst.childNodeSize() > 0
                    if (startsBlock) dst.appendText(" ")
                    convertInlineChildren(el, dst, depth)
                }
            }
        }

        private fun wrapInline(tag: String, el: Element, dst: Element, depth: Int) {
            val wrapper = dst.appendElement(tag)
            convertInlineChildren(el, wrapper, depth)
            if (wrapper.childNodeSize() == 0) wrapper.remove()
        }

        private fun insideLink(el: Element): Boolean {
            var current: Element? = el
            while (current != null) {
                if (current.normalName() == "a") return true
                current = current.parent()
            }
            return false
        }

        private fun appendTextParagraph(el: Element, dst: Element) {
            val text = el.text()
            if (text.isNotBlank()) dst.appendElement("p").appendText(text)
        }
    }

    /** Trims leading/trailing whitespace text and trailing `<br>`s of a block. */
    private fun trimParagraph(block: Element) {
        while (true) {
            val first = block.childNodes().firstOrNull() ?: break
            if (first is TextNode && first.isBlank) first.remove()
            else if (first is TextNode) { first.text(first.text().trimStart()); break }
            else break
        }
        while (true) {
            val last = block.childNodes().lastOrNull() ?: break
            when {
                last is TextNode && last.isBlank -> last.remove()
                last is TextNode -> { last.text(last.text().trimEnd()); break }
                last is Element && last.normalName() == "br" -> last.remove()
                else -> break
            }
        }
    }

    private fun isEmptyBlock(node: Node): Boolean =
        node is Element && node.normalName() == "p" && node.text().isBlank()

    /** Removes empty paragraphs at the start and end, and collapses runs of empty ones to one. */
    private fun normalizeEmptyParagraphs(body: Element) {
        body.textNodes().filter { it.isBlank }.forEach { it.remove() }
        val nodes = body.childNodes().toList()
        var previousEmpty = false
        for (node in nodes) {
            val empty = isEmptyBlock(node)
            if (empty && previousEmpty) node.remove()
            previousEmpty = empty
        }
        while (body.childNodes().firstOrNull()?.let(::isEmptyBlock) == true) body.childNode(0).remove()
        while (body.childNodes().lastOrNull()?.let(::isEmptyBlock) == true) {
            body.childNode(body.childNodeSize() - 1).remove()
        }
    }
}
