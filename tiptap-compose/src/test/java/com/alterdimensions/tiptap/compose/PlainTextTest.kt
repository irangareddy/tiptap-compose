package com.alterdimensions.tiptap.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainTextTest {

    // -- htmlToPlainText --

    @Test
    fun `paragraphs and headings are separated by a blank line`() {
        assertEquals("Schedule\n\nDoors at 7.\n\nMusic at 8.", htmlToPlainText("<h3>Schedule</h3><p>Doors at 7.</p><p>Music at 8.</p>"))
    }

    @Test
    fun `bulleted lists become bullet lines`() {
        assertEquals(
            "Bring:\n\n• Water\n• A friend",
            htmlToPlainText("<p>Bring:</p><ul><li><p>Water</p></li><li><p>A <strong>friend</strong></p></li></ul>"),
        )
    }

    @Test
    fun `numbered lists become numbered lines`() {
        assertEquals("1. First\n2. Second", htmlToPlainText("<ol><li>First</li><li><p>Second</p></li></ol>"))
    }

    @Test
    fun `nested lists are flattened`() {
        assertEquals("• a\n• b\n• c", htmlToPlainText("<ul><li>a<ul><li>b</li></ul></li><li>c</li></ul>"))
    }

    @Test
    fun `br becomes a newline`() {
        assertEquals("Line one\nLine two", htmlToPlainText("<p>Line one<br>Line two</p>"))
    }

    @Test
    fun `entities are decoded`() {
        assertEquals("Tom & Jerry <3 \"quoted\" café", htmlToPlainText("<p>Tom &amp; Jerry &lt;3 &quot;quoted&quot;&nbsp;caf&eacute;</p>"))
    }

    @Test
    fun `links keep their text only`() {
        assertEquals("See the map.", htmlToPlainText("<p>See the <a href=\"https://maps.example\">map</a>.</p>"))
    }

    @Test
    fun `whitespace is collapsed`() {
        assertEquals("a b c", htmlToPlainText("<p>  a \n\t b   c </p>"))
    }

    @Test
    fun `script and style content never leaks`() {
        val text = htmlToPlainText("<style>p{}</style><p>Hi</p><script>alert('x')</script>")
        assertEquals("Hi", text)
    }

    @Test
    fun `single line separator for summaries`() {
        assertEquals("Title Body • a • b", htmlToPlainText("<h3>Title</h3><p>Body</p><ul><li>a</li><li>b</li></ul>", blockSeparator = " "))
    }

    @Test
    fun `plain text is returned trimmed with line breaks kept`() {
        assertEquals("Line one\nLine two\n\nPara", htmlToPlainText("  Line one\r\nLine two\n\nPara \n"))
        assertEquals("5 < 6", htmlToPlainText("5 < 6"))
    }

    @Test
    fun `empty input gives empty text`() {
        assertEquals("", htmlToPlainText(null))
        assertEquals("", htmlToPlainText(""))
        assertEquals("", htmlToPlainText("<p></p>"))
    }

    @Test
    fun `quote paragraphs stay blocks`() {
        assertEquals("Before\n\nQuoted\n\nAfter", htmlToPlainText("<p>Before</p><blockquote><p>Quoted</p></blockquote><p>After</p>"))
    }

    @Test
    fun `keeps emoji and Indic text`() {
        assertEquals("ગરબા 💃🏽\n\nनवरात्रि", htmlToPlainText("<h3>ગરબા 💃🏽</h3><p>नवरात्रि</p>"))
    }

    // -- plainTextToHtml --

    @Test
    fun `plain text paragraphs become p`() {
        assertEquals("<p>One</p><p>Two<br>still two</p>", plainTextToHtml("One\n\nTwo\nstill two"))
    }

    @Test
    fun `plain text is escaped`() {
        val html = plainTextToHtml("<script>alert(1)</script> & <b>bold</b>")
        assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt; &amp; &lt;b&gt;bold&lt;/b&gt;</p>", html)
        assertFalse(html.contains("<script"))
    }

    @Test
    fun `bullet and number lines become lists`() {
        assertEquals(
            "<p>Bring:</p><ul><li><p>Water</p></li><li><p>Snacks</p></li></ul><ol><li><p>Arrive</p></li><li><p>Dance</p></li></ol>",
            plainTextToHtml("Bring:\n• Water\n- Snacks\n1. Arrive\n2. Dance"),
        )
    }

    @Test
    fun `numbered lines become bullets for tickets`() {
        assertEquals("<ul><li><p>a</p></li><li><p>b</p></li></ul>", plainTextToHtml("1. a\n2. b", RichTextKind.Ticket))
    }

    @Test
    fun `bare urls become links`() {
        assertEquals(
            "<p>Map: <a href=\"https://maps.example/x?a=1&amp;b=2\" rel=\"noopener noreferrer nofollow ugc\">https://maps.example/x?a=1&amp;b=2</a>.</p>",
            plainTextToHtml("Map: https://maps.example/x?a=1&b=2."),
        )
    }

    @Test
    fun `blank text gives empty html`() {
        assertEquals("", plainTextToHtml("  \n\n "))
        assertEquals("", plainTextToHtml(null))
    }

    @Test
    fun `round trip keeps paragraphs and lists`() {
        val html = "<p>Intro</p><ul><li><p>a</p></li><li><p>b</p></li></ul><ol><li><p>one</p></li></ol><p>End<br>line</p>"
        assertEquals(html, plainTextToHtml(htmlToPlainText(html)))
    }

    @Test
    fun `output passes the sanitiser unchanged`() {
        val html = plainTextToHtml("Hi <there>\n\n• a\n\nhttps://beet.app")
        assertEquals(html, RichTextSanitizer.sanitize(html))
        assertTrue(html.startsWith("<p>"))
    }
}
