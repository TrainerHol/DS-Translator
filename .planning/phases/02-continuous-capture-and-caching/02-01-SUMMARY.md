---
phase: 02-continuous-capture-and-caching
plan: 01
subsystem: database, cache
tags: [room, lru-cache, sqlite, datastore, hilt, kotlin-coroutines]

# Dependency graph
requires:
  - phase: 01-capture-to-display-pipeline
    provides: TranslationManager, SettingsRepository, TranslationEntry, AppModule, Hilt DI
provides:
  - Room database (AppDatabase) with cached_translations and translation_history tables
  - TranslationCache with two-tier LRU in-memory + Room persistent lookup
  - Cache-integrated TranslationManager (cache-first before API calls)
  - SettingsRepository capture interval setting (2s default, 0.5s-10s range)
  - TranslationEntry.sessionId for per-session history grouping
  - Hilt providers for AppDatabase, DAOs, and TranslationCache
affects: [02-continuous-capture-and-caching, 03-smart-deduplication, 04-persistence-and-history]

# Tech tracking
tech-stack:
  added: [androidx.room 2.7.1, androidx.collection.LruCache]
  patterns: [two-tier cache (LRU + Room), cache-first translation lookup, Flow-based DAO queries]

key-files:
  created:
    - app/src/main/java/com/dstranslator/data/db/AppDatabase.kt
    - app/src/main/java/com/dstranslator/data/db/CachedTranslationEntity.kt
    - app/src/main/java/com/dstranslator/data/db/CachedTranslationDao.kt
    - app/src/main/java/com/dstranslator/data/db/TranslationHistoryEntity.kt
    - app/src/main/java/com/dstranslator/data/db/TranslationHistoryDao.kt
    - app/src/main/java/com/dstranslator/data/cache/TranslationCache.kt
    - app/src/test/java/com/dstranslator/data/cache/TranslationCacheTest.kt
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - app/src/main/java/com/dstranslator/di/AppModule.kt
    - app/src/main/java/com/dstranslator/di/TranslationModule.kt
    - app/src/main/java/com/dstranslator/data/translation/TranslationManager.kt
    - app/src/main/java/com/dstranslator/data/settings/SettingsRepository.kt
    - app/src/main/java/com/dstranslator/domain/model/TranslationEntry.kt
    - app/src/test/java/com/dstranslator/data/translation/TranslationManagerTest.kt

key-decisions:
  - "Used androidx.collection.LruCache instead of android.util.LruCache for unit test compatibility"
  - "Cache key is exact Japanese source text string match (per locked decision from planning)"
  - "TranslationManager stores successful translations in cache, skips error messages"

patterns-established:
  - "Two-tier cache: in-memory LruCache fronting Room database for O(1) hot lookups with persistent backing"
  - "Cache-first translation: check cache before API, store after successful API call"
  - "Flow-based DAO queries: TranslationHistoryDao.getBySession returns Flow for reactive UI"

requirements-completed: [TRNS-03, DISP-03, SETT-02]

# Metrics
duration: 16min
completed: 2026-03-03
---

# Phase 2 Plan 1: Room Database and Translation Cache Summary

**Room database with two-tier LRU+Room translation cache, cache-integrated TranslationManager, and configurable capture interval settings**

## Performance

- **Duration:** 16 min
- **Started:** 2026-03-03T03:30:33Z
- **Completed:** 2026-03-03T03:47:12Z
- **Tasks:** 2
- **Files modified:** 18

## Accomplishments
- Room database with cached_translations (source text keyed) and translation_history (session-grouped) tables
- TranslationCache implementing two-tier lookup: LRU in-memory (500 entries) -> Room persistent -> null
- TranslationManager integrated with cache-first lookup before DeepL/ML Kit API chain
- SettingsRepository extended with capture interval (2s default, 0.5s-10s clamped range, Flow accessor)
- TranslationEntry extended with optional sessionId for per-session history grouping
- 4 unit tests for TranslationCache behaviors, all existing tests updated and passing

## Task Commits

Each task was committed atomically:

1. **Task 1: Create Room database with cache and history entities, DAOs, and TranslationCache** - `a04f2db` (feat)
2. **Task 2: Extend TranslationManager with cache, SettingsRepository with capture interval, and TranslationEntry with sessionId** - `9a381fb` (feat)

