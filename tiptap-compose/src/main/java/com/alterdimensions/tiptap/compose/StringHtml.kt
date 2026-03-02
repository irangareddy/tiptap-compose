package com.alterdimensions.tiptap.compose

/**
 * Returns `true` if the string contains HTML tags (e.g. `<p>`, `<strong>`).
 */
fun String.containsHtml(): Boolean {
    val pattern = Regex("<[a-zA-Z][^>]*>")
    return pattern.containsMatchIn(this)
}

/**
 * Returns the string with all HTML tags removed, suitable for plain-text previews.
 *
 * Multiple whitespace characters are collapsed into single spaces and
 * leading/trailing whitespace is trimmed.
 */
fun String.stripHtmlTags(): String {
    if (!containsHtml()) return this
    val stripped = replace(Regex("<[^>]+>"), "")
    val collapsed = stripped.replace(Regex("\\s+"), " ")
    return collapsed.trim()
}

/**
 * Auto-detects the link type and returns a properly prefixed URL string.
 *
 * - Email: `hello@example.com` → `mailto:hello@example.com`
 * - Phone: `+1234567890` or `(123) 456-7890` → `tel:+1234567890`
 * - URL: `example.com` → `https://example.com`
 * - Already prefixed values are returned as-is.
 */
fun String.autoDetectedLink(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return trimmed

    // Already has a scheme
    if ("://" in trimmed || trimmed.startsWith("mailto:") || trimmed.startsWith("tel:")) {
        return trimmed
    }

    // Email: contains @ with a dot after it
    if ("@" in trimmed) {
        val atIndex = trimmed.indexOf('@')
        if ('.' in trimmed.substring(atIndex)) {
            return "mailto:$trimmed"
        }
    }

    // Phone: starts with + or digit, contains mostly digits/phone chars
    val phoneChars = setOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '(', ')', '-', '.', ' ', '/')
    val isAllPhoneChars = trimmed.all { it in phoneChars }
    val startsLikePhone = trimmed.first().isDigit() || trimmed.startsWith("+") || trimmed.startsWith("(")
    val digitsOnly = trimmed.filter { it.isDigit() }
    if (isAllPhoneChars && startsLikePhone && digitsOnly.length >= 6) {
        return "tel:$trimmed"
    }

    // URL: add https:// if missing
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        return "https://$trimmed"
    }

    return trimmed
}
