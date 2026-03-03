# Pitfalls Research

**Domain:** Android dual-screen Japanese game translator (OCR + translation + Presentation API)
**Researched:** 2026-03-02
**Confidence:** HIGH (verified against official Android docs, WaniKani API docs, and multiple community sources)

## Critical Pitfalls

### Pitfall 1: MediaProjection Token Is Single-Use on Android 14+

**What goes wrong:**
On Android 14+, the `Intent` returned by `MediaProjectionManager.createScreenCaptureIntent()` can only be passed to `getMediaProjection()` once, and `createVirtualDisplay()` can only be called once per `MediaProjection` instance. Attempting to reuse either throws a `SecurityException`. This means every time the user starts a capture session (or the app restarts capture after a pause), the system consent dialog appears -- interrupting gameplay.

**Why it happens:**
Pre-Android 14, apps cached the consent `Intent` and reused it across sessions. Google closed this as a privacy/security measure. Developers building from older tutorials or Stack Overflow answers will hit this wall immediately.

**How to avoid:**
- Design the app to acquire the MediaProjection token once at launch and keep the foreground service running for the entire gameplay session. Never stop the projection and try to restart it without user interaction.
- Implement `MediaProjection.Callback.onStop()` to handle system-initiated stops gracefully (user dismisses via status bar chip, screen lock, another app starts projecting).
- On Android 15 QPR1+, projection auto-stops on lock screen -- the app must prompt the user to restart capture after unlock rather than silently failing.
- Consider a clear UX flow: "Start Translation Session" button that acquires projection once, then keeps it alive until the user explicitly stops.

**Warning signs:**
- `SecurityException` crashes in logs mentioning `MediaProjection`
- Users reporting "permission popup every time I switch back to the app"
- Black screen or silent capture failure after device sleep/wake

**Phase to address:**
Phase 1 (Core screen capture) -- this is foundational. Get the lifecycle right before building anything on top.

---

### Pitfall 2: MediaProjection Captures Your Own Overlay

**What goes wrong:**
When using `SYSTEM_ALERT_WINDOW` overlays (the floating bubble or translation panel in overlay mode), MediaProjection captures everything on screen -- including your own overlay UI. This means OCR processes your translation text as new game text, creating a feedback loop: capture -> OCR your own translation -> translate the translation -> display -> capture again.

**Why it happens:**
MediaProjection mirrors the entire display compositor output. There is no built-in API to exclude specific windows or layers from capture. Developers assume they can overlay and capture independently, but the display pipeline does not work that way.

**How to avoid:**
- **Primary mode (Presentation display):** This pitfall does not apply when translations are on the secondary screen via Presentation API. Prioritize this mode.
- **Overlay mode:** Before each OCR pass, temporarily hide the overlay (`View.GONE`), capture the frame, then show the overlay again. This introduces a brief flicker but prevents feedback loops.
- Alternatively, maintain a mask of overlay regions and crop/exclude those pixel areas before sending to OCR. This requires tracking overlay position and size precisely.
- Use the user-drawn "dialog region" feature as the OCR input region. If the region does not overlap with the overlay, no masking is needed.
- Consider using `FLAG_SECURE` on the overlay window -- this causes MediaProjection to render it as black, effectively excluding it from capture. However, verify this works on the AYN Thor's Android version.

**Warning signs:**
- OCR returning English text or your own UI strings
- Translation output that is a translation of a previous translation
- Infinite loop of "new text detected" events with no user interaction

**Phase to address:**
Phase 2 (Overlay mode implementation) -- must be solved before overlay mode ships.

---

### Pitfall 3: ImageReader Buffer Exhaustion Under Continuous Capture

**What goes wrong:**
Continuous OCR mode captures frames on a loop. Each frame produces an `Image` from `ImageReader`. If images are not explicitly `close()`-ed after processing, the buffer pool (set by `maxImages`) fills up. Once exhausted, `acquireLatestImage()` throws `IllegalStateException`, and the producer (virtual display) stalls -- no more frames arrive, capture silently dies.

**Why it happens:**
The OCR pipeline is slower than the capture rate. A frame arrives, gets queued for OCR processing, but the previous frame's `Image` object is still held open. With `maxImages=1`, a single slow OCR pass blocks the entire pipeline. Developers set `maxImages` too low to save memory or forget to close images in error paths.

