---
phase: 05-overlay-mode
plan: 01
subsystem: domain, service, settings
tags: [overlay, ocr, coordinate-mapping, state-machine, touch-passthrough, datastore]

# Dependency graph
requires:
  - phase: 01-core-pipeline
    provides: "CaptureService pipeline, SettingsRepository, OcrTextBlock, CaptureRegion, PresentationLifecycleOwner"
provides:
  - "OverlayMode enum (Off, OverlayOnSource, Panel)"
  - "OcrResult data class with coordinate metadata for overlay-on-source positioning"
  - "OverlayConfig with fraction-based sentinel sizing"
  - "OverlayCoordinateMapper pure function for bounding box to screen coordinate mapping"
  - "ScreenBounds data class for framework-free coordinate passing"
  - "TouchState enum and TouchStateFlags for WindowManager flag combinations"
  - "OverlayStateMachine for mode transition cleanup detection"
  - "PresentationLifecycleOwner as public shared class"
  - "CaptureService.latestOcrResult StateFlow"
  - "CaptureService.jmdictRepositoryRef static accessor"
  - "SettingsRepository overlay mode, per-screen config, dismiss-presentation settings"
affects: [05-02, 05-03]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ScreenBounds data class for Android-framework-free coordinate passing in unit tests"
    - "Sentinel value 0 for panel dimensions resolved at display time via resolveForDisplay()"
    - "Static companion StateFlow for cross-component OcrResult observation"
    - "testOptions.unitTests.isReturnDefaultValues for Android stub compatibility"

key-files:
  created:
    - app/src/main/java/com/dstranslator/domain/model/OverlayMode.kt
    - app/src/main/java/com/dstranslator/domain/model/OcrResult.kt
    - app/src/main/java/com/dstranslator/domain/model/OverlayConfig.kt
    - app/src/main/java/com/dstranslator/service/OverlayCoordinateMapper.kt
    - app/src/main/java/com/dstranslator/service/OverlayState.kt
    - app/src/main/java/com/dstranslator/ui/presentation/PresentationLifecycleOwner.kt
    - app/src/test/java/com/dstranslator/service/OverlayCoordinateMapperTest.kt
    - app/src/test/java/com/dstranslator/service/TouchStateTest.kt
    - app/src/test/java/com/dstranslator/service/OverlayStateTest.kt
    - app/src/test/java/com/dstranslator/data/settings/OverlaySettingsTest.kt
  modified:
    - app/src/main/java/com/dstranslator/service/CaptureService.kt
    - app/src/main/java/com/dstranslator/ui/presentation/TranslationPresentation.kt
    - app/src/main/java/com/dstranslator/data/settings/SettingsRepository.kt
    - app/build.gradle.kts

key-decisions:
  - "ScreenBounds data class instead of android.graphics.Rect for unit-testable coordinate mapper return type"
  - "Added testOptions.unitTests.isReturnDefaultValues to enable Android stub compatibility in tests"
  - "OverlayConfig serialization/deserialization as companion object static methods for testability"
  - "jmdictRepositoryRef follows existing screenCaptureManagerRef pattern for non-Hilt service access"

patterns-established:
  - "ScreenBounds: Pure Kotlin data class wrapping coordinates, with toRect() conversion for Android usage"
  - "Sentinel pattern: 0 values in OverlayConfig resolved at display time via resolveForDisplay()"
  - "TouchStateFlags: Integer constants matching Android framework values for pure unit testing"

requirements-completed: [OVLY-01, OVLY-03, OVLY-04, OVLY-05]

# Metrics
duration: 7min
completed: 2026-03-04
---

# Phase 5 Plan 1: Overlay Domain Foundation Summary

**Overlay domain models, coordinate mapper, touch state machine, and settings persistence for overlay-on-source and panel modes**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-04T07:24:14Z
- **Completed:** 2026-03-04T07:31:45Z
- **Tasks:** 3 (1 TDD, 2 standard)
- **Files modified:** 14

## Accomplishments
- Domain models (OverlayMode, OcrResult, OverlayConfig) define all overlay contracts with fraction-based sentinel sizing
- Pure coordinate mapper correctly transforms OCR bounding boxes to screen coordinates across all cases (null bbox, null region, 2x upscale)
- Touch state machine produces correct WindowManager flag combinations for all four interaction states
- CaptureService publishes OcrResult StateFlow after each OCR pass for overlay-on-source positioning
- PresentationLifecycleOwner extracted to public shared class for reuse by overlay ComposeView
- SettingsRepository extended with overlay mode, per-screen config, and dismiss-presentation preferences
- 25 unit tests passing (14 new for overlay logic + 11 new for settings) with zero regressions

## Task Commits

Each task was committed atomically:

1. **Task 1 (RED): Failing tests** - `0b56810` (test)
2. **Task 1 (GREEN): Domain models + pure logic** - `55d5812` (feat)
3. **Task 2: Extract PresentationLifecycleOwner + CaptureService OcrResult** - `5a6a088` (feat)
4. **Task 3: SettingsRepository overlay settings + tests** - `003534e` (feat)

