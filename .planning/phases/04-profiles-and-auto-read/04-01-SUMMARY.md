---
phase: 04-profiles-and-auto-read
plan: 01
subsystem: database
tags: [room, datastore, profiles, tts, json-serialization, migration]

# Dependency graph
requires:
  - phase: 03-learning-features
    provides: "AppDatabase v2, WaniKani assignments table, org.json test dependency"
provides:
  - "ProfileEntity Room entity with settingsJson/captureRegionsJson blobs"
  - "ProfileDao with CRUD, Flow listing, getDefault, count"
  - "AppDatabase v3 with MIGRATION_2_3 preserving all v2 data"
  - "CaptureRegion.autoRead field with backward-compatible false default"
  - "TranslationHistoryEntity.profileId for per-profile history tracking"
  - "SettingsRepository profile snapshot/restore with atomic DataStore writes"
  - "SettingsRepository auto-read settings (enabled, flush mode, flows)"
  - "SettingsRepository ensureDefaultProfile() initialization"
  - "TtsManager.speak() with optional queueMode parameter"
affects: [04-profiles-and-auto-read, 05-overlay-mode]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "JSON blob storage in Room entity for flexible settings snapshots"
    - "Atomic DataStore.edit for profile load (single edit block, no partial state)"
    - "FakeDao pattern for unit testing Room DAO contracts without Robolectric"
    - "Backward-compatible field additions with default values (autoRead, profileId)"

key-files:
  created:
    - "app/src/main/java/com/dstranslator/data/db/ProfileEntity.kt"
    - "app/src/main/java/com/dstranslator/data/db/ProfileDao.kt"
    - "app/src/test/java/com/dstranslator/data/db/ProfileEntityTest.kt"
    - "app/src/test/java/com/dstranslator/data/db/ProfileDaoTest.kt"
    - "app/src/test/java/com/dstranslator/data/settings/CaptureRegionSerializationTest.kt"
    - "app/src/test/java/com/dstranslator/data/settings/ProfileLoadTest.kt"
    - "app/src/test/java/com/dstranslator/data/tts/TtsManagerTest.kt"
  modified:
    - "app/src/main/java/com/dstranslator/data/db/AppDatabase.kt"
    - "app/src/main/java/com/dstranslator/data/db/TranslationHistoryEntity.kt"
    - "app/src/main/java/com/dstranslator/data/db/TranslationHistoryDao.kt"
    - "app/src/main/java/com/dstranslator/di/AppModule.kt"
    - "app/src/main/java/com/dstranslator/domain/model/CaptureRegion.kt"
    - "app/src/main/java/com/dstranslator/data/settings/SettingsRepository.kt"
    - "app/src/main/java/com/dstranslator/data/tts/TtsManager.kt"

key-decisions:
  - "FakeProfileDao for unit testing DAO contract without Robolectric (faster, simpler)"
  - "API keys excluded from profiles per user decision -- profiles store engine/config settings only"
  - "Atomic single DataStore.edit block for profile loading prevents partial state corruption"
  - "autoRead defaults to false for backward compatibility with existing CaptureRegion data"

patterns-established:
  - "FakeDao pattern: in-memory list backing for Room DAO unit tests"
  - "JSON blob pattern: flexible settings serialization in Room entities"
  - "Atomic profile load: single DataStore.edit block writes all settings at once"

requirements-completed: [SETT-03, AUD-02]

# Metrics
duration: 6min
completed: 2026-03-04
---

# Phase 4 Plan 01: Profile Data Layer Summary

**Room ProfileEntity with JSON settings blobs, ProfileDao CRUD, AppDatabase v3 migration, CaptureRegion autoRead flag, atomic profile save/load via SettingsRepository, and TtsManager queue mode support**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-04T05:44:22Z
- **Completed:** 2026-03-04T05:50:08Z
- **Tasks:** 2
- **Files modified:** 14 (7 created, 7 modified)

