---
phase: 02-continuous-capture-and-caching
plan: 02
subsystem: capture, ui
tags: [coroutines, change-detection, floating-bubble, continuous-capture, room-flow, settings-ui]

# Dependency graph
requires:
  - phase: 02-continuous-capture-and-caching
    provides: Room database, TranslationCache, TranslationHistoryDao, SettingsRepository capture interval, TranslationEntry.sessionId
provides:
  - Continuous capture loop with OCR text change detection in CaptureService
  - Expandable floating bubble menu with Play/Stop/Camera actions
  - PipelineState.ContinuousActive sealed class member
  - Capture interval slider (0.5s-10s) in Settings UI
  - Clear translation cache button in Settings UI
  - Room Flow-based translation history display on Presentation screen
affects: [03-learning-features, 04-profiles-and-auto-read, 05-overlay-mode]

# Tech tracking
tech-stack:
  added: []
  patterns: [coroutine while-loop with delay for periodic capture, OCR text comparison for change detection, expandable bubble menu with visibility toggles and animation]

key-files:
  created:
    - app/src/main/res/layout/floating_bubble_menu.xml
    - app/src/main/res/drawable/bg_floating_button_active.xml
  modified:
    - app/src/main/java/com/dstranslator/service/CaptureService.kt
    - app/src/main/java/com/dstranslator/service/FloatingButtonService.kt
    - app/src/main/java/com/dstranslator/domain/model/PipelineState.kt
    - app/src/main/java/com/dstranslator/ui/settings/SettingsScreen.kt
    - app/src/main/java/com/dstranslator/ui/settings/SettingsViewModel.kt
    - app/src/main/java/com/dstranslator/ui/main/MainScreen.kt

key-decisions:
  - "Used while(isActive) + delay(interval) pattern instead of deprecated ticker() for continuous capture loop"
  - "Bubble menu uses visibility toggles + ObjectAnimator instead of adding/removing WindowManager views"
  - "Added ContinuousActive branch to MainScreen.kt when expression for exhaustive sealed class matching"

patterns-established:
  - "Continuous capture: while(isActive) { captureAndTranslate(); delay(interval) } prevents overlap and reads interval dynamically"
  - "Change detection: compare OCR text string to previousOcrText -- consecutive identical text is always skipped"
  - "Expandable bubble menu: single root layout with visibility-toggled child buttons and staggered alpha/translationX animations"

requirements-completed: [CAPT-03, OCR-03, DISP-03, SETT-02]

# Metrics
duration: ~20min
completed: 2026-03-03
---

# Phase 2 Plan 2: Continuous Capture Loop and Bubble Menu Summary

**Continuous capture loop with OCR change detection, expandable Play/Stop/Camera bubble menu, and Settings UI for capture interval and cache management**

## Performance

- **Duration:** ~20 min (across checkpoint)
- **Started:** 2026-03-03
- **Completed:** 2026-03-03
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments
- CaptureService continuous capture loop using coroutine while-loop with configurable delay interval and OCR text change detection to skip redundant translations
- FloatingButtonService expandable bubble menu with Play (start continuous), Stop (stop continuous), and Camera (single capture) action buttons, plus active-mode visual indicator
- PipelineState extended with ContinuousActive state for sealed class exhaustive matching
- SettingsScreen capture interval slider (0.5s-10s, default 2s) and "Clear Translation Cache" button
- SettingsViewModel wired to TranslationManager.clearCache() and SettingsRepository.setCaptureIntervalMs()
- Room Flow-based history display: CaptureService inserts to Room, Flow emits to StateFlow, Presentation auto-updates

## Task Commits

Each task was committed atomically:

1. **Task 1: Add continuous capture loop with change detection and expandable bubble menu** - `73cfb01` (feat)
2. **Task 2: Add capture interval slider and clear cache button to Settings** - `a446d09` (feat)
3. **Task 3: Verify continuous capture and caching end-to-end** - checkpoint, approved by user

## Files Created/Modified
- `app/src/main/java/com/dstranslator/service/CaptureService.kt` - Continuous capture loop, ACTION_START_CONTINUOUS/ACTION_STOP_CONTINUOUS, OCR change detection via previousOcrText, Room history persistence per session
- `app/src/main/java/com/dstranslator/service/FloatingButtonService.kt` - Expandable bubble menu with Play/Stop/Camera buttons, staggered animation, active-mode background indicator
- `app/src/main/java/com/dstranslator/domain/model/PipelineState.kt` - Added ContinuousActive data object to sealed class
- `app/src/main/res/layout/floating_bubble_menu.xml` - Horizontal LinearLayout with main bubble + 3 expandable action buttons
- `app/src/main/res/drawable/bg_floating_button_active.xml` - Teal oval background for active continuous mode indicator
- `app/src/main/java/com/dstranslator/ui/settings/SettingsScreen.kt` - Capture interval slider (500f-10000f, 19 steps) and Clear Translation Cache button with confirmation feedback
- `app/src/main/java/com/dstranslator/ui/settings/SettingsViewModel.kt` - saveCaptureInterval() and clearTranslationCache() with TranslationManager dependency
- `app/src/main/java/com/dstranslator/ui/main/MainScreen.kt` - Added ContinuousActive branch to when expression for exhaustive matching

## Decisions Made
- Used `while(isActive) + delay(interval)` coroutine pattern instead of deprecated `ticker()` (marked @ObsoleteCoroutinesApi) for the continuous capture loop
- Bubble menu uses visibility toggles on child views within a single root layout, avoiding complex WindowManager add/remove cycles
- Added ContinuousActive branch to MainScreen.kt when expression -- required for exhaustive sealed class matching after adding the new state

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added ContinuousActive branch to MainScreen.kt when expression**
- **Found during:** Task 1 (assembleDebug verification)
- **Issue:** Adding PipelineState.ContinuousActive to the sealed class caused a non-exhaustive when expression compiler error in MainScreen.kt
- **Fix:** Added ContinuousActive branch to the when expression in MainScreen.kt
- **Files modified:** app/src/main/java/com/dstranslator/ui/main/MainScreen.kt
- **Verification:** assembleDebug compiles successfully
- **Committed in:** 73cfb01 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Minimal -- required for compilation after sealed class extension. No scope creep.

## Issues Encountered
None -- both tasks compiled cleanly after the MainScreen deviation fix.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Continuous capture and caching infrastructure is complete -- Phase 2 fully delivered
- Phase 3 (Learning Features) can build on the translation pipeline, cache, and history systems
- The expandable bubble menu pattern can be extended for overlay mode (Phase 5)
- Per-game profiles (Phase 4) can save/restore the capture interval setting

## Self-Check: PASSED

All 8 modified files verified on disk. Both task commits (73cfb01, a446d09) verified in git log. SUMMARY.md created successfully.

---
*Phase: 02-continuous-capture-and-caching*
*Completed: 2026-03-03*