## Files Created/Modified

### Created
- `app/src/main/java/com/dstranslator/domain/model/OverlayMode.kt` - Enum (Off, OverlayOnSource, Panel) with fromString() deserializer
- `app/src/main/java/com/dstranslator/domain/model/OcrResult.kt` - Data class bundling OCR text blocks with coordinate metadata
- `app/src/main/java/com/dstranslator/domain/model/OverlayConfig.kt` - Per-screen overlay config with sentinel-based default sizing
- `app/src/main/java/com/dstranslator/service/OverlayCoordinateMapper.kt` - Pure function mapping OCR bbox to screen coordinates + ScreenBounds
- `app/src/main/java/com/dstranslator/service/OverlayState.kt` - TouchState enum, TouchStateFlags, OverlayTransition, OverlayStateMachine
- `app/src/main/java/com/dstranslator/ui/presentation/PresentationLifecycleOwner.kt` - Shared LifecycleOwner for ComposeView in non-Activity contexts
- `app/src/test/java/com/dstranslator/service/OverlayCoordinateMapperTest.kt` - 4 tests for coordinate mapping
- `app/src/test/java/com/dstranslator/service/TouchStateTest.kt` - 4 tests for touch flag combinations
- `app/src/test/java/com/dstranslator/service/OverlayStateTest.kt` - 6 tests for state machine transitions
- `app/src/test/java/com/dstranslator/data/settings/OverlaySettingsTest.kt` - 11 tests for settings serialization

### Modified
- `app/src/main/java/com/dstranslator/service/CaptureService.kt` - Added latestOcrResult StateFlow + jmdictRepositoryRef
- `app/src/main/java/com/dstranslator/ui/presentation/TranslationPresentation.kt` - Removed private PresentationLifecycleOwner class
- `app/src/main/java/com/dstranslator/data/settings/SettingsRepository.kt` - Added overlay settings, updated snapshot/restore
- `app/build.gradle.kts` - Added testOptions.unitTests.isReturnDefaultValues

## Decisions Made

- **ScreenBounds data class instead of android.graphics.Rect**: OverlayCoordinateMapper returns ScreenBounds (pure Kotlin data class) rather than Rect, enabling unit testing without Android framework. ScreenBounds.toRect() converts for Android API usage.
- **testOptions.unitTests.isReturnDefaultValues**: Added to build.gradle.kts to allow Android stub classes (like Rect used in OcrTextBlock) to return defaults in unit tests instead of throwing RuntimeException.
- **Static companion methods for serialization**: serializeOverlayConfig/deserializeOverlayConfig are companion object methods on SettingsRepository for direct unit testing without DataStore dependency.
- **jmdictRepositoryRef pattern**: Follows existing screenCaptureManagerRef pattern for providing non-Hilt services (FloatingButtonService) access to injected repositories.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] ScreenBounds data class for testable coordinate mapping**
- **Found during:** Task 1 (OverlayCoordinateMapper implementation)
- **Issue:** android.graphics.Rect constructor throws RuntimeException("Stub!") in unit tests, even with isReturnDefaultValues (constructor body is stub, fields remain 0)
- **Fix:** Created ScreenBounds data class as return type for mapToScreenCoordinates(); added raw-coordinate overload for tests; added toRect() convenience method
- **Files modified:** OverlayCoordinateMapper.kt, OverlayCoordinateMapperTest.kt
- **Verification:** All 4 coordinate mapper tests pass
- **Committed in:** 55d5812 (Task 1 GREEN commit)

**2. [Rule 3 - Blocking] Added testOptions.unitTests.isReturnDefaultValues**
- **Found during:** Task 1 (running tests with android.graphics.Rect)
- **Issue:** OcrTextBlock contains Rect? field, instantiation in tests throws RuntimeException
- **Fix:** Added `testOptions { unitTests.isReturnDefaultValues = true }` to app/build.gradle.kts
- **Files modified:** app/build.gradle.kts
- **Verification:** Android stub classes return default values, tests can create OcrTextBlock instances
- **Committed in:** 55d5812 (Task 1 GREEN commit)

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both fixes necessary for unit test execution. No scope creep. ScreenBounds actually improves the API by providing a framework-free coordinate type.

## Issues Encountered
None beyond the auto-fixed deviations above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All domain contracts, pure logic, and settings persistence ready for Plans 02 and 03
- Plan 02 (OverlayDisplayManager, panel, source labels) can import OverlayMode, OcrResult, OverlayConfig, OverlayCoordinateMapper, TouchState/Flags, PresentationLifecycleOwner, and observe CaptureService.latestOcrResult
- Plan 03 (bubble menu, mode switching) can use OverlayStateMachine for transition logic and SettingsRepository overlay settings

## Self-Check: PASSED

All 11 created files verified present. All 4 task commits verified in git log. Full test suite passes with zero regressions.

---
*Phase: 05-overlay-mode*
*Completed: 2026-03-04*
