# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-02)

**Core value:** Clean, non-intrusive live translation of Japanese games on dual-screen hardware -- game on top, translations on bottom, never interrupting gameplay.
**Current focus:** Phase 2: Continuous Capture and Caching

## Current Position

Phase: 2 of 5 (Continuous Capture and Caching)
Plan: 2 of 3 in current phase
Status: Ready to execute Plan 02-02
Last activity: 2026-03-03 -- Plan 02-01 executed (Room database, translation cache, settings extensions)

Progress: [█████░░░░░] 30%

## Performance Metrics

**Velocity:**
- Total plans completed: 3
- Average duration: 8.3 min
- Total execution time: 0.42 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1 | 2 | 9 min | 4.5 min |
| 2 | 1 | 16 min | 16 min |

**Recent Trend:**
- Last 5 plans: 01-01 (5 min), 01-02 (4 min), 02-01 (16 min)
- Trend: Slight increase (new dependencies installed)

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: Phase 1 bundles capture, OCR, translation, display, manual capture, TTS, and basic settings into one phase to validate the full pipeline end-to-end before adding intelligence.
- [Roadmap]: Phase 5 (Overlay Mode) depends only on Phase 1, not Phases 2-4, since it is a separate display path consuming the same pipeline state.
- [01-01]: Used KSP instead of kapt for Hilt annotation processing (kapt deprecated in Kotlin 2.0)
- [01-01]: SettingsRepository splits storage: EncryptedSharedPreferences for API keys, DataStore for other settings
- [01-01]: TranslationManager never throws -- returns error message string on total failure
- [01-01]: PipelineState as sealed class with data objects for compile-time exhaustive matching
- [01-02]: TranslationPresentation uses manual PresentationLifecycleOwner since Presentation lacks ViewTree owners for ComposeView
- [01-02]: CaptureService exposes state via static companion MutableStateFlow for cross-component observability
- [01-02]: FloatingButtonService is not @AndroidEntryPoint -- creates SettingsRepository manually for simplicity
- [01-02]: Added material-icons-extended dependency for VolumeUp icon
- [01-02]: Used HorizontalDivider instead of deprecated Divider in Material3
- [01-03]: Fixed AndroidManifest activity reference from .ui.MainActivity to .ui.main.MainActivity
- [01-03]: Added CaptureService.screenCaptureManagerRef static accessor for region setup screenshot acquisition
- [02-01]: Used androidx.collection.LruCache instead of android.util.LruCache for unit test compatibility
- [02-01]: Cache key is exact Japanese source text string match (per locked architectural decision)
- [02-01]: TranslationManager stores successful translations in cache, skips error messages

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 1]: AYN Thor bottom screen must be confirmed as DISPLAY_CATEGORY_PRESENTATION -- research says yes (MEDIUM confidence) but needs hardware verification.
- [Phase 3]: Kuromoji morphological analysis library size and performance on Android is unverified -- may need lighter alternative.

## Session Continuity

Last session: 2026-03-03
Stopped at: Completed 02-01-PLAN.md
Resume file: .planning/phases/02-continuous-capture-and-caching/02-02-PLAN.md
