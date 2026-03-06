---
phase: 06-session-vocabulary-screen-tts-engine-setup-translation-list-ux-cleanup-bubble-menu-state-cleanup
plan: 03
subsystem: ui, capture
tags: [vocabulary, session-words, jlpt, tts, media-projection, navigation]

requires:
  - phase: 01-complete-pipeline-manual-capture-tts-settings
    provides: CaptureService, TranslationEntry, NavGraph, MainScreen, TtsManager
  - phase: 03-segmentation-dictionary-wanikani
    provides: JMdictRepository, SegmentedWord, JlptIndicator

provides:
  - Session vocabulary screen showing deduplicated words with JLPT badges and audio
  - VocabularyViewModel extracting and enriching words from CaptureService.translations
  - Separated capture permission lifecycle (stop OCR without releasing MediaProjection)
  - RegionSetupScreen fully removed from navigation

affects: [navigation, capture-pipeline, main-screen]

tech-stack:
  added: []
  patterns: [capture lifecycle separation (OCR loop vs MediaProjection), vocabulary extraction with deduplication]

key-files:
  created:
    - app/src/main/java/com/dstranslator/ui/vocabulary/VocabularyScreen.kt
    - app/src/main/java/com/dstranslator/ui/vocabulary/VocabularyViewModel.kt
    - app/src/test/java/com/dstranslator/ui/vocabulary/VocabularyViewModelTest.kt
  modified:
    - app/src/main/java/com/dstranslator/ui/navigation/NavGraph.kt
    - app/src/main/java/com/dstranslator/ui/main/MainScreen.kt
    - app/src/main/java/com/dstranslator/ui/main/MainActivity.kt
    - app/src/main/java/com/dstranslator/service/CaptureService.kt

key-decisions:
  - "ACTION_STOP stops OCR loop only; ACTION_RELEASE_CAPTURE does full teardown including MediaProjection"
  - "VocabularyWord is a separate data class (not reusing SegmentedWord) to hold enriched JLPT/definition data"
  - "RegionSetupScreen deleted entirely (not emptied) since no external code imports it"

patterns-established:
  - "Capture lifecycle separation: OCR loop lifecycle independent of MediaProjection session"

requirements-completed: [P6-VOCAB, P6-REGION]

duration: 5min
completed: 2026-03-06
---

# Phase 6 Plan 03: Session Vocabulary Screen and Capture Lifecycle Separation Summary

**Session vocabulary screen with deduplicated JLPT-badged word cards, capture permission persistence across OCR stops, and RegionSetupScreen removal**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-06T05:21:53Z
- **Completed:** 2026-03-06T05:26:50Z
- **Tasks:** 2
- **Files modified:** 7 (+ 2 deleted)

## Accomplishments
- VocabularyScreen shows deduplicated session words with surface form, reading, definition, JLPT badge, and audio playback
- Capture permission lifecycle separated: stopping OCR preserves MediaProjection for instant restart
- RegionSetupScreen and RegionSetupViewModel fully removed from codebase and navigation

## Task Commits

Each task was committed atomically:

1. **Task 1: Create VocabularyScreen, VocabularyViewModel, and wire navigation** - `c8badee` (feat)
2. **Task 2: Capture permission lifecycle separation and cleanup** - `de97d33` (feat)

## Files Created/Modified
- `app/src/main/java/com/dstranslator/ui/vocabulary/VocabularyScreen.kt` - Session vocabulary list with word cards, empty state
- `app/src/main/java/com/dstranslator/ui/vocabulary/VocabularyViewModel.kt` - Word extraction, deduplication by dictionaryForm, JLPT/definition enrichment
- `app/src/test/java/com/dstranslator/ui/vocabulary/VocabularyViewModelTest.kt` - 6 tests: dedup, OOV filtering, single-char filtering, chronological order, enrichment, empty state
- `app/src/main/java/com/dstranslator/ui/navigation/NavGraph.kt` - Replaced region_setup route with vocabulary route
- `app/src/main/java/com/dstranslator/ui/main/MainScreen.kt` - Replaced Set Region button with Vocabulary button (FormatListBulleted icon)
- `app/src/main/java/com/dstranslator/ui/main/MainActivity.kt` - Added onDestroy with ACTION_RELEASE_CAPTURE for full teardown
- `app/src/main/java/com/dstranslator/service/CaptureService.kt` - ACTION_STOP stops loop only; ACTION_RELEASE_CAPTURE for full teardown
- `app/src/main/java/com/dstranslator/ui/region/RegionSetupScreen.kt` - DELETED
- `app/src/main/java/com/dstranslator/ui/region/RegionSetupViewModel.kt` - DELETED

## Decisions Made
- ACTION_STOP stops OCR loop only; new ACTION_RELEASE_CAPTURE handles full MediaProjection teardown (called on app destroy)
- VocabularyWord as separate data class with enriched JLPT/definition data, not reusing SegmentedWord
- RegionSetupScreen deleted entirely since no external code imports it (region editing handled by bubble menu pencil overlay)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 6 complete: all 3 plans executed
- Session vocabulary, bundled TTS, OCR preprocessing, and capture lifecycle improvements ready for on-device testing

---
*Phase: 06-session-vocabulary-screen-tts-engine-setup-translation-list-ux-cleanup-bubble-menu-state-cleanup*
*Completed: 2026-03-06*

## Self-Check: PASSED

All 7 source files verified present. Both deleted files confirmed removed. Both commits (c8badee, de97d33) verified in git log. No references to RegionSetupScreen or region_setup remain. Build succeeds. All unit tests pass (6/6 VocabularyViewModelTest + full suite).