## Files Created/Modified
- `app/src/main/java/com/dstranslator/data/db/AppDatabase.kt` - Room database with cache and history tables
- `app/src/main/java/com/dstranslator/data/db/CachedTranslationEntity.kt` - Cache table entity (sourceText primary key)
- `app/src/main/java/com/dstranslator/data/db/CachedTranslationDao.kt` - DAO with findBySourceText, insert, deleteAll, count
- `app/src/main/java/com/dstranslator/data/db/TranslationHistoryEntity.kt` - History table entity with sessionId grouping
- `app/src/main/java/com/dstranslator/data/db/TranslationHistoryDao.kt` - DAO with insert, getBySession (Flow), deleteAll
- `app/src/main/java/com/dstranslator/data/cache/TranslationCache.kt` - Two-tier LRU + Room cache implementation
- `app/src/main/java/com/dstranslator/data/translation/TranslationManager.kt` - Cache-first translate() with clearCache()
- `app/src/main/java/com/dstranslator/data/settings/SettingsRepository.kt` - Capture interval with Flow and suspend accessors
- `app/src/main/java/com/dstranslator/domain/model/TranslationEntry.kt` - Added sessionId: String? field
- `app/src/main/java/com/dstranslator/di/AppModule.kt` - Hilt providers for AppDatabase, DAOs, TranslationCache
- `app/src/main/java/com/dstranslator/di/TranslationModule.kt` - Updated TranslationManager provider with cache param
- `gradle/libs.versions.toml` - Added Room 2.7.1 dependency
- `app/build.gradle.kts` - Added Room dependencies and META-INF packaging exclusion
- `app/src/test/java/com/dstranslator/data/cache/TranslationCacheTest.kt` - 4 behavior tests for TranslationCache
- `app/src/test/java/com/dstranslator/data/translation/TranslationManagerTest.kt` - Updated with cache mock

## Decisions Made
- Used `androidx.collection.LruCache` instead of `android.util.LruCache` for unit test compatibility -- android.util classes are not mocked in JVM unit tests
- TranslationManager only caches successful translations (skips results starting with "Translation failed:")
- Cache key is exact Japanese source text string match (per locked architectural decision)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Switched from android.util.LruCache to androidx.collection.LruCache**
- **Found during:** Task 1 (TDD RED phase)
- **Issue:** android.util.LruCache throws "not mocked" RuntimeException in JVM unit tests
- **Fix:** Used androidx.collection.LruCache (pure JVM, available transitively via Room)
- **Files modified:** app/src/main/java/com/dstranslator/data/cache/TranslationCache.kt
- **Verification:** All 4 TranslationCacheTest cases pass
- **Committed in:** a04f2db (Task 1 commit)

**2. [Rule 3 - Blocking] Updated TranslationModule with TranslationCache parameter**
- **Found during:** Task 2 (assembleDebug verification)
- **Issue:** TranslationModule.provideTranslationManager() missing new translationCache parameter
- **Fix:** Added TranslationCache parameter to provideTranslationManager()
- **Files modified:** app/src/main/java/com/dstranslator/di/TranslationModule.kt
- **Verification:** assembleDebug compiles successfully
- **Committed in:** 9a381fb (Task 2 commit)

**3. [Rule 3 - Blocking] Updated TranslationManagerTest with cache mock**
- **Found during:** Task 2 (test verification)
- **Issue:** Existing TranslationManagerTest missing translationCache constructor arg
- **Fix:** Added mockk(relaxed=true) cache with default null returns
- **Files modified:** app/src/test/java/com/dstranslator/data/translation/TranslationManagerTest.kt
- **Verification:** All 6 existing tests pass
- **Committed in:** 9a381fb (Task 2 commit)

**4. [Rule 3 - Blocking] Added META-INF/DEPENDENCIES packaging exclusion**
- **Found during:** Task 2 (assembleDebug verification)
- **Issue:** Duplicate META-INF/DEPENDENCIES from httpcomponents JARs (DeepL dependency)
- **Fix:** Added packaging.resources.excludes in build.gradle.kts
- **Files modified:** app/build.gradle.kts
- **Verification:** assembleDebug succeeds
- **Committed in:** 9a381fb (Task 2 commit)

**5. [Rule 3 - Blocking] Created placeholder launcher icon and gradle.properties**
- **Found during:** Task 1 (build verification)
- **Issue:** Missing mipmap/ic_launcher resource and android.useAndroidX property
- **Fix:** Created minimal 48x48 PNG icon and gradle.properties with AndroidX flag
- **Files modified:** app/src/main/res/mipmap-hdpi/ic_launcher.png, gradle.properties
- **Verification:** Build passes resource linking
- **Committed in:** a04f2db (Task 1 commit)

---

**Total deviations:** 5 auto-fixed (5 blocking)
**Impact on plan:** All auto-fixes were necessary to resolve build/test failures. No scope creep -- all fixes directly required for the current task's code to compile and tests to pass.

## Issues Encountered
- No JDK or Android SDK was installed on the development machine. Installed OpenJDK 17 via Homebrew and Android SDK via android-commandlinetools cask during execution. This added approximately 8 minutes to execution time.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Room database infrastructure ready for continuous capture loop (Plan 02-02) to write history entries
- TranslationCache ready for CaptureService to leverage cached translations
- Capture interval setting ready for UI binding in settings screen
- TranslationEntry.sessionId ready for CaptureService to tag entries per session

## Self-Check: PASSED

All 8 created files verified on disk. Both task commits (a04f2db, 9a381fb) verified in git log.

---
*Phase: 02-continuous-capture-and-caching*
*Completed: 2026-03-03*
