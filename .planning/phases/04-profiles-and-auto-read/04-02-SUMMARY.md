---
phase: 04-profiles-and-auto-read
plan: 02
subsystem: service
tags: [overlay, windowmanager, tts, auto-read, bubble-menu, capture-regions, permission-guard]

# Dependency graph
requires:
  - phase: 04-profiles-and-auto-read
    provides: "CaptureRegion.autoRead field, SettingsRepository auto-read settings and flows, TtsManager queue mode, ProfileDao"
provides:
  - "RegionEditOverlay fullscreen custom View for live region drawing with touch blocking"
  - "FloatingButtonService with pencil, auto-read toggle, and profile buttons"
  - "MediaProjection permission guard on pencil button (pendingRegionEdit pattern)"
  - "CaptureService per-region text tracking via previousRegionTexts map"
  - "Auto-read hook using AutoReadHelper.shouldAutoRead() pure function"
  - "AutoReadHelper testable helper for text change detection and queue mode"
  - "Updated floating_bubble_menu.xml with 6 action buttons"
affects: [04-profiles-and-auto-read, 05-overlay-mode]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Permission guard pattern: check screenCaptureManagerRef, set pending flag, observe pipeline state"
    - "Canvas-drawn control buttons: simple shapes on overlay View instead of inflated XML views"
    - "AutoReadHelper pure function extraction for testability of service logic"
    - "Per-region text tracking map replacing global previousOcrText for multi-region auto-read"

key-files:
  created:
    - "app/src/main/java/com/dstranslator/service/RegionEditOverlay.kt"
    - "app/src/main/java/com/dstranslator/service/AutoReadHelper.kt"
    - "app/src/test/java/com/dstranslator/service/AutoReadTest.kt"
  modified:
    - "app/src/main/java/com/dstranslator/service/FloatingButtonService.kt"
    - "app/src/main/java/com/dstranslator/service/CaptureService.kt"
    - "app/src/main/res/layout/floating_bubble_menu.xml"

key-decisions:
  - "Extracted auto-read decision logic into AutoReadHelper pure object for unit testability"
  - "Canvas-drawn control buttons on overlay (no inflated XML) for simplicity in Service context"
  - "Permission guard uses pendingRegionEdit flag + pipelineState observer pattern"
  - "Per-region text map tracks OCR text independently per region ID for multi-region auto-read"

patterns-established:
  - "Permission guard pattern: check static ref, set pending flag, observe state for deferred action"
  - "Pure helper extraction: service logic extracted to companion/object for unit testing without mocks"
  - "Canvas overlay controls: draw buttons directly on canvas for Service-context overlays"

requirements-completed: [AUD-02, SETT-03]

# Metrics
duration: 5min
completed: 2026-03-04
---

# Phase 4 Plan 02: Service Layer for Region Editing and Auto-Read Summary

**RegionEditOverlay for live game-screen region drawing, bubble menu with pencil/auto-read/profile buttons, per-region auto-read TTS hook in CaptureService with text change detection**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-04T05:53:13Z
- **Completed:** 2026-03-04T05:58:43Z
- **Tasks:** 2
- **Files modified:** 6 (3 created, 3 modified)

## Accomplishments
- RegionEditOverlay provides fullscreen touch-blocking overlay for live region drawing with draw-then-adjust UX, per-region autoRead toggle, and confirm/cancel controls
- FloatingButtonService gains 3 new buttons (pencil with permission guard, auto-read toggle with Flow-based state sync, profile launcher) with staggered expand/collapse animation
- CaptureService auto-read hook tracks text per-region via previousRegionTexts map, uses AutoReadHelper.shouldAutoRead() pure function, and speaks via TtsManager with configurable queue mode
- All 9 AutoReadTest unit tests pass covering text changed/unchanged/blank/null scenarios and flush vs queue modes

## Task Commits

Each task was committed atomically:

1. **Task 1: RegionEditOverlay** - `36609bc` (feat)
2. **Task 2: FloatingButtonService + CaptureService + layout** (TDD)
   - `dc76633` test(04-02): add failing tests for auto-read text change detection
   - `221a854` feat(04-02): implement bubble menu buttons, auto-read hook, and permission guard

## Files Created/Modified
- `app/src/main/java/com/dstranslator/service/RegionEditOverlay.kt` - Fullscreen overlay View for live region drawing with 8-handle resize, auto-read toggle, confirm/cancel
- `app/src/main/java/com/dstranslator/service/AutoReadHelper.kt` - Pure helper: shouldAutoRead() and getQueueMode() for testable auto-read logic
- `app/src/test/java/com/dstranslator/service/AutoReadTest.kt` - 9 unit tests for auto-read text change detection
- `app/src/main/java/com/dstranslator/service/FloatingButtonService.kt` - Added pencil/auto-read/profile buttons, permission guard, overlay creation, auto-read state observer
- `app/src/main/java/com/dstranslator/service/CaptureService.kt` - Per-region text tracking, auto-read TTS hook, previousRegionTexts map, ACTION_OPEN_REGION_EDIT
- `app/src/main/res/layout/floating_bubble_menu.xml` - 3 new ImageView buttons: btn_region_edit, btn_auto_read_toggle, btn_profile

## Decisions Made
- Extracted auto-read decision logic into standalone AutoReadHelper object rather than CaptureService companion -- cleaner separation, easier to test without service mocking
- Canvas-drawn control buttons on overlay instead of inflated XML views -- simpler in Service context where no layout inflater host is naturally available for fullscreen overlays
- Permission guard uses pendingRegionEdit boolean flag + pipeline state Flow observer -- lightweight pattern that doesn't require new broadcast receivers or callbacks
- Per-region text map (previousRegionTexts) supplements but does not replace global previousOcrText -- global still used for change detection (skip duplicate captures), per-region used for auto-read TTS decisions

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Service layer complete: RegionEditOverlay, updated FloatingButtonService, auto-read CaptureService ready for UI integration
- Plan 04-03 (UI layer) can add settings screen profiles section, region setup screen updates, and MainActivity intent handling
- All auto-read and region editing infrastructure functional pending on-device verification

---
*Phase: 04-profiles-and-auto-read*
*Completed: 2026-03-04*
