# Feature Research

**Domain:** Dual-screen Japanese game translator with learning features (Android, AYN Thor)
**Researched:** 2026-03-02
**Confidence:** MEDIUM-HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

Features that every screen-OCR translation app in this space offers. Missing any of these and users will reach for Gaminik, Screen Translate AI OCR, or Kaku instead.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Screen capture via MediaProjection | Foundational -- without this there is no OCR input. Every competitor captures the screen. | MEDIUM | Requires FOREGROUND_SERVICE, user permission grant each session. Must handle continuous and on-demand modes. |
| Japanese OCR text extraction | Core pipeline. Gaminik, Screen Translate AI OCR, Kamui, Kaku all do this. Users will not type text manually. | HIGH | Japanese OCR is hard -- pixel fonts, vertical text, stylized game fonts, furigana interference. Pluggable engine design is essential (ML Kit default, cloud Vision fallback). Vertical text drops accuracy from ~85% to ~40% on generic engines. |
| Translation to English | Core output. Every competitor translates. Users expect at minimum one functional translation engine. | MEDIUM | DeepL as primary recommendation -- 1.7x quality improvement with their next-gen LLM model for JA>EN. Must support at least one offline fallback. |
| Configurable capture region | Kamui, Translumo, Visual Novel OCR, Screen Translate AI OCR all let users define a scan region. Without this, OCR picks up UI elements, menus, HP bars -- garbage in, garbage out. | LOW | Draggable/resizable rectangle overlay. Persist per-game. This is the single most impactful accuracy feature. |
| Translation display on secondary screen | This IS the product. The AYN Thor second screen is the entire value proposition. Without clean secondary display, this is just another overlay translator. | MEDIUM | Android Presentation API (TYPE_PRESENTATION). Scrollable list of original + translation pairs. Must handle display lifecycle (screen sleep, app switch). |
| Manual capture mode | Fallback for when auto-capture is too noisy or user wants a specific frame. Kaku and most tools support manual trigger. | LOW | Button tap or hardware shortcut triggers single capture-OCR-translate cycle. |
| Continuous auto-capture mode | Gaminik, Screen Translate AI OCR, Kamui auto-OCR all do this. Users expect "set it and forget it" during gameplay. | MEDIUM | Loop with configurable interval. Must detect text changes to avoid re-translating identical frames. Diff detection is the hard part. |
| Settings/configuration screen | API keys, OCR engine selection, capture interval, TTS voice. Every app has this. | LOW | Standard Android preferences. Key management for DeepL, OpenAI, Claude, WaniKani. |
| Pluggable translation backend | Screen Translate AI OCR offers 20+ engines. Kamui offers Google + GPT-4. Users expect choice because no single engine is best for all game genres. | MEDIUM | Interface pattern: DeepL (best JA>EN quality), OpenAI/Claude (context-aware, handles idioms), local ML Kit (offline, lower quality). User picks based on cost/quality preference. |

### Differentiators (Competitive Advantage)