## Accomplishments
- ProfileEntity and ProfileDao provide full CRUD with Flow-based reactive listing and default profile support
- AppDatabase v3 migration adds profiles table and history profileId column without data loss
- SettingsRepository can atomically snapshot and restore all non-secret settings via JSON, with ensureDefaultProfile initialization
- CaptureRegion.autoRead field with backward-compatible serialization (legacy JSON without autoRead key defaults to false)
- TtsManager.speak() accepts optional queueMode for QUEUE_FLUSH (interrupt) or QUEUE_ADD (queue) behavior

## Task Commits

Each task was committed atomically:

1. **Task 1: Profile data layer** (TDD)
   - `9897d4f` test(04-01): add failing tests for profile data layer
   - `9b8bb11` feat(04-01): implement profile data layer with Room entity, DAO, DB migration
2. **Task 2: SettingsRepository profile save/load, TtsManager queue mode** (TDD)
   - `2cbbd2e` test(04-01): add tests for profile save/load and TtsManager queue mode
   - `aad7446` feat(04-01): add profile save/load, auto-read settings, TtsManager queue mode

## Files Created/Modified
- `app/src/main/java/com/dstranslator/data/db/ProfileEntity.kt` - Room entity for profile storage with JSON settings blob
- `app/src/main/java/com/dstranslator/data/db/ProfileDao.kt` - DAO with CRUD, Flow listing, getDefault, count
- `app/src/main/java/com/dstranslator/data/db/AppDatabase.kt` - Updated to v3 with ProfileEntity registered
- `app/src/main/java/com/dstranslator/data/db/TranslationHistoryEntity.kt` - Added profileId field
- `app/src/main/java/com/dstranslator/data/db/TranslationHistoryDao.kt` - Added getByProfile and deleteByProfile
- `app/src/main/java/com/dstranslator/di/AppModule.kt` - MIGRATION_2_3, provideProfileDao binding
- `app/src/main/java/com/dstranslator/domain/model/CaptureRegion.kt` - Added autoRead: Boolean = false
- `app/src/main/java/com/dstranslator/data/settings/SettingsRepository.kt` - Profile snapshot/restore, auto-read settings, default profile init
- `app/src/main/java/com/dstranslator/data/tts/TtsManager.kt` - Optional queueMode parameter on speak()
- `app/src/test/java/com/dstranslator/data/db/ProfileEntityTest.kt` - Settings JSON and captureRegions JSON roundtrip tests
- `app/src/test/java/com/dstranslator/data/db/ProfileDaoTest.kt` - CRUD operation tests with FakeProfileDao
- `app/src/test/java/com/dstranslator/data/settings/CaptureRegionSerializationTest.kt` - autoRead serialization and legacy format tests
- `app/src/test/java/com/dstranslator/data/settings/ProfileLoadTest.kt` - Profile snapshot, load, default profile tests
- `app/src/test/java/com/dstranslator/data/tts/TtsManagerTest.kt` - Queue mode verification tests

## Decisions Made
- Used FakeProfileDao (in-memory list) for unit testing DAO contract instead of Robolectric -- faster test execution, simpler setup
- API keys excluded from profile snapshots per user's explicit decision -- profiles store engine/config settings only, keys are global
- Atomic single DataStore.edit block for loadSettingsFromSnapshot prevents partial state corruption during profile switching
- autoRead defaults to false ensuring backward compatibility with existing serialized CaptureRegion data (legacy JSON without autoRead key)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Profile data layer complete: ProfileEntity, ProfileDao, and SettingsRepository profile methods ready for service/UI layer
- AppDatabase v3 migration tested and verified
- CaptureRegion autoRead and TtsManager queue mode ready for auto-read pipeline
- ensureDefaultProfile() ready to be called during CaptureService initialization

## Self-Check: PASSED

All 14 files verified present. All 4 task commits verified in git log.

---
*Phase: 04-profiles-and-auto-read*
*Completed: 2026-03-04*
