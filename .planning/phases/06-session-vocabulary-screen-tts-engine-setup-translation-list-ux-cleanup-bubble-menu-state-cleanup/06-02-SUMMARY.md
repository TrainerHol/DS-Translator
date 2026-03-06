---
phase: 06-session-vocabulary-screen-tts-engine-setup-translation-list-ux-cleanup-bubble-menu-state-cleanup
plan: 02
subsystem: tts
tags: [sherpa-onnx, kokoro, tts, on-device, japanese, audio]

requires:
  - phase: 01-complete-pipeline-manual-capture-tts-settings
    provides: TtsManager, SettingsRepository, SettingsScreen

provides:
  - SherpaOnnxTtsEngine wrapper with Kokoro multi-lang v1.0 int8 model
  - Dual-engine TTS dispatch (bundled Kokoro default, system TTS fallback)
  - TTS engine type selector in SettingsScreen
  - Random Japanese voice selection per playback (male and female IDs 37-41)
  - ttsEngineType preference in SettingsRepository with profile snapshot support

affects: [settings, tts, overlay-tts-playback]

tech-stack:
  added: [sherpa-onnx 1.12.28 AAR, kokoro-multi-lang-v1_0 int8 model]
  patterns: [dual-engine TTS dispatch, asset-to-filesDir model copy]

key-files:
  created:
    - app/src/main/java/com/dstranslator/data/tts/SherpaOnnxTtsEngine.kt
  modified:
    - app/src/main/java/com/dstranslator/data/tts/TtsManager.kt
    - app/src/main/java/com/dstranslator/data/settings/SettingsRepository.kt
    - app/src/main/java/com/dstranslator/ui/settings/SettingsScreen.kt
    - app/src/main/java/com/dstranslator/ui/settings/SettingsViewModel.kt
    - app/build.gradle.kts
    - app/src/test/java/com/dstranslator/data/tts/TtsManagerTest.kt

key-decisions:
  - "Used sherpa-onnx 1.12.28 AAR from GitHub releases instead of manual JNI integration"
  - "Model files and AAR excluded from git via .gitignore (too large for source control)"
  - "Bundled engine initializes async on IO thread; system TTS always init as fallback"
  - "OfflineTts created from filesystem paths (not AssetManager) after asset copy"

patterns-established:
  - "Dual-engine TTS: dispatch via EngineType enum, auto-fallback on bundled failure"
  - "Large model asset copy: marker file pattern to skip re-copy on subsequent launches"

requirements-completed: [P6-TTS]

duration: 10min
completed: 2026-03-06
---

# Phase 6 Plan 02: TTS Engine Setup Summary

**Bundled on-device Japanese TTS via sherpa-onnx Kokoro with random male/female voice selection and dual-engine dispatch**

## Performance

- **Duration:** 10 min
- **Started:** 2026-03-06T05:08:45Z
- **Completed:** 2026-03-06T05:18:43Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Integrated sherpa-onnx 1.12.28 AAR with Kokoro multi-lang v1.0 int8 model (~157MB assets)
- SherpaOnnxTtsEngine provides zero-setup Japanese TTS with 5 voices (4 female, 1 male)
- TtsManager dispatches to bundled or system engine based on user preference, with auto-fallback
- Settings UI shows engine type radio selector; system voice picker hidden when bundled is active
- Tests verify engine type parsing, voice ID coverage (37-41), and random selection coverage

## Task Commits

Each task was committed atomically:

1. **Task 1: Integrate sherpa-onnx native library and create SherpaOnnxTtsEngine** - `b6fe348` (feat)
2. **Task 2: Extend TtsManager for dual-engine dispatch, add settings UI, and test voice randomization** - `0cd0cac` (feat)

## Files Created/Modified
- `app/src/main/java/com/dstranslator/data/tts/SherpaOnnxTtsEngine.kt` - Kokoro TTS wrapper with random voice selection, model asset copy, AudioTrack playback
- `app/src/main/java/com/dstranslator/data/tts/TtsManager.kt` - Extended with EngineType enum, dual dispatch, bundled/system fallback
- `app/src/main/java/com/dstranslator/data/settings/SettingsRepository.kt` - Added ttsEngineType get/set/flow and profile snapshot support
- `app/src/main/java/com/dstranslator/ui/settings/SettingsScreen.kt` - TTS engine radio selector, conditional voice picker
- `app/src/main/java/com/dstranslator/ui/settings/SettingsViewModel.kt` - ttsEngineType StateFlow with save method
- `app/build.gradle.kts` - sherpa-onnx AAR dependency, noCompress for model files
- `app/src/test/java/com/dstranslator/data/tts/TtsManagerTest.kt` - Engine type, voice ID, and randomization tests
- `.gitignore` - Exclude large model files and AAR from source control

## Decisions Made
- Used sherpa-onnx AAR from GitHub releases (simpler than manual JNI .so integration)
- Model files excluded from git (157MB total) -- must be downloaded separately for build
- OfflineTts instantiated from filesystem paths after asset-to-filesDir copy (same pattern as Sudachi)
- Default engine type is "bundled" for zero-setup experience
- numThreads=2 for TTS inference to balance speed and battery

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed numSpeakers/sampleRate property vs function call**
- **Found during:** Task 1 (SherpaOnnxTtsEngine compilation)
- **Issue:** Used Kotlin property syntax for numSpeakers/sampleRate but AAR exposes them as functions
- **Fix:** Changed to function call syntax numSpeakers()/sampleRate()
- **Files modified:** SherpaOnnxTtsEngine.kt
- **Verification:** Compilation succeeded
- **Committed in:** b6fe348

**2. [Rule 1 - Bug] Removed duplicate getCurrentEngineType() method**
- **Found during:** Task 2 (TtsManager compilation)
- **Issue:** Explicit getCurrentEngineType() clashed with Kotlin-generated getter for currentEngineType property
- **Fix:** Removed the explicit method, relying on property getter
- **Files modified:** TtsManager.kt
- **Verification:** Compilation succeeded
- **Committed in:** 0cd0cac

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Minor API usage fixes. No scope creep.

## Issues Encountered
- espeak-ng-data files required for Japanese phonemization had to be downloaded file-by-file from HuggingFace (no single archive available)
- Java home not set in environment; resolved by using /opt/homebrew/opt/openjdk@17

## User Setup Required
Model files must be present for the app to build and run:
- Download sherpa-onnx AAR to `app/libs/sherpa-onnx-1.12.28.aar`
- Download Kokoro model files to `app/src/main/assets/sherpa-onnx-kokoro/` (model.int8.onnx, voices.bin, tokens.txt, espeak-ng-data/)

## Next Phase Readiness
- TTS engine integration complete, ready for session vocabulary screen (Plan 01) and remaining Phase 6 work
- On-device testing recommended to verify Japanese phonemizer produces valid audio output

---
*Phase: 06-session-vocabulary-screen-tts-engine-setup-translation-list-ux-cleanup-bubble-menu-state-cleanup*
*Completed: 2026-03-06*