No existing competitor in this space combines dual-screen display with learning features. These features are where DS-Translator creates a category of one.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Dual-screen separation (game top, translations bottom) | **The core differentiator.** Zero competitors do this. Every existing tool overlays on the game screen, obscuring gameplay. The AYN Thor second screen eliminates this entirely. Pixel Guide does guides on bottom; no one does live translation. | MEDIUM | Presentation API handles the rendering. The hard part is lifecycle management -- what happens when the game app changes, when the device sleeps, when the user switches apps. |
| WaniKani-aware furigana | No game translator integrates WaniKani. Kamui has basic furigana filtering but not personalized to the user's kanji knowledge. This turns passive translation into active learning -- furigana appears only above kanji you haven't learned yet, reinforcing what you know while making unknown kanji readable. | MEDIUM | WaniKani API v2: fetch /assignments to get user's known kanji + SRS stage. Cache locally, refresh periodically. Morphological analysis (kuromoji/MeCab) to identify kanji in OCR output, then annotate with furigana selectively. Three modes: all furigana, no furigana, WaniKani-aware. |
| Text-to-speech for captured sentences | Game2Text and YomiNinja have TTS but most screen translators do not. Hearing Japanese pronunciation while seeing the text is a learning multiplier. Especially valuable for users who can read some kanji but want to hear natural pronunciation. | LOW | Android built-in TTS engine supports Japanese. Tap any sentence on bottom screen to hear it. Quality varies by device TTS engine -- consider allowing cloud TTS (Google Cloud, ElevenLabs) as upgrade option. |
| Dialog region auto-detect and auto-read | Kamui has auto-OCR but not auto-read. This combines: (1) persistent capture region for the dialog box, (2) change detection to spot new dialog, (3) automatic TTS read-aloud of new text. Creates a "live narrator" experience unique to this app. | HIGH | Requires robust text-change detection (not just pixel diff -- need semantic diff to handle animation/effects). False positives (reading the same text twice) will be annoying. Must get this right or it becomes an anti-feature. |
| Overlay mode for DS/3DS dual-screen games | When both physical screens are used for gameplay (DS/3DS emulators), the secondary screen can't show translations. Floating bubble overlay with tap-to-reveal translation panel solves this. No existing AYN Thor app does live translation in overlay mode. | HIGH | SYSTEM_ALERT_WINDOW permission. Floating bubble must not intercept game touch input. Translation panel must be dismissible. This is a separate UX mode from the primary dual-screen mode -- two distinct interaction patterns to build and maintain. |
| Sentence history / translation log | Game2Text and Kamui keep history. But on a dedicated second screen, a scrollable history of all detected dialog creates a "game transcript" that users can review, re-read, and learn from after a play session. | LOW | SQLite or Room database. Store: timestamp, original Japanese, translation, game context. Scrollable list on secondary display is already the primary UI -- history is just persistence of what's already shown. |
| Word segmentation with tap-to-lookup | Kamui and YomiNinja segment words and offer dictionary popup. On the bottom screen, each word in a sentence becomes tappable for definition, reading, and part-of-speech. Transforms the translation display into an interactive reading comprehension tool. | MEDIUM | Requires Japanese morphological analyzer (kuromoji for JVM/Android). Segment OCR text into tokens, render as tappable spans. Dictionary lookup via JMdict/EDICT (offline, bundled). |
| Sentence mining / Anki export | GameSentenceMiner proves demand -- the sentence mining community is large and active. One-tap export of a sentence (Japanese text + translation + screenshot + audio clip) to Anki creates a learning feedback loop from gameplay. | MEDIUM | AnkiDroid intent or AnkiConnect API. Package: screenshot crop of dialog region, TTS audio clip, original text, translation, word definitions. The screenshot+audio combination is what makes game sentence cards high quality. |
| Per-game profiles | Different games have different dialog box locations, font styles, and OCR accuracy characteristics. Saving capture region, OCR engine preference, and translation engine per game avoids reconfiguration every session. | LOW | Simple key-value store keyed by game package name or user-defined label. Store: capture rect, OCR engine, translation engine, TTS settings. |
| JLPT level indicators on vocabulary | Japanese.io and Hanabira color-code words by JLPT level. Showing N5/N4/N3/N2/N1 tags next to segmented words helps users gauge the difficulty of what they're reading and prioritize which words to study. | LOW | JMdict entries include JLPT tags. Cross-reference segmented words against JLPT word lists. Color-code: N5=green, N4=blue, N3=yellow, N2=orange, N1=red (or similar). |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Multi-language support (beyond JA>EN) | "Why not support Korean, Chinese, etc.?" | Scatters focus. Japanese OCR alone is extremely hard (vertical text, pixel fonts, furigana). Each language has unique OCR challenges. WaniKani integration is Japanese-only. Trying to generalize makes everything worse. | Ship JA>EN only. Architect the translation backend to be pluggable (it already is), so adding languages later is possible but not a v1 priority. |
| Built-in SRS / flashcard system | "Don't make me use Anki" | Building a good SRS is a separate product (WaniKani, Anki, Bunpro each spent years on theirs). A mediocre built-in SRS is worse than Anki export. Maintenance burden is high. | Export to Anki via AnkiDroid intent. Let specialized tools handle SRS. Focus on making the export frictionless (one tap, auto-populated fields). |
| Full offline translation | "Work without internet" | High-quality JA>EN translation requires LLM-class models. On-device models (ML Kit translate) produce noticeably worse output for Japanese. Users will blame the app for bad translations even though it's a model limitation. | Default to cloud translation (DeepL/LLM). Offer ML Kit offline as explicit fallback with clear quality warning. Don't pretend offline quality equals cloud quality. |
| OCR training / custom model fine-tuning | "Let me improve OCR for my specific game" | Massively complex UX. Users don't want to label training data. On-device fine-tuning is computationally expensive. The payoff is marginal for most users. | Pluggable OCR engine architecture. If ML Kit fails on a game, user switches to Google Cloud Vision or another engine. Per-game OCR engine preference in profiles handles the 80% case. |
| Real-time subtitle-style overlay on game screen | "Show translation directly on the dialog box like subtitles" | Positions translation text over the original, hiding the Japanese text that learners want to see. Requires precise positioning that breaks when dialog boxes move. Constant overlay obscures gameplay -- the exact pain point this app solves. | The entire point is the second screen. Translation goes there. Overlay mode exists as DS/3DS fallback, not as a preferred mode. |
| Automatic game detection / auto-switch profiles | "Know what game I'm playing automatically" | Unreliable. Emulators report the emulator package name, not the ROM. Game detection requires maintaining a database of game signatures. Fragile and high-maintenance. | Manual game profile selection from a dropdown. Simple, reliable, zero maintenance. User names their profiles ("Persona 5", "FF7", etc.). |
| Social features / shared translations | "Let me share translations with other players" | Requires a backend, user accounts, moderation. The project scope explicitly excludes cloud backend/user accounts. Community translation quality varies wildly. | Keep it local. User's translations are their own. If they want to share, they can export via standard Android share intents. |
| Grammar explanation / sentence parsing | "Explain the grammar of each sentence" | Accurate Japanese grammar parsing is an unsolved NLP problem for complex sentences. LLM-based grammar explanation is possible but expensive (API call per sentence) and slow. Would significantly increase per-sentence cost and latency. | Word segmentation + dictionary lookup covers the 80% case. Users wanting grammar can paste sentences into ChatGPT/Claude separately. Consider as a v2 "deep dive" feature behind explicit user action (not automatic). |

