# Roadmap: DS-Translator

## Overview

DS-Translator delivers a live Japanese game translator for the AYN Thor dual-screen handheld. The roadmap moves from a working capture-to-display pipeline (Phase 1), through intelligent continuous capture (Phase 2), into learning features that differentiate from all competitors (Phase 3), then per-game profiles and auto-read polish (Phase 4), and finally overlay mode for dual-screen games that need both physical screens (Phase 5). Each phase delivers a complete, testable capability on real hardware.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Capture-to-Display Pipeline** - End-to-end screen capture, OCR, translation, and secondary screen display with manual capture and TTS
- [x] **Phase 2: Continuous Capture and Caching** - Change detection, auto-capture loop, translation caching, and scrollable history
- [ ] **Phase 3: Learning Features** - WaniKani furigana, word segmentation, tap-to-lookup dictionary, JLPT indicators, and additional translation engines
- [ ] **Phase 4: Profiles and Auto-Read** - Per-game profiles and dialog region auto-read via TTS
- [ ] **Phase 5: Overlay Mode** - Floating bubble overlay with translation panel for dual-screen games (DS/3DS)

## Phase Details

### Phase 1: Capture-to-Display Pipeline
**Goal**: User can capture a game screen, extract Japanese text, translate it, and see the translation on the AYN Thor bottom screen
**Depends on**: Nothing (first phase)
**Requirements**: CAPT-01, CAPT-02, CAPT-04, CAPT-05, OCR-01, OCR-02, TRNS-01, TRNS-04, DISP-01, DISP-02, DISP-04, AUD-01, AUD-03, SETT-01
**Success Criteria** (what must be TRUE):
  1. User grants screen capture permission once and the capture session persists through app switches and game changes
  2. User can draw a capture region on the game screen and that region persists across captures
  3. User taps a button and the app captures the screen, extracts Japanese text via OCR, translates it via DeepL, and displays the result on the bottom screen within 2 seconds
  4. Each translation entry on the bottom screen shows original Japanese text, English translation, and a play-audio button that reads the Japanese aloud via TTS
  5. User can configure DeepL API key, TTS voice, and OCR settings from a settings screen
**Plans**: 3 plans across 3 waves

Plans:
- [x] 01-01: Project scaffold, domain layer, engine implementations, settings (Wave 1)
- [x] 01-02: Capture service, pipeline orchestration, floating button, Presentation display (Wave 2)
- [ ] 01-03: App UI screens, region setup, settings UI, end-to-end wiring (Wave 3)

### Phase 2: Continuous Capture and Caching
**Goal**: App automatically detects new game dialog and translates it without user intervention, with cached results and scrollable history
**Depends on**: Phase 1
**Requirements**: CAPT-03, OCR-03, TRNS-03, DISP-03, SETT-02
**Success Criteria** (what must be TRUE):
  1. User enables continuous mode and the app automatically captures, OCRs, and translates new dialog text as it appears on screen
  2. When game dialog has not changed, the app skips OCR and translation (no redundant API calls, no duplicate entries)
  3. Previously translated text returns instantly from cache without hitting the translation API
  4. Bottom screen shows a scrollable history of all translations from the current session, persisted as a translation log
  5. User can configure the capture interval from settings
**Plans**: 2 plans across 2 waves

Plans:
- [x] 02-01: Room database, translation cache (LRU + Room), history persistence, settings extensions (Wave 1)
- [x] 02-02: Continuous capture loop, change detection, expandable bubble menu, settings UI, end-to-end verification (Wave 2)

### Phase 3: Learning Features
**Goal**: Users can learn Japanese while playing, with selective furigana based on their WaniKani level, interactive word segmentation, dictionary lookup, and JLPT difficulty indicators
**Depends on**: Phase 2
**Requirements**: LRNG-01, LRNG-02, LRNG-03, LRNG-04, LRNG-05, TRNS-02
**Success Criteria** (what must be TRUE):
  1. User connects their WaniKani account and the app shows furigana only above kanji they have not yet learned (with toggle for all/none/WaniKani-aware modes)
  2. Each translated sentence is segmented into individual words, and tapping any word shows its dictionary definition from the bundled JMdict database
  3. Segmented words display color-coded JLPT level indicators (N5 through N1)
  4. User can switch between DeepL, OpenAI, and Claude translation backends from settings, each using their own API key
**Plans**: 4 plans across 2 waves

Plans:
- [ ] 03-01: OpenAI and Claude translation engines, TranslationManager engine selection, SettingsRepository extensions (Wave 1)
- [ ] 03-02: Domain models (SegmentedWord, DictionaryResult, FuriganaSegment), Sudachi morphological analyzer integration (Wave 1)
- [ ] 03-03: JMdict dictionary database, WaniKani API sync and local cache, Room entities and repositories (Wave 1)
- [ ] 03-04: Presentation UI integration -- FuriganaText, JlptIndicator, DictionaryPopup, segmented word display, settings UI (Wave 2)

### Phase 4: Profiles and Auto-Read
**Goal**: App remembers per-game settings and can automatically read new dialog text aloud
**Depends on**: Phase 3
**Requirements**: AUD-02, SETT-03
**Success Criteria** (what must be TRUE):
  1. User can save a named profile per game that stores capture region, OCR engine, translation engine, and TTS settings, and loading that profile restores all settings
  2. User can define a dialog region on the game screen and the app automatically reads new text in that region aloud via TTS as it appears
**Plans**: TBD

Plans:
- [ ] 04-01: TBD

### Phase 5: Overlay Mode
**Goal**: Users playing dual-screen games (DS/3DS) where the bottom screen shows game content can access translations via a floating overlay on the game screen
**Depends on**: Phase 1 (uses same pipeline; does not require Phases 2-4)
**Requirements**: OVLY-01, OVLY-02, OVLY-03, OVLY-04, OVLY-05
**Success Criteria** (what must be TRUE):
  1. A floating bubble appears over the game screen via SYSTEM_ALERT_WINDOW permission, and the game remains playable underneath
  2. Tapping the bubble expands a translation panel showing detected words/phrases as interactive buttons with translation and audio
  3. When the overlay panel is collapsed, touch events pass through to the game without interference
  4. The overlay does not appear in its own screen capture (no OCR feedback loop)
**Plans**: TBD

Plans:
- [ ] 05-01: TBD
- [ ] 05-02: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Capture-to-Display Pipeline | 2/3 | In progress | - |
| 2. Continuous Capture and Caching | 2/2 | Complete | 2026-03-03 |
| 3. Learning Features | 0/4 | Not started | - |
| 4. Profiles and Auto-Read | 0/1 | Not started | - |
| 5. Overlay Mode | 0/2 | Not started | - |
