package com.alterdimensions.tiptap.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextSanitizerTest {

    private fun event(html: String) = RichTextSanitizer.sanitize(html, RichTextKind.Event)
    private fun ticket(html: String) = RichTextSanitizer.sanitize(html, RichTextKind.Ticket)

    // -- the allowlist survives unchanged --

    @Test
    fun `keeps the event allowlist`() {
        val html = "<h3>Schedule</h3><p>Doors at <strong>7</strong>, <em>music</em> at 8.<br>Bring a friend.</p>" +
            "<ul><li><p>Snacks</p></li><li><p>Drinks</p></li></ul><ol><li><p>One</p></li></ol>"
        assertEquals(html, event(html))
    }

    @Test
    fun `keeps allowed links and adds rel`() {
        val out = event("<p><a href=\"https://beet.app/e/1\" target=\"_blank\" class=\"x\">Tickets</a></p>")
        assertEquals("<p><a href=\"https://beet.app/e/1\" rel=\"noopener noreferrer nofollow ugc\">Tickets</a></p>", out)
    }

    @Test
    fun `keeps http mailto and tel links`() {
        listOf("http://example.com", "mailto:hi@beet.app", "tel:+14155550100", "HTTPS://EXAMPLE.COM").forEach { href ->
            val out = event("<p><a href=\"$href\">x</a></p>")
            assertTrue("$href kept: $out", out.contains("href=\"$href\""))
        }
    }

    // -- scripts, handlers, styles --

    @Test
    fun `removes script elements with their content`() {
        val out = event("<p>Hi</p><script>alert(1)</script><p>there</p>")
        assertEquals("<p>Hi</p><p>there</p>", out)
        assertFalse(out.contains("alert"))
    }

    @Test
    fun `removes script hidden inside inline content`() {
        val out = event("<p>Hi <strong>you<script>steal()</script></strong></p>")
        assertEquals("<p>Hi <strong>you</strong></p>", out)
    }

    @Test
    fun `drops on handlers`() {
        val out = event("<p onclick=\"evil()\" onmouseover=\"evil()\">Tap <strong onload=\"x()\">me</strong></p>")
        assertEquals("<p>Tap <strong>me</strong></p>", out)
        assertFalse(out.contains("evil"))
    }

    @Test
    fun `drops style attributes and style elements`() {
        val out = event("<style>p{color:red}</style><p style=\"color:red;font-size:40px\" class=\"big\" data-x=\"1\" align=\"center\">Text</p>")
        assertEquals("<p>Text</p>", out)
    }

    @Test
    fun `removes javascript links but keeps their text`() {
        val out = event("<p><a href=\"javascript:alert(1)\">Click</a> me</p>")
        assertEquals("<p>Click me</p>", out)
    }

    @Test
    fun `removes obfuscated javascript links`() {
        listOf(
            " javascript:alert(1)",
            "JaVaScRiPt:alert(1)",
            "jav&#x09;ascript:alert(1)",
            "jav\nascript:alert(1)",
            "vbscript:msgbox(1)",
        ).forEach { href ->
            val out = event("<p><a href=\"$href\">x</a></p>")
            assertEquals("for $href", "<p>x</p>", out)
        }
    }

    @Test
    fun `removes data file intent and relative links`() {
        listOf(
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "file:///sdcard/secret",
            "intent://scan/#Intent;scheme=zxing;end",
            "content://contacts/1",
            "/relative/path",
            "//evil.example.com",
            "#anchor",
        ).forEach { href ->
            val out = event("<p><a href=\"$href\">x</a></p>")
            assertEquals("for $href", "<p>x</p>", out)
        }
    }

    @Test
    fun `removes embeds images and forms with their content`() {
        val out = event(
            "<p>a</p><img src=x onerror=alert(1) alt=\"alt text\"><iframe src=\"https://e.com\">frame</iframe>" +
                "<svg><script>x()</script><text>svg</text></svg><video><source src=v>vid</video>" +
                "<form action=\"https://e.com\"><input value=v><button>Go</button></form><object>obj</object><p>b</p>",
        )
        assertEquals("<p>a</p><p>b</p>", out)
    }

    @Test
    fun `removes hr and comments`() {
        assertEquals("<p>a</p><p>b</p>", event("<p>a</p><hr><!-- secret --><p>b</p>"))
    }

    // -- mapping --

    @Test
    fun `maps h1 h2 h5 h6 to the section heading for events`() {
        assertEquals("<h3>One</h3>", event("<h1>One</h1>"))
        assertEquals("<h3>Two</h3>", event("<h2 style=\"color:red\">Two</h2>"))
        assertEquals("<h3>Five</h3><h3>Six</h3>", event("<h5>Five</h5><h6>Six</h6>"))
    }

    @Test
    fun `keeps legacy h4 for events`() {
        assertEquals("<h4>Legacy</h4>", event("<h4>Legacy</h4>"))
    }

    @Test
    fun `maps headings to bold paragraphs for tickets`() {
        assertEquals("<p><strong>One</strong></p>", ticket("<h1>One</h1>"))
        assertEquals("<p><strong>Three</strong></p>", ticket("<h3>Three</h3>"))
        assertEquals("<p><strong>Four</strong></p>", ticket("<h4>Four</h4>"))
    }

    @Test
    fun `maps ordered lists to bulleted lists for tickets`() {
        assertEquals("<ul><li><p>a</p></li></ul>", ticket("<ol><li><p>a</p></li></ol>"))
    }

    @Test
    fun `maps b and i to strong and em`() {
        assertEquals("<p><strong>bold</strong> <em>it</em></p>", event("<p><b>bold</b> <i>it</i></p>"))
    }

    @Test
    fun `unwraps u s code span font and mark`() {
        val out = event("<p><u>u</u> <s>s</s> <del>d</del> <code>c</code> <span style=\"x\">sp</span> <font color=red>f</font> <mark>m</mark> <sub>1</sub><sup>2</sup></p>")
        assertEquals("<p>u s d c sp f m 12</p>", out)
    }

    @Test
    fun `keeps legacy blockquote`() {
        assertEquals("<blockquote><p>Said</p></blockquote>", event("<blockquote><p>Said</p></blockquote>"))
    }

    @Test
    fun `flattens nested lists to one level`() {
        val out = event("<ul><li><p>One</p><ul><li><p>One.a</p><ol><li><p>deep</p></li></ol></li></ul></li><li><p>Two</p></li></ul>")
        assertEquals("<ul><li><p>One</p></li><li><p>One.a</p></li><li><p>deep</p></li><li><p>Two</p></li></ul>", out)
    }

    @Test
    fun `table cells become paragraphs`() {
        val out = event("<table><tr><th>Time</th><td>7 PM</td></tr><tr><td><b>Doors</b></td></tr></table>")
        assertEquals("<p>Time</p><p>7 PM</p><p><strong>Doors</strong></p>", out)
    }

    @Test
    fun `wraps loose text and divs in paragraphs`() {
        assertEquals("<p>Hello <strong>there</strong></p><p>Next</p>", event("Hello <b>there</b><div>Next</div>"))
    }

    // -- empty content --

    @Test
    fun `removes empty paragraphs at the ends and collapses runs`() {
        assertEquals("<p>a</p><p></p><p>b</p>", event("<p></p><p> </p><p>a</p><p></p><p></p><p><br></p><p>b</p><p></p>"))
    }

    @Test
    fun `whitespace only content becomes empty`() {
        assertEquals("", event("<p> </p><p><br></p>"))
        assertEquals("", event("   "))
        assertEquals("", RichTextSanitizer.sanitize(null))
        assertEquals("", event("<script>alert(1)</script>"))
    }

    // -- text is preserved --

    @Test
    fun `keeps emoji`() {
        val html = "<p>Garba night 💃🏽🪔 with friends 👨‍👩‍👧</p>"
        assertEquals(html, event(html))
    }

    @Test
    fun `keeps Indic scripts`() {
        val html = "<h3>ગરબા નાઇટ</h3><p>नवरात्रि की शुभकामनाएं। நவராத்திரி வாழ்த்துக்கள்</p><ul><li><p>দুর্গা পূজা</p></li></ul>"
        assertEquals(html, event(html))
    }

    @Test
    fun `escapes text so it can never become markup`() {
        val out = event("<p>5 &lt; 6 &amp;&amp; &lt;script&gt;x&lt;/script&gt;</p>")
        assertEquals("<p>5 &lt; 6 &amp;&amp; &lt;script&gt;x&lt;/script&gt;</p>", out)
    }

    @Test
    fun `plain text becomes paragraphs`() {
        assertEquals("<p>Line one<br>line two</p><p>Para two</p>", event("Line one\nline two\n\nPara two"))
        assertEquals("<p>5 &lt; 6 &amp; 7 &gt; 2</p>", event("5 < 6 & 7 > 2"))
    }

    @Test
    fun `survives very deep nesting`() {
        val html = "<div>".repeat(5000) + "deep" + "</div>".repeat(5000)
        assertEquals("<p>deep</p>", event(html))
        val inline = "<p>" + "<strong>".repeat(3000) + "x" + "</strong>".repeat(3000) + "</p>"
        assertTrue(event(inline).contains("x"))
    }

    @Test
    fun `is idempotent`() {
        val html = "<h1>T</h1><p onclick=x>a <a href=\"https://a.b\">l</a></p><ul><li>i<ul><li>j</li></ul></li></ul>"
        val once = event(html)
        assertEquals(once, event(once))
    }

    // -- link scheme helpers --

    @Test
    fun `linkScheme parses schemes`() {
        assertEquals("https", RichTextSanitizer.linkScheme("HTTPS://x"))
        assertEquals("mailto", RichTextSanitizer.linkScheme(" mailto:a@b.c"))
        assertEquals(null, RichTextSanitizer.linkScheme("/path"))
        assertEquals(null, RichTextSanitizer.linkScheme("jav\tascript:x"))
        assertEquals(null, RichTextSanitizer.linkScheme(""))
        assertEquals(null, RichTextSanitizer.linkScheme(null))
    }
}
