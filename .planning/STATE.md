---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: completed
stopped_at: Completed 06-03-PLAN.md
last_updated: "2026-03-06T05:30:38.485Z"
last_activity: 2026-03-06 -- Plan 06-03 executed (vocabulary screen, capture permission persistence, region setup cleanup)
progress:
  total_phases: 6
  completed_phases: 4
  total_plans: 18
  completed_plans: 16
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-02)

**Core value:** Clean, non-intrusive live translation of Japanese games on dual-screen hardware -- game on top, translations on bottom, never interrupting gameplay.
**Current focus:** Phase 6: Session Vocabulary, TTS Engine, UX Cleanup

## Current Position

Phase: 6 of 6 (Session Vocabulary, TTS Engine, UX Cleanup)
Plan: 3 of 3 in current phase (COMPLETE)
Status: Plan 06-03 complete (session vocabulary screen, capture lifecycle separation, RegionSetupScreen removal)
Last activity: 2026-03-06 -- Plan 06-03 executed (vocabulary screen, capture permission persistence, region setup cleanup)

Progress: [██████████] 100%

## Performance Metrics

**Velocity:**
- Total plans completed: 13
- Average duration: 8 min
- Total execution time: 1.59 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1 | 2 | 9 min | 4.5 min |
| 2 | 2 | 36 min | 18 min |
| 3 | 3 | 22 min | 7.3 min |
| 4 | 3 | 16 min | 5.3 min |

**Recent Trend:**
- Last 5 plans: 04-02 (5 min), 04-03 (5 min), 05-02 (5 min), 05-03 (3 min), 06-01 (4 min)
- Trend: Plans consistently fast (3-5 min)

*Updated after each plan completion*
| Phase 03 P01 | 9 | 2 tasks | 8 files |
| Phase 03 P03 | 10 | 2 tasks | 17 files |
| Phase 04 P01 | 6 | 2 tasks | 14 files |
| Phase 04 P02 | 5 | 2 tasks | 6 files |
| Phase 04 P03 | 5 | 2 tasks | 6 files |
| Phase 05 P01 | 7 | 3 tasks | 14 files |
| Phase 05 P02 | 5 | 2 tasks | 4 files |
| Phase 05 P03 | 3 | 1 tasks | 3 files |
| Phase 06 P01 | 4 | 2 tasks | 12 files |
| Phase 06 P02 | 10 | 2 tasks | 7 files |
| Phase 06 P03 | 5 | 2 tasks | 9 files |

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
- [02-02]: Used while(isActive) + delay(interval) pattern instead of deprecated ticker() for continuous capture loop
- [02-02]: Bubble menu uses visibility toggles + ObjectAnimator instead of adding/removing WindowManager views
- [02-02]: Added ContinuousActive branch to MainScreen.kt when expression for exhaustive sealed class matching
- [03-02]: Sudachi dictionary copied from assets to filesDir on first launch (required for memory-mapped MappedByteBuffer access)
- [03-02]: Unit tests verify contract only; integration tests @Ignore for on-device execution with real dictionary
- [Phase 03-01]: Reused existing OkHttpClient from AppModule instead of adding duplicate Hilt binding
- [Phase 03-01]: Engine selection defaults to DeepL when settings returns null (backwards compatible)
- [03-03]: Separate JMdictDatabase from AppDatabase: read-only dictionary from asset vs mutable user data
- [03-03]: Proper Migration(1,2) instead of fallbackToDestructiveMigration to preserve cached translations
- [03-03]: Two-phase WaniKani sync: subjects first (kanji chars), then assignments (SRS stages)
- [03-03]: 1-second delay between paginated WaniKani API requests for rate limiting
- [03-03]: Added org.json:json testImplementation for unit test JSON parsing support
- [04-01]: FakeProfileDao for unit testing DAO contract without Robolectric (faster, simpler)
- [04-01]: API keys excluded from profiles -- profiles store engine/config settings only, keys are global
- [04-01]: Atomic single DataStore.edit block for profile loading prevents partial state corruption
- [04-01]: CaptureRegion.autoRead defaults to false for backward compat with existing serialized data
- [04-02]: AutoReadHelper extracted as pure object for unit testability of auto-read decision logic
- [04-02]: Canvas-drawn overlay controls (no inflated XML) for Service-context RegionEditOverlay
- [04-02]: Permission guard uses pendingRegionEdit flag + pipelineState observer for deferred overlay opening
- [04-02]: Per-region previousRegionTexts map supplements (not replaces) global previousOcrText
- [04-03]: Profiles section at TOP of settings page for prominence and easy deep-link scrolling
- [04-03]: singleTask launchMode on MainActivity for OPEN_PROFILES intent reuse
- [04-03]: NavGraph parameterized route settings?section={section} with empty default for backward compat
- [04-03]: ProfileCard uses three-dot DropdownMenu (not long press) for discoverable actions
- [Phase 05-01]: ScreenBounds data class instead of android.graphics.Rect for unit-testable coordinate mapper return type
- [Phase 05-01]: Added testOptions.unitTests.isReturnDefaultValues for Android stub test compatibility
- [Phase 05-01]: jmdictRepositoryRef follows existing screenCaptureManagerRef pattern for non-Hilt service access
- [Phase 05-02]: Canvas-drawn SourceLabelView for lightweight many-label overlay rendering (not ComposeView per label)
- [Phase 05-02]: Touch region-based panel interaction: top 36dp drag, bottom-right 48dp resize, body passes to Compose
- [Phase 05-02]: Tooltip auto-dismiss after 5 seconds via coroutine delay with cancellation
- [Phase 05]: TTS from overlay uses ACTION_SPEAK intent to CaptureService for service isolation
- [Phase 05]: Overlay mode activates independently of capture; shows empty until OCR results arrive
- [06-01]: OcrPreprocessor tests verify contract math (not Bitmap operations) since Robolectric is not available
- [06-01]: ensureInitialized() uses initializationInProgress flag to prevent concurrent retry attempts
- [06-02]: Used sherpa-onnx 1.12.28 AAR from GitHub releases instead of manual JNI .so integration
- [06-02]: Model files (157MB) excluded from git -- must be downloaded separately for build
- [06-02]: Bundled TTS engine initializes async on IO thread; system TTS always init as fallback
- [06-02]: Default engine type is "bundled" for zero-setup Japanese TTS experience
- [06-03]: ACTION_STOP stops OCR loop only; ACTION_RELEASE_CAPTURE does full MediaProjection teardown
- [06-03]: VocabularyWord as separate enriched data class (not reusing SegmentedWord)
- [06-03]: RegionSetupScreen deleted entirely -- region editing via bubble menu pencil overlay only

### Roadmap Evolution

- Phase 6 added: Session vocabulary screen, TTS engine setup, translation list UX cleanup, bubble menu state cleanup

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 1]: AYN Thor bottom screen must be confirmed as DISPLAY_CATEGORY_PRESENTATION -- research says yes (MEDIUM confidence) but needs hardware verification.
- [Phase 3]: Sudachi Java morphological analyzer is untested on Android -- must verify compatibility early in research phase. Fallback to Kuromoji if needed. APK size (~72MB dictionary) confirmed acceptable by user.

## Session Continuity

Last session: 2026-03-06T05:26:50Z
Stopped at: Completed 06-03-PLAN.md
Resume file: Phase 6 complete (all plans executed)