**How to avoid:**
- Set `maxImages` to at least 2 (required for `acquireLatestImage()` to discard stale frames) but ideally 3-4 to absorb OCR processing jitter.
- Always call `image.close()` in a `finally` block or use Kotlin's `use {}` extension. Close the image immediately after copying pixels to a `Bitmap` -- do not hold the `Image` open during OCR processing.
- Copy frame data to a reusable `Bitmap` buffer, close the `Image`, then pass the `Bitmap` to the OCR pipeline asynchronously.
- Implement frame skipping: if OCR is still processing, skip the new frame entirely rather than queuing it.

**Warning signs:**
- `IllegalStateException: Unable to acquire a lockedBuffer, very likely client tries to lock more than maxImages buffers`
- Capture works for 10-30 seconds then silently stops
- Memory usage climbs steadily during capture sessions

**Phase to address:**
Phase 1 (Core screen capture) -- must be correct from the start. This is the #1 cause of "works in testing, fails in production."

---

### Pitfall 4: OCR Accuracy Collapse on Japanese Game Text

**What goes wrong:**
Japanese game text has characteristics that destroy OCR accuracy: pixel fonts with sub-24px character heights, stylized/outlined/shadowed text, transparent or gradient textbox backgrounds, vertical writing (tategaki), furigana mixed with main kanji, and mixed Hiragana/Katakana/Kanji scripts. Without preprocessing, accuracy drops from 85%+ to below 40%.

**Why it happens:**
ML Kit and Tesseract are trained primarily on document-style text with clean backgrounds and standard fonts. Game screenshots break every assumption: low resolution per character, decorative outlines, colored backgrounds, anti-aliased pixel art. Vertical text compounds the problem because standard detection models assume horizontal layout.

**How to avoid:**
- **Preprocessing pipeline is mandatory, not optional:**
  1. Crop to dialog region (user-defined or auto-detected) to eliminate background noise
  2. Upscale the cropped region to ensure each character is at least 24x24 pixels (ML Kit minimum for reliable Japanese recognition)
  3. Apply adaptive thresholding/binarization to separate text from background
  4. Detect text orientation before OCR (vertical vs horizontal) and rotate if needed
- **Pluggable OCR is correct:** ML Kit works for clean text; fall back to cloud OCR (Google Cloud Vision, manga-ocr) for stylized fonts. The pluggable architecture in PROJECT.md is the right call.
- **Test with actual game screenshots early.** Synthetic test data will give false confidence. Build a test corpus of 20+ real game screenshots across genres (RPG dialog, JRPG menus, visual novels, retro pixel games) during Phase 1.
- **Furigana stripping:** Furigana (small reading aid text above/beside kanji) confuses OCR by creating overlapping text regions. Detect and strip furigana before main OCR pass, then re-add it as annotation data.

**Warning signs:**
- OCR returns garbage characters or empty strings on game screenshots that clearly have text
- High character error rate (>20%) on pixel art fonts
- Vertical text returned as garbled horizontal fragments
- Furigana characters merged with main kanji text

**Phase to address:**
Phase 1 (OCR pipeline) must include preprocessing and real-game testing. Phase 2+ should add pluggable engine switching and fine-tuning.

---

### Pitfall 5: Foreground Service Killed While Game Is Running