## Feature Dependencies

```
[Screen Capture (MediaProjection)]
    |
    v
[OCR Text Extraction] -----> [Configurable Capture Region]
    |                              |
    v                              v
[Translation Engine] -----> [Per-Game Profiles]
    |
    +--------+--------+
    |        |        |
    v        v        v
[Secondary   [Overlay   [Sentence
 Screen       Mode]      History]
 Display]       |           |
    |           |           v
    v           |      [Anki Export]
[Word           |
 Segmentation]  |
    |           |
    +-----+-----+
    |     |     |
    v     v     v
[WaniKani  [TTS]  [JLPT Level
 Furigana]   |     Indicators]
    |        v
    |   [Dialog Region
    |    Auto-Read]
    v
[Tap-to-Lookup
 Dictionary]
```

### Dependency Notes

- **OCR requires Screen Capture:** No OCR without screen capture. This is the foundation of everything.
- **Translation requires OCR:** Translation engine needs text input from OCR. These form the core pipeline.
- **Secondary Screen Display requires Translation:** Need translated content to display. But display architecture should be built early as it shapes the entire UX.
- **Word Segmentation requires Translation output:** Operates on the Japanese text that comes through the OCR pipeline.
- **WaniKani Furigana requires Word Segmentation:** Must identify individual kanji in segmented words to apply selective furigana.
- **Tap-to-Lookup requires Word Segmentation:** Dictionary lookup operates on segmented word tokens.
- **TTS requires Translation pipeline:** Operates on the Japanese text extracted by OCR. Independent of display mode.
- **Dialog Region Auto-Read requires TTS + Capture Region:** Combines change detection on a fixed region with automatic TTS playback.
- **Overlay Mode is independent of Secondary Screen:** Separate display path. Both consume the same translation pipeline output but render differently.
- **Anki Export requires Sentence History:** Exports data from stored translations. Also benefits from screenshot capture and TTS audio clip.
- **Per-Game Profiles enhance Capture Region:** Stores region configuration per game. Also stores OCR/translation engine preferences.
- **JLPT Level Indicators require Word Segmentation:** Cross-references segmented words against JLPT vocabulary lists.

## MVP Definition

### Launch With (v1)

Minimum viable product -- validate that the dual-screen translation concept works and is usable during gameplay.

