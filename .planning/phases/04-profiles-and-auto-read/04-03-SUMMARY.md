---
phase: 04-profiles-and-auto-read
plan: 03
subsystem: ui
tags: [compose, profiles, auto-read, tts, deep-link, navigation, settings, material3]

# Dependency graph
requires:
  - phase: 04-profiles-and-auto-read
    provides: "ProfileEntity, ProfileDao, SettingsRepository profile snapshot/restore, auto-read settings, TtsManager queue mode, RegionEditOverlay, FloatingButtonService buttons, auto-read hook"
provides:
  - "SettingsViewModel profile CRUD (save, load, rename, delete) with auto-naming"
  - "SettingsViewModel auto-read enabled/flush mode management"
  - "SettingsScreen ProfilesSection with save dialog, profile cards, rename/delete dialogs"
  - "SettingsScreen AutoReadSection with enabled switch and flush/queue radio group"
  - "NavGraph settings?section=profiles deep link route"
  - "MainActivity OPEN_PROFILES and START_CAPTURE_THEN_EDIT_REGIONS intent handling"
  - "CaptureService ensureDefaultProfile on startup"
  - "singleTask launchMode for intent reuse"
affects: [05-overlay-mode]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Deep link via NavGraph route parameter with defaultValue for backward-compatible navigation"
    - "singleTask launchMode + onNewIntent for Service-to-Activity intent communication"
    - "ProfileCard with three-dot DropdownMenu for contextual actions"
    - "AnimatedVisibility for conditional UI sections (auto-read mode selector)"

key-files:
  created: []
  modified:
    - "app/src/main/java/com/dstranslator/ui/settings/SettingsViewModel.kt"
    - "app/src/main/java/com/dstranslator/ui/settings/SettingsScreen.kt"
    - "app/src/main/java/com/dstranslator/ui/navigation/NavGraph.kt"
    - "app/src/main/java/com/dstranslator/ui/main/MainActivity.kt"
    - "app/src/main/java/com/dstranslator/service/CaptureService.kt"
    - "app/src/main/AndroidManifest.xml"

key-decisions:
  - "Profiles section placed at TOP of settings page for prominence and easy deep-link scrolling"
  - "singleTask launchMode on MainActivity to reuse existing instance for OPEN_PROFILES intent"
  - "NavGraph uses parameterized route settings?section={section} with empty default for backward compat"
  - "ProfileCard uses three-dot DropdownMenu (not long press) for discoverable rename/delete actions"

patterns-established:
  - "Deep link navigation: parameterized route with defaultValue replaces fixed route"
  - "Service-to-Activity intent: singleTask + onNewIntent + action constants in companion"
  - "ViewModel auto-refresh: refreshAllSettings() re-reads all StateFlows after profile load"

requirements-completed: [SETT-03, AUD-02]

# Metrics
duration: 5min
completed: 2026-03-04
---

# Phase 4 Plan 03: Profile UI and Auto-Read Settings Summary

**SettingsScreen with profiles section (save/load/rename/delete), auto-read toggle with flush/queue mode, NavGraph deep link, and MainActivity intent handling for bubble menu profile navigation**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-04T06:01:41Z
- **Completed:** 2026-03-04T06:06:50Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- SettingsViewModel gains full profile CRUD (save with auto-naming, load with atomic settings restore, rename, delete with Default guard) and auto-read enabled/flush mode management
- SettingsScreen has prominent Profiles section at top with save dialog, profile cards (active indicator, Default label, three-dot menu), and Auto-Read section with switch and flush/queue radio group
- NavGraph supports `settings?section=profiles` deep link for bubble menu profile button navigation
- MainActivity handles OPEN_PROFILES and START_CAPTURE_THEN_EDIT_REGIONS intents from FloatingButtonService with singleTask launchMode
- CaptureService calls ensureDefaultProfile on startup so app always operates within a profile

## Task Commits

Each task was committed atomically:

1. **Task 1: SettingsViewModel profile CRUD and auto-read management** - `b268f9f` (feat)
2. **Task 2: SettingsScreen profiles section, auto-read UI, NavGraph deep link, MainActivity intent** - `bdacf25` (feat)

## Files Created/Modified
- `app/src/main/java/com/dstranslator/ui/settings/SettingsViewModel.kt` - Profile CRUD methods, auto-read StateFlows, refreshAllSettings helper
- `app/src/main/java/com/dstranslator/ui/settings/SettingsScreen.kt` - ProfilesSection, ProfileCard, SaveProfileDialog, RenameProfileDialog, DeleteProfileDialog, AutoReadSection composables
- `app/src/main/java/com/dstranslator/ui/navigation/NavGraph.kt` - Parameterized settings route with section argument
- `app/src/main/java/com/dstranslator/ui/main/MainActivity.kt` - onNewIntent, handleActionIntent, OPEN_PROFILES/START_CAPTURE_THEN_EDIT_REGIONS constants
- `app/src/main/java/com/dstranslator/service/CaptureService.kt` - ProfileDao injection, ensureDefaultProfile call in handleStart
- `app/src/main/AndroidManifest.xml` - singleTask launchMode on MainActivity

## Decisions Made
- Profiles section placed at TOP of settings page -- most prominent section for quick access and trivial deep-link scrolling (scroll to 0)
- Used singleTask launchMode on MainActivity so OPEN_PROFILES intent from FloatingButtonService reuses existing activity instead of creating a new one
- NavGraph uses parameterized route `settings?section={section}` with empty default value, backward compatible with existing `navigate("settings")` calls
- ProfileCard uses three-dot DropdownMenu (MoreVert icon) for rename/delete actions -- more discoverable than long-press which has no visual affordance

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 4 complete: all 3 plans (data layer, service layer, UI layer) delivered
- Profile management fully wired: data -> service -> UI with atomic save/load
- Auto-read pipeline complete: region flags -> text change detection -> TTS speak
- Region editing overlay accessible via bubble menu pencil icon
- Ready for Phase 5 (Overlay Mode) which depends on Phase 1 pipeline + Phase 4 profiles

## Self-Check: PASSED

All 6 modified files verified present. Both task commits verified in git log.

---
*Phase: 04-profiles-and-auto-read*
*Completed: 2026-03-04*