**What goes wrong:**
The translation service runs as a foreground service with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`. On memory-constrained devices (the AYN Thor is a handheld gaming device), the Android Low Memory Killer (LMK) may still kill your foreground service if the game consumes most available RAM. The MediaProjection token becomes invalid, capture stops, and the user sees nothing on the secondary screen -- with no indication of what happened.

**Why it happens:**
Games, especially emulated 3DS/DS games, consume significant RAM. The AYN Thor must run your app, the emulator, and the game ROM simultaneously. While foreground services have high OOM priority, they are not immune. Additionally, some device manufacturers (including custom Android builds on gaming handhelds) apply aggressive battery optimization that kills background processes regardless of foreground service status.

**How to avoid:**
- Minimize memory footprint of the translation service: reuse bitmap buffers, avoid caching full-resolution screenshots, process and discard frames immediately.
- Request that users disable battery optimization for the app (guide them through Settings -> Apps -> Battery Optimization -> Don't optimize).
- Implement `Service.onTaskRemoved()` and `Service.onTrimMemory()` callbacks to detect impending kills and save state.
- Use `START_STICKY` or `START_REDELIVER_INTENT` for the foreground service so Android restarts it after a kill. However, the MediaProjection token will be invalid -- the app must detect this and prompt the user to re-grant capture permission.
- Display a persistent notification that clearly shows "Translation Active" so the user knows when it has stopped.
- Monitor with `ActivityManager.getRunningAppProcesses()` or `Debug.getNativeHeapAllocatedSize()` to track memory pressure.

**Warning signs:**
- Service silently stops after 15-30 minutes of gameplay
- `onStop()` callback fires without user action
- Logs show `Process died` or `Low Memory Killer` entries
- Translation display goes blank mid-game

**Phase to address:**
Phase 1 (Service architecture) and Phase 3 (Performance optimization). Design for recovery from the start.

---

### Pitfall 6: Translation API Latency Breaks Real-Time Feel

**What goes wrong:**
The full pipeline (capture -> preprocess -> OCR -> API call -> display) must complete in under 2 seconds per the project requirements. OCR alone takes 200-800ms on-device. Cloud translation API calls (DeepL, Claude, GPT) add 500-3000ms depending on the service and network conditions. The total pipeline exceeds the target, and text changes faster than translations arrive -- causing stale/out-of-order translations that confuse the user.

**Why it happens:**
Developers optimize OCR and display independently but forget the cumulative latency. Network round-trips are unpredictable. LLM-based translation (Claude, GPT) has fundamentally higher latency (2-5 seconds) than dedicated translation APIs (DeepL ~200-500ms).

**How to avoid:**
- **Tiered translation strategy:** Use DeepL as the primary real-time backend (lowest latency, ~200-500ms). Reserve Claude/GPT for a "detailed translation" mode triggered on user tap, where latency is acceptable.
- **Pipeline parallelism:** Start preprocessing frame N+1 while OCR processes frame N. Use Kotlin coroutines with dedicated dispatchers.
- **Change detection before OCR:** Compare frame hashes (perceptual hash / pHash) before running OCR. If the dialog region has not changed, skip the entire pipeline. This eliminates redundant processing for static text screens.
- **Debounce rapid text changes:** When text changes rapidly (scrolling dialog), wait for text to stabilize (200-300ms of no change) before triggering OCR + translation. Display a "..." indicator during debounce.
- **Cache translations:** Hash the OCR output text and cache translations. Identical text appearing again (common in menus, repeated dialog) gets instant results.
- **Local fallback:** Include ML Kit on-device translation as a zero-latency fallback for when network is unavailable or slow. Quality is lower but latency is near-zero.

**Warning signs:**
- Users see translations for the previous dialog line while reading the next one
- Translation display "flickers" between old and new results
- Battery drain from redundant OCR/API calls on unchanged text
- API cost unexpectedly high due to duplicate calls

**Phase to address:**
Phase 1 (basic pipeline), Phase 2 (change detection + caching), Phase 3 (tiered translation + local fallback).

---

### Pitfall 7: Presentation API Display Lifecycle Crashes

**What goes wrong:**
The `Presentation` class is a subclass of `Dialog`, not `Activity`. It has no `LifecycleOwner`, cannot use `ViewModels` directly, and crashes when the associated display becomes unavailable (disconnected, turned off, or display ID changes). On the AYN Thor specifically, the bottom screen's display ID and availability may change when the device is docked, rotated, or connected to an external monitor.

**Why it happens:**
Developers treat `Presentation` like an Activity with its own lifecycle. But it is tied to the host Activity's lifecycle. When the Activity is destroyed (configuration change, recents swipe), the Presentation is also destroyed. If the display is disconnected while the Presentation is shown, calling any method on it throws `WindowManager.BadTokenException`.

**How to avoid:**
- Register a `DisplayManager.DisplayListener` to detect display connect/disconnect events. Dismiss the Presentation before the display becomes unavailable, recreate it when the display returns.
- Tie Presentation lifecycle to the foreground service, not to any Activity. The service outlives Activity configuration changes.
- Use `DisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)` to enumerate available presentation displays, and verify the display is still valid before showing content.
- Wrap all Presentation operations in try-catch for `WindowManager.BadTokenException` and `IllegalArgumentException`.
- On the AYN Thor, the top screen is primary and bottom screen is external/presentation. Verify this with `Display.getDisplayId()` at runtime -- do not hardcode display IDs.
- Test behavior when: (a) external monitor is plugged in (bottom screen may lose presentation status), (b) device is rotated, (c) device goes to sleep.

**Warning signs:**
- `WindowManager.BadTokenException` crashes
- Blank secondary screen after device sleep/wake
- Presentation content disappears when switching apps and returning
- Display shows on wrong screen after connecting external monitor

**Phase to address:**
Phase 1 (Presentation display setup) -- this must be robust before building translation UI on it.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Hardcoding AYN Thor display IDs | Faster initial development | Breaks on firmware updates, external monitors, other devices | Never -- always enumerate displays at runtime |
| Skipping OCR preprocessing | Faster pipeline, simpler code | OCR accuracy too low for real games, forces rewrite | Never -- preprocessing is required for Japanese game text |
| Synchronous OCR on main thread | Simpler code flow | UI freezes, ANR crashes, blocked capture pipeline | Never -- always use coroutines/background threads |
| No translation cache | Simpler architecture | Redundant API calls, higher costs, higher latency | Only acceptable in first prototype; add cache in Phase 1 |
| Single OCR engine (no pluggable architecture) | Faster initial development | Locked to one engine's weaknesses, painful to swap later | Only in first prototype week; pluggable interface should be Phase 1 |
| Storing API keys in SharedPreferences plaintext | Quick to implement | Security vulnerability, keys extractable from device | Only in development; use EncryptedSharedPreferences for release |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| WaniKani API | Fetching all subjects on every app launch (6000+ items, multiple paginated requests, hits rate limit) | Cache subjects aggressively (they rarely change). Use `updated_after` filter for incremental sync. Respect 60 req/min rate limit. Use `If-None-Match`/ETag headers to avoid re-downloading unchanged data. |
| WaniKani API | Ignoring user subscription level | Check `user.subscription.max_level_granted` -- free users only have access to levels 1-3. Filter kanji data accordingly or the furigana feature shows incorrect "known" status. |
| DeepL API | Not handling rate limit 429 responses | Implement exponential backoff with jitter. Monitor `RateLimit-Remaining` header. Queue translation requests rather than firing them all simultaneously. |
| Claude/GPT API | Sending individual sentences as separate API calls | Batch multiple detected sentences into a single prompt. Reduces latency and cost. Include game context ("this is dialog from a Japanese RPG") for better translation quality. |
| ML Kit on-device OCR | Not bundling the Japanese language model | Japanese recognition requires downloading the `TextRecognizerOptions.JAPANESE` model. If not pre-bundled, first OCR attempt fails or silently returns empty results while the model downloads in the background. |
| Android TTS | Assuming Japanese voice is available | Not all devices have a Japanese TTS voice installed. Check `TextToSpeech.isLanguageAvailable(Locale.JAPANESE)` and guide users to install a voice pack if missing. |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Running OCR on every captured frame at max rate | 100% CPU, device overheating, game frame drops, battery drain in minutes | Capture at 1-2 FPS max, skip frames when OCR is still processing, use change detection to avoid redundant OCR | Immediately -- continuous full-speed capture is unsustainable |
| Full-resolution screen capture for OCR | Excessive memory usage (1080p frame = ~8MB ARGB), slow OCR processing | Capture at reduced resolution or crop to dialog region before OCR. A 400x200 crop is 10x faster than full-screen | Within minutes on a memory-constrained handheld |
| Allocating new Bitmap per frame | GC pressure causes frame drops and jank, eventual OOM | Reuse a pre-allocated Bitmap buffer. Copy ImageReader pixels into the same buffer every frame | After 50-100 frames depending on device RAM |
| Unbounded translation result list on Presentation display | Secondary screen scroll performance degrades, memory grows indefinitely during long play sessions | Cap the visible list to 50-100 entries. Archive older entries to local storage. Use RecyclerView with ViewHolder pattern | After 1-2 hours of continuous play |
| Blocking main thread with API calls | ANR dialog appears, app marked as unresponsive | All network calls on IO dispatcher. All OCR on Default dispatcher. Only UI updates on Main | Immediately with any network latency >5 seconds |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Storing translation API keys (DeepL, OpenAI, Claude) in plaintext SharedPreferences | Keys extractable via ADB backup or rooted device. User gets billed for unauthorized API usage | Use `EncryptedSharedPreferences` from AndroidX Security library. Keys are encrypted at rest with AES-256 |
| Logging OCR output or translations to Logcat in release builds | Sensitive game content or user's WaniKani level exposed in device logs accessible to other apps | Strip all OCR/translation logging in release builds using ProGuard/R8 rules or a logging wrapper |
| Not validating WaniKani API token before storing | App stores invalid token, makes requests that always fail, user confused by "no furigana" | Validate token with a lightweight API call (`/v2/user`) before saving. Show success/failure feedback |
| MediaProjection captures sensitive content (notifications, passwords) visible on screen | User's private data processed through OCR and potentially sent to cloud translation APIs | Document that the app captures screen content. Limit OCR to the user-defined dialog region only. Never send full-screen captures to cloud APIs |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Translation appears with no indication of OCR confidence | User trusts garbage translations from low-confidence OCR output | Show a confidence indicator (color-coded: green/yellow/red) on each translation. Let users know when OCR quality is low |
| Overlay bubble blocks game input | User cannot tap on game UI elements under the bubble | Use `FLAG_NOT_TOUCHABLE` on the bubble when not actively interacting. Make the bubble draggable so users can move it away from active UI areas. Keep it small and semi-transparent |
| No feedback during pipeline processing | User taps capture, nothing visible happens for 2-3 seconds | Show a subtle loading indicator on the secondary screen while OCR + translation runs. Even a "..." placeholder helps |
| Furigana displayed above every kanji regardless of WaniKani level | Defeats the learning purpose -- user never practices reading known kanji | Default to WaniKani-aware mode. Only show furigana above kanji the user has not reached in WaniKani. Make this toggleable |
| Dialog region must be re-drawn after every game/app switch | Tedious for users who play the same game repeatedly | Save dialog region presets per game/app. Auto-detect active game from foreground package name. Let users name and recall presets |
| MediaProjection consent dialog appears mid-game | Jarring interruption, user loses game context | Acquire projection before game launches. Include clear onboarding: "Start translation, then launch your game" |

## "Looks Done But Isn't" Checklist

- [ ] **Screen capture:** Works on initial launch but crashes after device sleep/wake -- verify `MediaProjection.Callback.onStop()` is handled and recovery flow works
- [ ] **OCR accuracy:** Works on clean test images but fails on actual game screenshots -- verify with 20+ real game screenshots across genres
- [ ] **Vertical text:** OCR returns text for horizontal Japanese but garbles vertical -- verify with tategaki test images and orientation detection
- [ ] **Presentation display:** Shows content once but crashes on Activity recreation (rotation, recents) -- verify with rapid Activity lifecycle cycling
- [ ] **Overlay mode:** Overlay displays correctly but game input is blocked underneath -- verify touch passthrough with `FLAG_NOT_TOUCHABLE` and opacity requirements (Android 12+)
- [ ] **Translation cache:** Cache works for exact matches but misses minor OCR variations -- verify with perceptual similarity matching, not exact string equality
- [ ] **WaniKani sync:** Initial sync works but never updates -- verify `updated_after` incremental sync runs periodically
- [ ] **Foreground service:** Notification shows but service dies after 30 minutes -- verify on actual AYN Thor hardware with a game running, check battery optimization settings
- [ ] **API key management:** Keys work when entered but are lost on app update or data clear -- verify persistence across app updates and backup/restore scenarios
- [ ] **TTS:** Works for standard sentences but crashes on empty strings or very long text -- verify edge cases with empty OCR results and multi-paragraph text

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| MediaProjection token invalid | LOW | Detect via `onStop()` callback. Show notification "Translation paused -- tap to resume." Re-acquire token on tap (one consent dialog). |
| ImageReader buffer exhaustion | LOW | Catch `IllegalStateException`, close all images, reset ImageReader, resume capture. User sees brief pause. |
| OCR accuracy too low for a game | MEDIUM | Switch to cloud OCR engine for that game. Store per-game OCR engine preference. Long-term: collect failed screenshots for fine-tuning. |
| Presentation display lost | LOW | Detect via `DisplayListener`. Show fallback overlay on primary screen. Re-create Presentation when display returns. |
| Foreground service killed | MEDIUM | Use `START_STICKY` to restart service. Detect invalid MediaProjection. Show "Session ended" notification with restart action. User must re-grant capture permission. |
| Translation API rate-limited | LOW | Queue requests, apply exponential backoff. Show cached/partial results while waiting. Fall back to on-device translation. |
| WaniKani data stale/missing | LOW | Fall back to "show all furigana" mode when WaniKani data is unavailable. Retry sync on next app launch. |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| MediaProjection single-use token | Phase 1: Core capture | Token survives 1-hour gameplay session without re-prompting consent |
| Overlay captures own UI | Phase 2: Overlay mode | OCR never returns English text or UI strings during overlay mode |
| ImageReader buffer exhaustion | Phase 1: Core capture | Continuous capture runs for 2+ hours without `IllegalStateException` |
| OCR accuracy on game text | Phase 1: OCR pipeline | >70% character accuracy on test corpus of 20 real game screenshots |
| Foreground service killed | Phase 1: Service architecture, Phase 3: Optimization | Service survives 2-hour gameplay session on AYN Thor with demanding game |
| Translation API latency | Phase 1: Basic pipeline, Phase 2: Optimization | Full pipeline (capture to display) completes in <2 seconds with DeepL |
| Presentation display lifecycle | Phase 1: Display setup | Presentation survives Activity recreation, sleep/wake, external monitor connect |
| WaniKani rate limits | Phase 2: WaniKani integration | Initial sync completes without 429 errors; incremental sync uses <5 requests |
| Overlay touch passthrough | Phase 2: Overlay mode | Game receives all touch input while overlay is visible; verified on Android 12+ |
| API key security | Phase 1: Settings | Keys not visible in ADB backup or Logcat; survive app updates |

## Sources

- [Android MediaProjection Official Documentation](https://developer.android.com/media/grow/media-projection) -- HIGH confidence: token lifecycle, consent requirements, callback handling, Android 14+ changes
- [Android 15 Behavior Changes](https://developer.android.com/about/versions/15/behavior-changes-15) -- HIGH confidence: SYSTEM_ALERT_WINDOW restrictions, foreground service timeouts
- [Android 14 Behavior Changes](https://developer.android.com/about/versions/14/behavior-changes-14) -- HIGH confidence: MediaProjection consent per-session requirement
- [WaniKani API Reference](https://docs.api.wanikani.com/) -- HIGH confidence: 60 req/min rate limit, pagination, caching headers, updated_after filter
- [Android Presentation API Reference](https://developer.android.com/reference/android/app/Presentation) -- HIGH confidence: Dialog inheritance, display lifecycle, TYPE_PRESENTATION requirements
- [ImageReader API Reference](https://developer.android.com/reference/kotlin/android/media/ImageReader) -- HIGH confidence: maxImages requirements, acquireLatestImage behavior, buffer exhaustion
- [Why OCR for CJK Languages Is Still a Hard Problem in 2026](https://dev.to/joe_wang_6a4a3e51566e8b52/why-ocr-for-cjk-languages-is-still-a-hard-problem-in-2026-and-how-im-tackling-it-5fge) -- MEDIUM confidence: vertical text challenges, CTC limitations, transformer alternatives
- [Dual-Screen Android Handheld Guide (Retro Game Corps)](https://retrogamecorps.com/2025/10/27/dual-screen-android-handheld-guide/) -- MEDIUM confidence: AYN Thor display architecture (top=primary, bottom=external/presentation)
- [Android Untrusted Touch Events](https://medium.com/androiddevelopers/untrusted-touch-events-2c0e0b9c374c) -- HIGH confidence: Android 12+ overlay touch restrictions, opacity requirements
- [Kamui - Japanese OCR and Translator for Games](https://kamui.gg/) -- MEDIUM confidence: competitor approach to game text OCR, accuracy benchmarks
- [Optimizing OCR Performance on Mobile](https://dev.to/joe_wang_6a4a3e51566e8b52/optimizing-ocr-performance-on-mobile-from-5-seconds-to-under-1-second-332m) -- MEDIUM confidence: perceptual hashing for change detection, region-based OCR optimization
- [Tesseract OCR Quality Improvement](https://tesseract-ocr.github.io/tessdoc/ImproveQuality.html) -- HIGH confidence: DPI requirements, preprocessing techniques, PSM modes

---
*Pitfalls research for: Android dual-screen Japanese game translator*
*Researched: 2026-03-02*