- [ ] **Screen capture via MediaProjection** -- the input pipeline
- [ ] **Japanese OCR with ML Kit** -- on-device default, pluggable architecture for future engines
- [ ] **Configurable capture region** -- user draws a box around the dialog area, persisted
- [ ] **DeepL translation** -- best JA>EN quality, user provides API key
- [ ] **Secondary screen display** -- scrollable translation list on Presentation display
- [ ] **Manual capture mode** -- tap a button to capture + OCR + translate
- [ ] **Continuous auto-capture mode** -- looped capture with text-change detection
- [ ] **Basic settings** -- API keys, capture interval, OCR engine toggle
- [ ] **TTS playback** -- tap any sentence on bottom screen to hear Japanese pronunciation

### Add After Validation (v1.x)

Features to add once the core capture-OCR-translate-display pipeline is proven stable.

- [ ] **Word segmentation + tap-to-lookup** -- add when users confirm they use the bottom screen for reading, not just glancing at translations
- [ ] **WaniKani-aware furigana** -- add when word segmentation is working; requires WaniKani API integration
- [ ] **Per-game profiles** -- add when users report annoyance at reconfiguring capture region per game
- [ ] **Sentence history / translation log** -- add as persistence layer for the existing scrollable list
- [ ] **JLPT level indicators** -- add alongside word segmentation, low incremental cost
- [ ] **Additional translation engines** -- OpenAI, Claude, local fallback; add based on user demand for alternatives to DeepL

### Future Consideration (v2+)

Features to defer until the product proves its value and the core is solid.

- [ ] **Overlay mode for DS/3DS games** -- high complexity, separate UX paradigm, only needed for dual-screen games
- [ ] **Dialog region auto-read** -- requires very reliable change detection; premature implementation will annoy users with false triggers
- [ ] **Anki sentence mining export** -- strong demand from immersion learning community, but requires sentence history and clean data pipeline first
- [ ] **Cloud OCR engines (Google Cloud Vision)** -- add when users hit ML Kit accuracy limits on specific games
- [ ] **Grammar explanation (LLM-powered)** -- expensive, slow, and hard to get right; v2 "deep dive" feature at earliest

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Screen capture + OCR pipeline | HIGH | HIGH | P1 |
| Configurable capture region | HIGH | LOW | P1 |
| Translation (DeepL) | HIGH | LOW | P1 |
| Secondary screen display | HIGH | MEDIUM | P1 |
| Manual + continuous capture modes | HIGH | MEDIUM | P1 |
| TTS playback | HIGH | LOW | P1 |
| Basic settings | MEDIUM | LOW | P1 |
| Word segmentation + dictionary | HIGH | MEDIUM | P2 |
| WaniKani furigana | HIGH | MEDIUM | P2 |
| Per-game profiles | MEDIUM | LOW | P2 |
| Sentence history | MEDIUM | LOW | P2 |
| JLPT indicators | MEDIUM | LOW | P2 |
| Additional translation engines | MEDIUM | LOW | P2 |
| Overlay mode (DS/3DS) | MEDIUM | HIGH | P3 |
| Dialog auto-read | MEDIUM | HIGH | P3 |
| Anki export | MEDIUM | MEDIUM | P3 |
| Cloud OCR engines | LOW | MEDIUM | P3 |

**Priority key:**
- P1: Must have for launch -- the core translation pipeline and dual-screen display
- P2: Should have, add when pipeline is stable -- learning features and quality-of-life
- P3: Nice to have, future consideration -- advanced features requiring solid foundation

## Competitor Feature Analysis

| Feature | Kamui (Desktop) | Gaminik (Android) | Screen Translate AI OCR (Android) | Kaku (Android) | Game2Text (Desktop) | YomiNinja (Desktop) | DS-Translator (Ours) |
|---------|----------------|-------------------|----------------------------------|----------------|--------------------|--------------------|---------------------|
| Platform | Windows/Web | Android | Android | Android | Windows | Windows/Linux | Android (AYN Thor) |
| OCR Engine | Google Cloud Vision | Local + Cloud | Multiple AI models | On-device | Multiple (manga-ocr) | Multiple (Tesseract, etc.) | ML Kit + pluggable |
| Translation | Google, GPT-4 | 76+ languages | 20+ engines, LLMs | Dictionary only | DeepL, Google, Papago | Via extensions | DeepL, OpenAI, Claude |
| Display Method | Overlay window | Overlay on game | Floating bubble overlay | Floating window | Separate window | Overlay on screen | **Dedicated second screen** |
| Furigana | Basic filtering | None | None | None | None | None | **WaniKani-aware selective** |
| TTS | None | None | None | None | None | Built-in | **Built-in, auto-read** |
| Dictionary/Lookup | Sidebar lookup | None | None | Tap character | Popup dictionary | Popup dictionary (Yomitan) | **Tap-to-lookup on second screen** |
| Anki Integration | Yes (buggy) | None | None | None | Yes (AnkiConnect) | Yes (via Yomitan) | Planned (AnkiDroid) |
| Auto-capture | Yes (experimental) | Yes | Yes | Manual only | Yes (hooks/OCR) | Yes (templates) | Yes (with change detection) |
| Capture Region | Yes | Yes (area select) | Yes | Manual drag | Yes | Yes (templates) | Yes (persistent per-game) |
| Game Profiles | None | None | None | None | None | OCR Templates | **Per-game with all settings** |
| Learning Features | Anki export | None | Story mode | Character lookup | Anki export | Dictionary | **WaniKani, JLPT, furigana, TTS, Anki** |

