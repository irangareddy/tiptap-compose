# tiptap-compose

A small Jetpack Compose renderer for [Tiptap](https://tiptap.dev) rich text (the HTML a Tiptap
editor stores), for event and ticket descriptions. Read-only: it shows HTML, it doesn't edit it.

- Sanitises every description to a short allowlist before rendering.
- Renders in a locked-down `WebView` (no JavaScript interface, no file access, strict CSP).
- Styled by the app: font, colours, sizes and the theme come from an `HtmlContentStyle`.
- Clamps long text to whole lines and tells the app when to show "Read more".
- Pure-Kotlin helpers for plain-text previews and plain-text editors.

## Install

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

`app/build.gradle.kts`:

```kotlin
implementation("com.github.irangareddy:tiptap-compose:v0.2.1")
```

minSdk 28. The library brings `org.jsoup:jsoup`, `androidx.webkit` and `androidx.browser`.

## Usage

```kotlin
HtmlContentView(
    htmlContent = event.description,
    modifier = Modifier.fillMaxWidth(),
    style = HtmlContentStyle(darkTheme = isAppDark),
)
```

The view is as tall as its content; put it in your scrolling container. Plain text without tags
is shown as paragraphs (blank lines split paragraphs, single newlines become line breaks).

### Parameters

| Parameter | Default | |
|---|---|---|
| `htmlContent` | — | HTML fragment or plain text |
| `modifier` | `Modifier` | usually `Modifier.fillMaxWidth()` |
| `style` | `HtmlContentStyle.Light` | font, colours, sizes, theme ([Style](#style)) |
| `kind` | `RichTextKind.Event` | which allowlist to sanitise to ([Security](#security)) |
| `maxLines` | `null` | clamp to this many lines ([Line clamp](#line-clamp)) |
| `clampThreshold` | `null` (= `maxLines`) | clamp only when there are more lines than this |
| `onOverflowChanged` | `null` | `true` when the content is clamped |
| `onLinkClick` | `null` (= `DefaultHtmlLinkHandler`) | opens allowed links ([Links](#links)) |

## Style

`HtmlContentStyle` is a plain data class; build it from your theme. Nothing is read from the
system: the theme is the `darkTheme` flag you pass (so an in-app theme switch works), not
`isSystemInDarkTheme()`.

```kotlin
val style = HtmlContentStyle(
    darkTheme = isAppDark,
    font = HtmlFont.Custom(
        familyName = "Instrument Sans",
        faces = listOf(
            HtmlFontFace(HtmlFontSource.Resource(R.font.instrument_sans_regular), weight = 400),
            HtmlFontFace(HtmlFontSource.Resource(R.font.instrument_sans_semibold), weight = 600),
        ),
    ),
    textColor = colors.textPrimary,
    secondaryTextColor = colors.textSecondary,
    linkColor = colors.textPrimary,   // links are told apart by the underline
    linkUnderline = true,
    bodySize = 16.sp,
    bodyLineHeight = 24.sp,
)
```

| Property | Default | Notes |
|---|---|---|
| `darkTheme` | `false` | selects the neutral dark colours and `color-scheme` |
| `font` | `HtmlFont.SansSerif` | `HtmlFont.Generic("serif")`, or `HtmlFont.Custom(...)` with font files |
| `textColor` | `#1C1C1E` / `#F2F2F5` (dark) | paragraphs, headings, list markers |
| `secondaryTextColor` | `#5C5C63` / `#B0B0B8` | legacy quotes |
| `linkColor` | `textColor` | |
| `linkUnderline` | `true` | 1 dp underline, offset 0.18 em |
| `ruleColor` | `#E2E2E6` / `#3A3A3F` | the bar beside a legacy quote |
| `bodySize` / `bodyLineHeight` | 16 sp / 24 sp | paragraphs and lists |
| `headingSize` / `headingLineHeight` | 16 sp / 22 sp | `h3` (and legacy `h4`) |
| `headingWeight` / `boldWeight` | `SemiBold` (600) | |
| `blockSpacing` | 16 dp | after paragraphs, lists, quotes |
| `headingSpacingAbove` / `Below` | 24 dp / 8 dp | |
| `listItemSpacing` | 8 dp | between list items |
| `listIndent` | 24 dp | |

**Fonts.** `HtmlFontSource.Resource` takes a font *file* in `res/font` or `res/raw` (not a
font-family XML); `HtmlFontSource.Asset` takes a path in `assets/`. Files are served to the WebView
by a `WebViewAssetLoader` on the private origin `https://appassets.androidplatform.net`; file
access stays off. Styles you don't provide (for example italic) are synthesised. Characters the
font lacks (Indic scripts, emoji) fall back to the system fonts.

**Font scale.** Sizes are converted with the composition's `Density`, so text follows the system
font size exactly like Compose text, including Android 14's non-linear scaling (line heights
keep their ratio to the font size). The WebView's own text zoom is fixed at 100%.

## Line clamp

For an "About" section that collapses long text:

```kotlin
var expanded by rememberSaveable { mutableStateOf(false) }
var overflows by remember { mutableStateOf(false) }

HtmlContentView(
    htmlContent = description,
    modifier = Modifier.fillMaxWidth(),
    style = style,
    maxLines = if (expanded) null else 8,
    clampThreshold = 10,             // collapse only when longer than 10 lines
    onOverflowChanged = { overflows = it },
)
if (overflows && !expanded) {
    TextButton(onClick = { expanded = true }) { Text("Read more") }
}
```

- The clamp counts the rendered lines and cuts between two lines: never through a line.
- A heading is never the last visible line; the clamp ends on the line before it.
- The last visible line fades out.
- `onOverflowChanged(true)` only when the content really is clamped, so "Read more" never shows
  for text that fits. Setting `maxLines = null` expands in place (no reload) and reports `false`.
- Until the first measurement the content is pre-clamped to about `maxLines` lines, so the page
  doesn't jump.

## Plain text helpers

Pure Kotlin, safe off the main thread.

```kotlin
htmlToPlainText("<h3>Schedule</h3><p>Doors at 7&amp;8</p><ul><li>Snacks</li></ul>")
// "Schedule\n\nDoors at 7&8\n\n• Snacks"

htmlToPlainText(html, blockSeparator = " ")   // one line, for row summaries

plainTextToHtml("Bring:\n• Water\n\nSee https://beet.app")
// "<p>Bring:</p><ul><li><p>Water</p></li></ul><p>See <a href=\"https://beet.app\" …>https://beet.app</a></p>"
```

- `htmlToPlainText`: sanitises first, blocks separated by a blank line (or `blockSeparator`),
  `<br>` as a newline, bulleted items as `• item`, numbered items as `1. item`, entities decoded,
  whitespace collapsed, links as their text. Text without tags is returned as is (trimmed).
- `plainTextToHtml`: for plain-text editors saving into an HTML field. Blank lines split `<p>`
  paragraphs, single newlines become `<br>`, `• `/`- `/`* ` lines become a bulleted list and `1. `
  lines a numbered one, bare URLs become links, and `<`, `>`, `&` are escaped so typed tags stay
  text. Its output round-trips through `htmlToPlainText`.
- `RichTextSanitizer.sanitize(html, kind)` is public too, as are `String.containsHtml()`,
  `String.stripHtmlTags()` and `String.autoDetectedLink()`.

## Links

Links never navigate the WebView. A tap on an `http`, `https`, `mailto` or `tel` link calls
`onLinkClick`; every other scheme (`javascript:`, `data:`, `file:`, `intent:`, relative links) is
dropped by the sanitiser and blocked again at tap time (`HtmlLinkPolicy`).

The default, `DefaultHtmlLinkHandler`, opens web links in a Custom Tab (falling back to
`ACTION_VIEW`) and hands `mailto:` and `tel:` to the system. A missing app
(`ActivityNotFoundException`) is caught. Pass your own to route links through the app:

```kotlin
HtmlContentView(htmlContent = html, onLinkClick = { url -> openInApp(url) })
```

## Security

Descriptions are user content, so the renderer assumes they are hostile.

1. **Sanitised before rendering** (`RichTextSanitizer`, jsoup-based, with an allowlist):
   - Event: `p`, `br`, `strong`, `em`, `h3`, `ul`, `ol`, `li`, `a[href]` (http, https, mailto,
     tel). Legacy content that is rendered but never offered: `blockquote`, and `h4` (shown like
     `h3`).
   - Ticket (`RichTextKind.Ticket`): `p`, `br`, `strong`, `em`, `ul`, `li`, `a[href]`; headings
     become a bold paragraph, numbered lists become bulleted.
   - Mapped: `b` → `strong`, `i` → `em`, `h1`/`h2`/`h5`/`h6` → `h3`; nested lists are flattened;
     table cells become paragraphs.
   - Unwrapped (text kept): `u`, `s`, `del`, `code`, `pre`, `mark`, `span`, `font`, `div`, any
     unknown element, and links with any other scheme.
   - Removed with their content: `script`, `style`, `img`, `video`, `audio`, `iframe`, `embed`,
     `object`, `svg`, `form` and other embeds; `hr`; comments.
   - Every attribute is dropped except a vetted `href` (`on*`, `style`, `class`, `data-*`, …);
     links get `rel="noopener noreferrer nofollow ugc"`.
2. **Content Security Policy** on every document: `default-src 'none'; script-src 'none'` plus
   fonts from the private origin only. No script in the document can run, not even if something
   slipped past the sanitiser (an instrumented test checks this on a real WebView).
3. **No JavaScript interface**, ever. JavaScript is **off** unless you ask for a line clamp; then
   it is on only so the library can measure its own wrapper with `evaluateJavascript`. The CSP
   still blocks every script of the document.
4. **WebView settings:** file and content access off, file-URL access off, mixed content never
   allowed, DOM storage off, no multiple windows, images blocked, algorithmic darkening off. Every
   network request is answered locally with a 403; only the app's own fonts are served.
5. **Links** go through `HtmlLinkPolicy` and `onLinkClick` (see [Links](#links)).
6. A crashed WebView renderer is replaced rather than crashing the app.

## Development

```bash
./gradlew build test                        # unit tests (JVM + Robolectric) and lint
./gradlew connectedDebugAndroidTest         # WebView tests on a device or emulator
```

JitPack builds with JDK 21 (`jitpack.yml`).

