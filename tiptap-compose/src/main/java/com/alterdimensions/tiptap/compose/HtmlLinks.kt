package com.alterdimensions.tiptap.compose

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Receives every link a reader taps in [HtmlContentView].
 *
 * The library only calls it for links whose scheme passes [HtmlLinkPolicy] (`http`, `https`,
 * `mailto`, `tel`); every other navigation is blocked before it reaches the handler. The WebView
 * itself never navigates.
 */
fun interface HtmlLinkHandler {
    /** Open [url]. Called on the main thread. */
    fun onLinkClick(url: String)
}

/** Which links the renderer opens. Pure Kotlin, so apps can reuse it for their own links. */
object HtmlLinkPolicy {

    /** What to do with a tapped link. */
    enum class Action {
        /** `http:` / `https:`: open in a browser tab (Custom Tabs by default). */
        Browser,

        /** `mailto:` / `tel:`: hand to the system (`ACTION_VIEW`). */
        System,

        /** Anything else (`javascript:`, `data:`, `file:`, `intent:`, relative…): ignored. */
        Blocked,
    }

    fun actionFor(url: String?): Action = when (RichTextSanitizer.linkScheme(url)) {
        "http", "https" -> Action.Browser
        "mailto", "tel" -> Action.System
        else -> Action.Blocked
    }

    fun isAllowed(url: String?): Boolean = actionFor(url) != Action.Blocked
}

/**
 * The default [HtmlLinkHandler]: `http`/`https` in a Custom Tab (falling back to `ACTION_VIEW`),
 * `mailto`/`tel` through `ACTION_VIEW`. Blocked schemes are ignored. A missing browser or mail or
 * phone app is caught (`ActivityNotFoundException`) and the tap does nothing.
 */
class DefaultHtmlLinkHandler(private val context: Context) : HtmlLinkHandler {

    override fun onLinkClick(url: String) {
        when (HtmlLinkPolicy.actionFor(url)) {
            HtmlLinkPolicy.Action.Browser -> openInBrowser(url)
            HtmlLinkPolicy.Action.System -> openWithSystem(url, browsable = false)
            HtmlLinkPolicy.Action.Blocked -> Unit
        }
    }

    private fun openInBrowser(url: String) {
        val uri = Uri.parse(url.trim())
        try {
            val customTab = CustomTabsIntent.Builder().setShowTitle(true).build()
            if (context.findActivity() == null) customTab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTab.launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            openWithSystem(url, browsable = true)
        } catch (_: SecurityException) {
            openWithSystem(url, browsable = true)
        }
    }

    private fun openWithSystem(url: String, browsable: Boolean) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))
        if (browsable) intent.addCategory(Intent.CATEGORY_BROWSABLE)
        if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No app can open it; the tap does nothing rather than crashing.
        } catch (_: SecurityException) {
            // Same: never crash on a link.
        }
    }
}

internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