### Key Competitive Insights

1. **No Android competitor has learning features.** Gaminik and Screen Translate AI OCR are translation-only. Kaku has character lookup but no translation. The learning niche on Android is completely open.

2. **No competitor uses a second screen.** Every single tool overlays on the game. This is the fundamental UX problem DS-Translator solves.

3. **Desktop tools (Kamui, Game2Text, YomiNinja) have better learning features** than Android tools, but they require a PC setup. DS-Translator brings desktop-quality learning features to a portable handheld.

4. **OCR accuracy is the universal pain point.** Every competitor struggles with stylized game fonts. The pluggable OCR approach is not just nice-to-have -- it's how every serious tool handles this (Translumo uses multiple engines simultaneously and picks the best result via ML scoring).

5. **The AYN Thor ecosystem has no translation app.** Pixel Guide shows guides, launchers show metadata, but live translation on the second screen is an unfilled gap in the dual-screen handheld space.

## Sources

- [Kamui - Japanese OCR and Translator for Games](https://kamui.gg/) -- MEDIUM confidence (web marketing + blog)
- [Gaminik: Auto Screen Translate - Google Play](https://play.google.com/store/apps/details?id=com.gaminik.i18n&hl=en_US) -- MEDIUM confidence (Play Store listing)
- [Screen Translate AI OCR - Google Play](https://play.google.com/store/apps/details?id=com.screen.translate.google) -- MEDIUM confidence (Play Store listing)
- [Kaku - Japanese OCR Dictionary](https://kaku.fuwafuwa.ca/) -- HIGH confidence (official site + open source on GitHub)
- [Game2Text](https://www.game2text.com/welcome/) -- HIGH confidence (official site + open source)
- [YomiNinja - GitHub](https://github.com/matt-m-o/YomiNinja) -- HIGH confidence (open source, verified features)
- [Translumo - GitHub](https://github.com/ramjke/Translumo) -- HIGH confidence (open source)
- [WaniKani API Reference](https://docs.api.wanikani.com/) -- HIGH confidence (official documentation)
- [Must have Apps for AYN Thor - HandheldRank](https://www.handheldrank.com/apps-for-ayn-thor-dual-screen-handhelds/) -- MEDIUM confidence (community site)
- [Dual-Screen Android Handheld Guide - Retro Game Corps](https://retrogamecorps.com/2025/10/27/dual-screen-android-handheld-guide/) -- MEDIUM confidence (respected community guide)
- [DeepL next-gen LLM translation quality](https://www.deepl.com/en/blog/next-gen-language-model) -- HIGH confidence (official DeepL blog)
- [GameSentenceMiner - GitHub](https://github.com/bpwhelan/GameSentenceMiner) -- HIGH confidence (open source, active development)
- [CJK OCR challenges in 2026 - DEV Community](https://dev.to/joe_wang_6a4a3e51566e8b52/why-ocr-for-cjk-languages-is-still-a-hard-problem-in-2026-and-how-im-tackling-it-5fge) -- MEDIUM confidence (developer blog)
- [Vertical Japanese Text in Android OCR - DEV Community](https://dev.to/joe_wang_6a4a3e51566e8b52/how-to-handle-vertical-japanese-text-in-android-ocr-1mj9) -- MEDIUM confidence (developer blog)

---
*Feature research for: Dual-screen Japanese game translator (AYN Thor)*
*Researched: 2026-03-02*
