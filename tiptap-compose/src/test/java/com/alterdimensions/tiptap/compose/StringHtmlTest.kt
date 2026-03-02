package com.alterdimensions.tiptap.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringHtmlTest {

    // -- containsHtml --

    @Test
    fun `containsHtml returns true for paragraph tags`() {
        assertTrue("<p>Hello</p>".containsHtml())
    }

    @Test
    fun `containsHtml returns true for self-closing tags`() {
        assertTrue("Line<br/>break".containsHtml())
    }

    @Test
    fun `containsHtml returns true for tags with attributes`() {
        assertTrue("<a href=\"https://example.com\">link</a>".containsHtml())
    }

    @Test
    fun `containsHtml returns false for plain text`() {
        assertFalse("Hello world".containsHtml())
    }

    @Test
    fun `containsHtml returns false for angle brackets without tag`() {
        assertFalse("5 < 10 and 20 > 15".containsHtml())
    }

    @Test
    fun `containsHtml returns false for empty string`() {
        assertFalse("".containsHtml())
    }

    // -- stripHtmlTags --

    @Test
    fun `stripHtmlTags removes tags and collapses whitespace`() {
        val html = "<p>Hello</p>  <p>World</p>"
        assertEquals("Hello World", html.stripHtmlTags())
    }

    @Test
    fun `stripHtmlTags returns plain text unchanged`() {
        assertEquals("Hello world", "Hello world".stripHtmlTags())
    }

    @Test
    fun `stripHtmlTags handles nested tags`() {
        val html = "<div><strong>Bold</strong> and <em>italic</em></div>"
        assertEquals("Bold and italic", html.stripHtmlTags())
    }

    @Test
    fun `stripHtmlTags trims leading and trailing whitespace`() {
        val html = "  <p> spaced </p>  "
        assertEquals("spaced", html.stripHtmlTags())
    }

    // -- autoDetectedLink --

    @Test
    fun `autoDetectedLink adds mailto for email`() {
        assertEquals("mailto:hello@example.com", "hello@example.com".autoDetectedLink())
    }

    @Test
    fun `autoDetectedLink adds tel for phone number with plus`() {
        assertEquals("tel:+1234567890", "+1234567890".autoDetectedLink())
    }

    @Test
    fun `autoDetectedLink adds tel for phone with parens`() {
        assertEquals("tel:(123) 456-7890", "(123) 456-7890".autoDetectedLink())
    }

    @Test
    fun `autoDetectedLink adds https for bare domain`() {
        assertEquals("https://example.com", "example.com".autoDetectedLink())
    }

    @Test
    fun `autoDetectedLink preserves existing https`() {
        assertEquals("https://example.com", "https://example.com".autoDetectedLink())
    }

    @Test
    fun `autoDetectedLink preserves existing mailto`() {
        assertEquals("mailto:test@foo.com", "mailto:test@foo.com".autoDetectedLink())
    }

    @Test
    fun `autoDetectedLink preserves existing tel`() {
        assertEquals("tel:+1234567890", "tel:+1234567890".autoDetectedLink())
    }

    @Test
    fun `autoDetectedLink returns empty for empty string`() {
        assertEquals("", "".autoDetectedLink())
    }

    @Test
    fun `autoDetectedLink trims whitespace`() {
        assertEquals("https://example.com", "  example.com  ".autoDetectedLink())
    }
}
