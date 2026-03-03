# DS-Translator

## What This Is

A native Android (Kotlin) dual-screen translator built for the AYN Thor device. The app captures game text from the primary screen via OCR, translates it Japanese-to-English, and displays translations on the secondary Presentation display — keeping the game view clean and unobstructed. It doubles as a Japanese learning tool with WaniKani integration, furigana support, and text-to-speech.

## Core Value

Clean, non-intrusive live translation of Japanese games on dual-screen hardware — game on top, translations on bottom, never interrupting gameplay.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Capture game screen via MediaProjection API and extract Japanese text with pluggable OCR (ML Kit default, cloud options available)
- [ ] Translate detected text via pluggable translation backends (DeepL, OpenAI/GPT, Claude API, local fallback)
- [ ] Display translations on AYN Thor secondary screen using Android Presentation API (TYPE_PRESENTATION)
- [ ] Bottom screen shows scrollable list of detected sentences/phrases with original Japanese, English translation, and play-audio buttons
- [ ] WaniKani API integration: fetch user's known kanji, show furigana above unknown kanji only (toggleable — show all furigana, no furigana, or WaniKani-aware mode)
- [ ] Text-to-speech: tap any sentence on the bottom screen to hear it read aloud in Japanese
- [ ] Dialog region: user draws a persistent box on the game screen to mark the dialog area; app monitors that region for text changes and auto-reads new text via TTS
- [ ] Continuous OCR mode (default): auto-capture on a loop, detect and translate new text as it appears
- [ ] Manual capture mode: user-triggered screenshot + OCR for specific moments
- [ ] Overlay mode for dual-screen games (DS/3DS): floating bubble on the game screen; tap to reveal detected words/phrases as interactive buttons for translation and audio
- [ ] Overlay translation panel: non-intrusive overlay with full translation capabilities, toggled via the floating bubble, game remains visible and playable underneath
- [ ] Settings: configurable OCR engine, translation API keys/endpoints, TTS voice, WaniKani API key, capture interval, dialog region presets
- [ ] Display over other apps permission (SYSTEM_ALERT_WINDOW) for overlay mode without interrupting game input

### Out of Scope

- iOS/cross-platform support — native Android only, targeting AYN Thor hardware
- Languages other than Japanese→English — may expand later but v1 is focused
- Game-specific integrations or ROM hacking — purely visual OCR-based approach
- Cloud backend/user accounts — all processing is local or via user-provided API keys
- Multiplayer/streaming features — single-player translation focus

## Context

- **Target device:** AYN Thor — Android handheld with dual screens; secondary screen is TYPE_PRESENTATION (similar to external monitor/Chromecast)
- **Primary use case:** Playing Japanese games (retro emulators, 3DS/DS games, native Android games) while learning Japanese
- **Existing pain point:** Current translator apps use overlays on the game screen itself, which are messy and obstruct gameplay during live translation
- **Learning angle:** Not just translating — helping the user learn by showing furigana selectively based on their WaniKani level, making unknown kanji readable while reinforcing known ones
- **Dual-screen games:** Some systems (DS/3DS) use both screens for gameplay, so overlay mode is needed as a fallback when the secondary screen can't be dedicated to translations

## Constraints

- **Platform**: Android (Kotlin) — must use native APIs for Presentation, MediaProjection, SYSTEM_ALERT_WINDOW
- **Hardware**: AYN Thor dual-screen device — secondary screen is TYPE_PRESENTATION
- **OCR accuracy**: Japanese game text varies wildly (pixel fonts, stylized text, vertical writing) — OCR must be pluggable to swap engines when one fails
- **Latency**: Translation pipeline (capture → OCR → translate → display) must feel responsive during gameplay; sub-2-second target for the full loop
- **Permissions**: Needs MediaProjection (screen capture), SYSTEM_ALERT_WINDOW (overlay), and potentially FOREGROUND_SERVICE for continuous capture

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Native Android (Kotlin) over Flutter | Best access to Presentation API, MediaProjection, overlay system — no plugin gaps | — Pending |
| Pluggable OCR engine | Game text varies too much for one engine; ML Kit default, cloud fallback | — Pending |
| Pluggable translation API | User controls cost/quality tradeoff; DeepL, GPT, Claude, local | — Pending |
| WaniKani furigana (not replacement) | Show furigana above unknown kanji rather than replacing them — preserves kanji exposure for learning | — Pending |
| SYSTEM_ALERT_WINDOW for overlay | Required to overlay on games without interrupting input; standard Android approach | — Pending |

---
*Last updated: 2026-03-02 after initialization*
