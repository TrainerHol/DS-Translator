# Requirements: DS-Translator

**Defined:** 2026-03-02
**Core Value:** Clean, non-intrusive live translation of Japanese games on dual-screen hardware — game on top, translations on bottom, never interrupting gameplay.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Screen Capture

- [x] **CAPT-01**: App captures game screen via MediaProjection API with foreground service
- [ ] **CAPT-02**: User can draw a persistent capture region on the game screen
- [x] **CAPT-03**: Continuous auto-capture mode with configurable interval and change detection
- [x] **CAPT-04**: Manual capture mode via tap trigger
- [x] **CAPT-05**: OCR preprocessing pipeline (crop, upscale, binarize, orientation detection)

### OCR

- [x] **OCR-01**: Japanese text extraction via ML Kit Text Recognition v2 (bundled model)
- [x] **OCR-02**: Pluggable OCR engine architecture (interface for swapping engines)
- [x] **OCR-03**: Text-change detection (skip re-processing identical frames/text)

### Translation

- [x] **TRNS-01**: DeepL translation API integration (JA→EN)
- [x] **TRNS-02**: Pluggable translation backend (OpenAI, Claude, local fallback)
- [x] **TRNS-03**: Translation caching in local database (same text returns cached result)
- [x] **TRNS-04**: User-configurable API keys per translation engine

### Display

- [x] **DISP-01**: Translation list rendered on secondary screen via Presentation API
- [x] **DISP-02**: Each entry shows original Japanese, English translation, and play-audio button
- [x] **DISP-03**: Scrollable sentence history persisted as translation log
- [x] **DISP-04**: Presentation display lifecycle tied to foreground service (survives app switches)

### Audio

- [x] **AUD-01**: Tap any sentence on bottom screen to hear Japanese TTS
- [x] **AUD-02**: User can define a dialog region that auto-reads new text via TTS
- [x] **AUD-03**: TTS voice selection in settings

### Learning

- [x] **LRNG-01**: WaniKani API integration — fetch user's known kanji and SRS stages
- [ ] **LRNG-02**: Selective furigana — show furigana above unknown kanji only (toggleable: all/none/WaniKani-aware)
- [x] **LRNG-03**: Word segmentation via morphological analysis (kuromoji)
- [x] **LRNG-04**: Tap-to-lookup dictionary (JMdict offline, bundled)
- [x] **LRNG-05**: JLPT level indicators on segmented vocabulary (N5-N1 color-coded)

### Overlay

- [ ] **OVLY-01**: Floating bubble overlay via SYSTEM_ALERT_WINDOW for dual-screen games
- [ ] **OVLY-02**: Expandable translation panel from bubble with full translation capabilities
- [ ] **OVLY-03**: Detected words/phrases as interactive buttons (tap for translation + audio)
- [ ] **OVLY-04**: Touch passthrough when overlay is not actively interacted with
- [ ] **OVLY-05**: Overlay toggle — show/hide via bubble without interrupting gameplay

### Settings

- [x] **SETT-01**: API key management for translation engines, OCR, and WaniKani
- [x] **SETT-02**: Capture interval and OCR engine configuration
- [x] **SETT-03**: Per-game profiles (capture region, engines, TTS settings saved per game)

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Export

- **EXPT-01**: Anki sentence mining export (Japanese + translation + screenshot + audio clip)
- **EXPT-02**: AnkiDroid integration via intent API

### Advanced OCR

- **AOCR-01**: Cloud OCR engines (Google Cloud Vision) for difficult game fonts
- **AOCR-02**: Tesseract4Android as open-source OCR fallback

### Advanced Learning

- **ALRN-01**: Grammar explanation via LLM (on-demand deep dive per sentence)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Multi-language support (beyond JA→EN) | Japanese OCR is hard enough alone; WaniKani is JA-only; focus prevents quality dilution |
| Built-in SRS / flashcard system | Anki export is better than a mediocre built-in SRS; let specialized tools handle spaced repetition |
| iOS / cross-platform | Native Android APIs (Presentation, MediaProjection, SYSTEM_ALERT_WINDOW) have no cross-platform equivalent |
| Cloud backend / user accounts | All processing uses user-provided API keys; no server infrastructure to maintain |
| Real-time subtitle overlay on game screen | Obscures Japanese text learners want to see; the entire point is the second screen |
| Automatic game detection | Unreliable with emulators; manual profile selection is simple and reliable |
| Social features / shared translations | Requires backend, accounts, moderation; out of scope |
| OCR training / custom model fine-tuning | Complex UX, marginal payoff; pluggable engine architecture handles the 80% case |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| CAPT-01 | Phase 1 | Complete |
| CAPT-02 | Phase 1 | Pending |
| CAPT-03 | Phase 2 | Complete |
| CAPT-04 | Phase 1 | Complete |
| CAPT-05 | Phase 1 | Complete |
| OCR-01 | Phase 1 | Complete |
| OCR-02 | Phase 1 | Complete |
| OCR-03 | Phase 2 | Complete |
| TRNS-01 | Phase 1 | Complete |
| TRNS-02 | Phase 3 | Complete |
| TRNS-03 | Phase 2 | Complete |
| TRNS-04 | Phase 1 | Complete |
| DISP-01 | Phase 1 | Complete |
| DISP-02 | Phase 1 | Complete |
| DISP-03 | Phase 2 | Complete |
| DISP-04 | Phase 1 | Complete |
| AUD-01 | Phase 1 | Complete |
| AUD-02 | Phase 4 | Complete |
| AUD-03 | Phase 1 | Complete |
| LRNG-01 | Phase 3 | Complete |
| LRNG-02 | Phase 3 | Pending |
| LRNG-03 | Phase 3 | Complete |
| LRNG-04 | Phase 3 | Complete |
| LRNG-05 | Phase 3 | Complete |
| OVLY-01 | Phase 5 | Pending |
| OVLY-02 | Phase 5 | Pending |
| OVLY-03 | Phase 5 | Pending |
| OVLY-04 | Phase 5 | Pending |
| OVLY-05 | Phase 5 | Pending |
| SETT-01 | Phase 1 | Complete |
| SETT-02 | Phase 2 | Complete |
| SETT-03 | Phase 4 | Complete |

**Coverage:**
- v1 requirements: 32 total
- Mapped to phases: 32
- Unmapped: 0

---
*Requirements defined: 2026-03-02*
*Last updated: 2026-03-03 after Plan 02-02 completion*
