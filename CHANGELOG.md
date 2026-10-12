# Changelog

## v0.2.0 — 2026-10-11

Security, styling and line-clamp release. **Breaking changes** are marked ⚠️.

### Security
- Every description is sanitised before rendering (`RichTextSanitizer`, jsoup allowlist):
  `p`, `br`, `strong`, `em`, `h3`, `ul`/`ol`/`li`, `a[href]` with http/https/mailto/tel only,
  plus legacy `blockquote` and `h4`. Scripts, styles, embeds, `on*` handlers, `style` and every
  other attribute are dropped; `h1`/`h2`/`h5`/`h6` map to `h3`; nested lists are flattened.
  `RichTextKind.Ticket` applies the smaller ticket allowlist.
- ⚠️ The `TipTapBridge` `addJavascriptInterface` and the page script are gone. JavaScript is off
  unless a line clamp is requested; then only the library's own `evaluateJavascript` runs.
- Every document carries a CSP (`default-src 'none'; script-src 'none'`, fonts from the private
  origin only), so no script in the content can run even if one got past the sanitiser.
- File, content and file-URL access off; mixed content never allowed; all network requests are
  answered locally with a 403; images blocked; algorithmic darkening off.
- Links never navigate the WebView. Only http/https/mailto/tel reach the new `onLinkClick`
  handler; the default opens web links in a Custom Tab and mailto/tel through `ACTION_VIEW`,
  catching `ActivityNotFoundException`. Other schemes are blocked.
- A crashed renderer is replaced instead of crashing the app.

### Fixed
- The measured height was in CSS px but converted as physical px (too short by the density).
  `cssPxToDp` now converts `cssPx × devicePixelRatio / density`.

### Added
- `HtmlContentStyle`: font (`HtmlFont.Generic` or `HtmlFont.Custom` with files from `res/font`,
  `res/raw` or `assets/`, served via `WebViewAssetLoader`), text, secondary, link and rule colours,
  link underline, body and heading sizes, line heights and weights, block, heading and list
  spacing (8 dp between items), list indent, and an explicit `darkTheme`.
- Text sizes follow the system font scale through the composition `Density` (non-linear scaling
  included); WebView text zoom is fixed at 100%.
- Line clamp: `maxLines`, `clampThreshold` and `onOverflowChanged`. Clamps whole lines, never ends
  on a heading, fades the last line, expands in place.
- `htmlToPlainText(html, blockSeparator)` and `plainTextToHtml(text, kind)`.
- `HtmlLinkHandler`, `HtmlLinkPolicy`, `DefaultHtmlLinkHandler`.
- Tests: sanitiser, plain text, link policy, unit conversion, document/CSS (JVM); WebView settings
  and link routing (Robolectric); clamp, overflow, heading rule, expand and CSP on a real WebView
  (instrumented).

### Changed
- ⚠️ `HtmlContentView` takes `style`, `kind`, `maxLines`, `clampThreshold`, `onOverflowChanged`
  and `onLinkClick`. The theme comes from `style.darkTheme`, no longer `isSystemInDarkTheme()`.
- ⚠️ Defaults are neutral: system sans-serif, near-black/near-white text, links in the text colour
  and underlined. The hard-coded Roboto, `#1a73e8` link blue and `#000` text are gone.
- ⚠️ `HtmlContentDefaults.loadCss()` and the bundled `tiptap-content.css` are removed; the CSS is
  generated from the style (`HtmlContentDefaults.css`, `document`). `wrapHtml(content, css,
  darkTheme: Boolean)` replaces `wrapHtml(content, css, theme: String)` and no longer adds a
  script.
- `String.stripHtmlTags()` now sanitises first (script/style text never shows) and decodes
  entities.
- New dependencies: `org.jsoup:jsoup` 1.21.2, `androidx.webkit` 1.12.1, `androidx.browser` 1.8.0.

## v0.1.1

- maven-publish for JitPack.

## v0.1.0

- `HtmlContentView` WebView renderer, `containsHtml`, `stripHtmlTags`, `autoDetectedLink`.
