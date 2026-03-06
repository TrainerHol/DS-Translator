---
phase: 06-session-vocabulary-screen-tts-engine-setup-translation-list-ux-cleanup-bubble-menu-state-cleanup
plan: 01
subsystem: capture, segmentation, ui
tags: [ocr, bicubic, sudachi, material-icons, vector-drawable]

requires:
  - phase: 01-foundation
    provides: OcrPreprocessor, SudachiSegmenter, CaptureService pipeline
provides:
  - 4x bicubic OCR preprocessing with 2.0x contrast enhancement
  - Sudachi retry-on-use initialization (ensureInitialized)
  - 7 custom Material Design vector icons for bubble menu
affects: [capture-pipeline, bubble-menu, segmentation]

tech-stack:
  added: []
  patterns: [retry-on-use initialization for lazy services, custom vector drawables for consistent icon styling]

key-files:
  created:
    - app/src/main/res/drawable/ic_play_arrow.xml
    - app/src/main/res/drawable/ic_pause.xml
    - app/src/main/res/drawable/ic_edit.xml
    - app/src/main/res/drawable/ic_volume_up_filled.xml
    - app/src/main/res/drawable/ic_person.xml
    - app/src/main/res/drawable/ic_layers.xml
    - app/src/main/res/drawable/ic_swap_horiz.xml
    - app/src/test/java/com/dstranslator/data/capture/OcrPreprocessorTest.kt
  modified:
    - app/src/main/java/com/dstranslator/data/capture/OcrPreprocessor.kt
    - app/src/main/java/com/dstranslator/data/segmentation/SudachiSegmenter.kt
    - app/src/main/java/com/dstranslator/service/CaptureService.kt
    - app/src/main/res/layout/floating_bubble_menu.xml

key-decisions:
  - "OcrPreprocessor tests verify contract math (not Bitmap operations) since Robolectric is not available"
  - "ensureInitialized() uses initializationInProgress flag to prevent concurrent retry attempts"

patterns-established:
  - "Retry-on-use: ensureInitialized() pattern for services that may not be ready at startup"

requirements-completed: [P6-OCR, P6-SUDACHI, P6-ICONS]

duration: 4min
completed: 2026-03-06
---

# Phase 6 Plan 01: OCR Preprocessing, Sudachi Retry, Bubble Menu Icons Summary

**4x bicubic OCR upscale with 2.0x contrast, Sudachi retry-on-use initialization, and 7 custom Material Design vector icons replacing system drawables**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-06T05:08:22Z
- **Completed:** 2026-03-06T05:12:25Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments
- OCR preprocessing upgraded to fixed 4x bicubic upscale with 2.0x contrast enhancement for better pixel-font recognition
- SudachiSegmenter gains ensureInitialized() for reliable furigana on device even if startup init was too early
- All 7 system drawable icons replaced with consistent white-filled Material Design vectors

## Task Commits

Each task was committed atomically:

1. **Task 1: OCR preprocessing 4x bicubic + contrast boost, and Sudachi init retry** - `77cc05c` (feat)
2. **Task 2: Replace all bubble menu system drawable icons with Material Design vectors** - `7b25272` (feat)

## Files Created/Modified
- `app/src/main/java/com/dstranslator/data/capture/OcrPreprocessor.kt` - 4x bicubic upscale, 2.0x contrast
- `app/src/main/java/com/dstranslator/data/segmentation/SudachiSegmenter.kt` - ensureInitialized() with retry
- `app/src/main/java/com/dstranslator/service/CaptureService.kt` - Uses ensureInitialized() for segmentation
- `app/src/test/java/com/dstranslator/data/capture/OcrPreprocessorTest.kt` - Contract verification tests
- `app/src/main/res/drawable/ic_play_arrow.xml` - Material play_arrow filled icon
- `app/src/main/res/drawable/ic_pause.xml` - Material pause filled icon
- `app/src/main/res/drawable/ic_edit.xml` - Material edit filled icon
- `app/src/main/res/drawable/ic_volume_up_filled.xml` - Material volume_up filled icon
- `app/src/main/res/drawable/ic_person.xml` - Material person filled icon
- `app/src/main/res/drawable/ic_layers.xml` - Material layers filled icon
- `app/src/main/res/drawable/ic_swap_horiz.xml` - Material swap_horiz filled icon
- `app/src/main/res/layout/floating_bubble_menu.xml` - Updated icon references

## Decisions Made
- OcrPreprocessor tests verify contract math rather than actual Bitmap operations since Robolectric is not a project dependency
- ensureInitialized() uses an initializationInProgress flag to prevent concurrent retry attempts

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- OCR pipeline improved, ready for on-device testing
- Sudachi initialization is now resilient to timing issues
- Bubble menu has unified visual appearance

---
*Phase: 06-session-vocabulary-screen-tts-engine-setup-translation-list-ux-cleanup-bubble-menu-state-cleanup*
*Completed: 2026-03-06*

## Self-Check: PASSED

All 12 files verified present. Both commits (77cc05c, 7b25272) verified in git log. createScaledBitmap uses `true` for filter param (bicubic). ensureInitialized() present in SudachiSegmenter and used in CaptureService. Zero @android:drawable references remain in floating_bubble_menu.xml. All unit tests pass.
