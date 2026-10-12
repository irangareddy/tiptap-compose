package com.alterdimensions.tiptap.compose

import com.alterdimensions.tiptap.compose.HtmlLinkPolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlLinkPolicyTest {

    @Test
    fun `web links open in the browser`() {
        assertEquals(Action.Browser, HtmlLinkPolicy.actionFor("https://beet.app/e/1"))
        assertEquals(Action.Browser, HtmlLinkPolicy.actionFor("http://example.com"))
        assertEquals(Action.Browser, HtmlLinkPolicy.actionFor("HTTPS://EXAMPLE.COM"))
    }

    @Test
    fun `mailto and tel go to the system`() {
        assertEquals(Action.System, HtmlLinkPolicy.actionFor("mailto:hi@beet.app"))
        assertEquals(Action.System, HtmlLinkPolicy.actionFor("tel:+14155550100"))
    }

    @Test
    fun `every other scheme is blocked`() {
        listOf(
            "javascript:alert(1)",
            "data:text/html,hi",
            "file:///etc/hosts",
            "content://x",
            "intent://x#Intent;end",
            "market://details?id=x",
            "sms:123",
            "about:blank",
            "/relative",
            "",
            null,
        ).forEach { url ->
            assertEquals("for $url", Action.Blocked, HtmlLinkPolicy.actionFor(url))
            assertFalse(HtmlLinkPolicy.isAllowed(url))
        }
    }

    @Test
    fun `allowed schemes match the sanitiser`() {
        assertEquals(setOf("http", "https", "mailto", "tel"), RichTextSanitizer.AllowedLinkSchemes)
        RichTextSanitizer.AllowedLinkSchemes.forEach { assertTrue(HtmlLinkPolicy.isAllowed("$it:x")) }
    }
}
