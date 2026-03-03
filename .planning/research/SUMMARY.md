# Project Research Summary

**Project:** DS-Translator — Native Android dual-screen Japanese game translator
**Domain:** Mobile OCR translation app with learning features (Android, AYN Thor handheld)
**Researched:** 2026-03-02
**Confidence:** HIGH (stack + pitfalls), MEDIUM-HIGH (features + architecture)

## Executive Summary

DS-Translator is a native Android app targeting the AYN Thor dual-screen handheld device. It captures the primary game screen via MediaProjection, runs Japanese OCR on dialog regions, translates the text via pluggable cloud APIs (DeepL primary), and renders the result on the secondary screen using the Android Presentation API. The core value proposition — live translation on a dedicated second screen without obscuring gameplay — has zero direct competitors. Every existing tool (Kamui, Gaminik, Screen Translate AI OCR, Kaku) overlays on the game screen. The secondary display is the entire product, and it must be built as such from day one.

The recommended approach is a layered coroutine-driven pipeline: a foreground service owns the MediaProjection token for the session lifetime, a change-detection loop (perceptual frame hashing) throttles OCR to changed frames only, pluggable OCR and translation engine interfaces allow per-game switching, and a shared StateFlow connects the service to both display surfaces (Presentation display and SYSTEM_ALERT_WINDOW overlay fallback). The stack is modern Kotlin-first Android: AGP 9.0.1, Jetpack Compose (BOM 2026.02.01), Hilt with KSP, Room, DataStore, Ktor, and ML Kit Japanese OCR. These are all well-documented, mutually compatible, and carry no significant dependency risk.

The key risks are Android platform-specific: the MediaProjection token is single-use on Android 14+ (design around one session = one token), ImageReader buffer exhaustion silently kills capture if Images are not closed immediately, and the Presentation API is a Dialog subclass that requires careful lifecycle management tied to the foreground service rather than to any Activity. OCR accuracy on stylized game fonts requires mandatory bitmap preprocessing (upscale, binarize, orient) before any ML Kit call — skipping this produces failure rates above 60% on pixel-art Japanese text. Address all three of these in Phase 1 before building any learning features on top.

## Key Findings

### Recommended Stack

The stack is entirely Kotlin-first and modern, with no legacy dependencies. Kotlin 2.3.10 with AGP 9.0.1 (built-in Kotlin support, no separate plugin) and KSP (not KAPT) for all annotation processing. Hilt 2.57.1 provides compile-time DI. Jetpack Compose with BOM 2026.02.01 covers UI on both displays. ML Kit 16.0.1 with the bundled Japanese model handles on-device OCR with ~140ms average latency. Ktor 3.4.0 (OkHttp engine) handles all network calls. Room 2.7.x with Kotlin code generation stores translation history and WaniKani cache. DataStore 1.2.0 stores settings and API keys.

See [STACK.md](.planning/research/STACK.md) for full dependency list, version compatibility matrix, and Gradle snippets.

**Core technologies:**
- Kotlin 2.3.10 + AGP 9.0.1: Primary language + build system — only language Google actively supports; built-in Kotlin plugin in AGP 9.0
- Jetpack Compose (BOM 2026.02.01): Declarative UI on both screens — reactive state management fits the translation pipeline naturally
- Hilt 2.57.1 (KSP): DI framework — compile-time correctness; powers pluggable OCR/translation engine swapping
- ML Kit Japanese 16.0.1 (bundled): Default on-device OCR — ~140ms latency, no Play Services dependency, offline-capable
- Ktor 3.4.0: HTTP client for all APIs — Kotlin-native, coroutine-first, single HTTP stack for DeepL/OpenAI/WaniKani
- Room 2.7.x + DataStore 1.2.0: Local storage — translation cache, WaniKani subjects cache, user settings
- MediaProjection API: Screen capture foundation — only official Android screen capture mechanism
- Presentation API: Secondary display rendering — correct API for TYPE_PRESENTATION displays like the AYN Thor bottom screen

### Expected Features

Research identified nine table-stakes features (missing any means users pick a competitor) and ten differentiators (no competitor combines all of these). The competitive gap is clear: no Android app has learning features, and no app uses a dedicated second screen.

See [FEATURES.md](.planning/research/FEATURES.md) for full competitor matrix and prioritization.

**Must have (table stakes):**
- Screen capture via MediaProjection — foundational input; no OCR without it
- Japanese OCR with configurable capture region — region selection is the single highest-impact accuracy feature
- Translation to English (DeepL primary) — core output; users expect at least one working engine
- Secondary screen display (Presentation API) — the product's entire value proposition
- Manual and continuous auto-capture modes — users expect both tap-to-capture and "set it and forget it"
- TTS playback — tap a sentence to hear Japanese pronunciation; low cost, high learning value
- Basic settings — API keys, capture interval, OCR engine selection

**Should have (competitive differentiators):**
- WaniKani-aware furigana — furigana shown only above kanji the user hasn't learned; unique in the market
- Word segmentation + tap-to-lookup dictionary — transforms the second screen into interactive reading comprehension
- Per-game profiles — saves capture region, OCR engine, and translation engine per game
- Sentence history / translation log — scrollable game transcript on secondary screen
- JLPT level indicators on vocabulary — color-coded difficulty alongside segmented words
- Additional translation backends — OpenAI and Claude for context-aware game dialog translation

**Defer (v2+):**
- Overlay mode for DS/3DS dual-screen games — high complexity, separate UX paradigm, narrow use case
- Dialog region auto-read (TTS narrator) — requires extremely reliable change detection; premature implementation creates false-trigger annoyance
- Anki sentence mining export — strong demand but needs stable history pipeline first
- Cloud OCR engines — add when users hit ML Kit accuracy limits on specific games
- Grammar explanation (LLM-powered) — expensive, slow, and quality is unreliable

### Architecture Approach

The architecture follows Clean Architecture with a domain layer of pure Kotlin use cases, a data layer of pluggable engine implementations (OcrEngine and TranslationEngine interfaces), and a foreground service as the long-running owner of all MediaProjection resources. A shared @Singleton StateFlow/SharedFlow (TranslationPipelineState) connects the service to all UI surfaces — the Presentation display, the main Activity, and the optional overlay — without any direct coupling. The pipeline is: CaptureService -> ChangeDetector -> OCR Engine -> TextDiffEngine -> Translation Engine -> FuriganaProcessor -> PipelineState -> displays.

See [ARCHITECTURE.md](.planning/research/ARCHITECTURE.md) for full component diagram, data flow diagrams, anti-patterns, and build order.

**Major components:**
1. CaptureService (Foreground Service) — owns MediaProjection, VirtualDisplay, and ImageReader; runs the capture loop; never bound to Activity lifecycle
2. TranslationPipeline (processing layer) — orchestrates capture -> OCR -> translate -> annotate; runs entirely on Dispatchers.IO; emits to PipelineState
3. TranslationPresentation (Presentation subclass) — renders translation list on AYN Thor bottom screen via ComposeView; owned by CaptureService, not any Activity
4. OCR Engine (pluggable interface) — MlKitOcrEngine default; CloudOcrEngine fallback; swappable via Hilt without service restart
5. Translation Engine (pluggable interface) — DeepLEngine primary; OpenAiEngine, ClaudeEngine as alternatives; same interface pattern as OCR
6. WaniKani Repository + FuriganaProcessor — fetches kanji knowledge, caches in Room, annotates OCR output with selective furigana
7. OverlayManager — SYSTEM_ALERT_WINDOW floating bubble + panel; separate display path for DS/3DS games; consumes same PipelineState

### Critical Pitfalls

All seven critical pitfalls from research are well-documented with prevention strategies. Five must be addressed in Phase 1 before any learning features.

See [PITFALLS.md](.planning/research/PITFALLS.md) for full detail including warning signs, recovery strategies, and phase mapping.

1. **MediaProjection token is single-use on Android 14+** — Design around one session = one token. Foreground service acquires the token once at session start and keeps it alive. Handle `MediaProjection.Callback.onStop()` for system-initiated stops. Re-prompt user after lock screen on Android 15+.
2. **ImageReader buffer exhaustion kills capture** — Set maxImages to 3-4. Copy pixels to a Bitmap immediately, close the Image in a `finally` block, then pass the Bitmap to OCR asynchronously. Never hold an Image open during OCR processing.
3. **Presentation display lifecycle crashes** — Tie the Presentation to the foreground service, not any Activity. Register `DisplayManager.DisplayListener` for connect/disconnect. Wrap all Presentation operations in try-catch for `BadTokenException`. Never hardcode display IDs.
4. **OCR accuracy collapse on game text** — Mandatory preprocessing pipeline: crop to dialog region, upscale to 24x24px minimum per character, apply adaptive binarization, detect orientation. Test with 20+ real game screenshots during Phase 1, not synthetic data.
5. **Foreground service killed under memory pressure** — Reuse bitmap buffers, process and discard frames immediately. Guide users to disable battery optimization. Use START_STICKY for auto-restart. Display persistent notification showing capture status.
6. **Translation API latency breaks real-time feel** — DeepL as primary (200-500ms). Reserve LLMs (Claude/GPT, 2-5 seconds) for on-demand "deep dive" mode. Cache translations in Room keyed by Japanese text. Implement two-tier diff: frame hash before OCR, text diff before translation.
7. **Overlay captures own UI (feedback loop)** — This pitfall only applies to overlay mode. Presentation display mode is immune. For overlay mode: maintain the user-drawn capture region strictly within the game area, or use `FLAG_SECURE` on the overlay window to render it as black in MediaProjection output.

## Implications for Roadmap

Based on the dependency chain from ARCHITECTURE.md and pitfall-to-phase mapping from PITFALLS.md, the natural phase order is: get capture and display working correctly before adding pipeline intelligence, then add the learning layer, then add edge-case modes.

### Phase 1: Foundation — Capture, OCR, Translation, and Secondary Display

**Rationale:** Every feature in the app depends on this pipeline. The three most dangerous pitfalls (single-use MediaProjection token, ImageReader buffer exhaustion, Presentation lifecycle crashes) must be addressed here before anything is built on top. Building anything else before this is solid wastes all subsequent work.

**Delivers:** A working end-to-end pipeline: screen capture -> region crop -> ML Kit Japanese OCR -> DeepL translation -> scrollable translation list on the AYN Thor bottom screen. Manual capture mode. Basic settings (API keys, capture region). OCR preprocessing pipeline with real game screenshot testing.

**Addresses features:** Screen capture, Japanese OCR, configurable capture region, DeepL translation, secondary screen display, manual capture mode, basic settings, TTS playback.

**Avoids pitfalls:** MediaProjection single-use token (service lifecycle design), ImageReader buffer exhaustion (close-immediately pattern), Presentation display lifecycle crashes (service ownership, DisplayListener), OCR accuracy collapse (preprocessing pipeline + real game test corpus).

**Research flag:** Needs focused attention on the Presentation API + ComposeView lifecycle wiring pattern — this is niche with fewer reference implementations. Also needs real AYN Thor hardware testing for display ID enumeration and behavior on sleep/wake.

### Phase 2: Pipeline Intelligence — Change Detection, Caching, and Continuous Capture

**Rationale:** Phase 1 delivers a working but naive pipeline that re-runs OCR and translation on every frame, regardless of whether dialog has changed. This makes continuous auto-capture mode impractical due to battery drain, API cost, and latency pile-up. Phase 2 makes continuous capture viable.

**Delivers:** Continuous auto-capture mode with perceptual frame hashing (skip unchanged frames), text-level diff (skip unchanged OCR output before translation), translation caching in Room (identical Japanese text returns cached result instantly), debounce for rapidly-changing text, configurable capture interval.

**Addresses features:** Continuous auto-capture mode (now viable), sentence history / translation log (cache becomes history).

**Avoids pitfalls:** Translation API latency (caching + diff), API cost overruns (diff eliminates redundant calls), foreground service memory pressure (bitmap buffer reuse).

**Research flag:** Standard patterns — well-documented. Perceptual hashing (pHash) for Android bitmaps has established implementations. No deep research needed.

### Phase 3: Learning Features — WaniKani, Word Segmentation, and Dictionary

**Rationale:** With the capture-translate-display pipeline stable, the learning differentiators can be added. These build on the stable OCR text output and require the Room database already set up in Phase 2. This is where DS-Translator becomes categorically different from all competitors.

**Delivers:** WaniKani API integration with local caching, selective furigana (only above kanji below the user's current SRS stage), morphological word segmentation (kuromoji), tap-to-lookup dictionary (JMdict bundled offline), JLPT level indicators on segmented words, additional translation backends (OpenAI, Claude).

**Addresses features:** WaniKani-aware furigana, word segmentation + tap-to-lookup, JLPT indicators, additional translation engines.

**Avoids pitfalls:** WaniKani rate limit abuse (use `updated_after` incremental sync, aggressive 24h cache, respect 60 req/min), incorrect known-kanji status (check `user.subscription.max_level_granted` for free tier users).

**Research flag:** Morphological analysis for Japanese (kuromoji/MeCab) on Android needs validation — this is the most research-uncertain part of the learning layer. Kuromoji is JVM-based and may have size/performance concerns on Android. Verify before committing to this approach.

### Phase 4: Per-Game Profiles and Quality of Life

**Rationale:** Once the core pipeline and learning features are working, users will encounter friction from reconfiguring the same settings every game session. Per-game profiles eliminate this. This phase also addresses the "looks done but isn't" checklist items from PITFALLS.md.

**Delivers:** Per-game profiles (capture region, OCR engine, translation engine, TTS settings stored per game name/label), OCR confidence indicators on translations, loading states during pipeline processing, memory optimization (bitmap buffer reuse, translation list capping at 50-100 entries), EncryptedSharedPreferences for API keys.

**Addresses features:** Per-game profiles, security hardening, UX polish.

**Avoids pitfalls:** Unbounded translation list memory growth, API key security, OCR confidence not surfaced to users.

**Research flag:** Standard patterns — all well-documented Android APIs. No deep research needed.

### Phase 5: Overlay Mode for DS/3DS Dual-Screen Games

**Rationale:** Overlay mode is a separate UX paradigm from the Presentation display mode. It targets a narrower use case (games where both physical screens are game content), has significantly higher complexity (touch passthrough, overlay-captures-own-UI risk), and must be deferred until the primary mode is solid. Android 12+ opacity requirements for overlay touch passthrough add platform-specific complexity.

**Delivers:** SYSTEM_ALERT_WINDOW floating bubble + expandable translation panel, touch passthrough verification (`FLAG_NOT_TOUCHABLE` when not interacting), overlay region exclusion from OCR (prevent feedback loop), Android 12+ opacity compliance.

**Addresses features:** Overlay mode for DS/3DS games.

**Avoids pitfalls:** Overlay captures own UI (OCR feedback loop), overlay touch blocking game input.

**Research flag:** Needs research on Android 12+ overlay opacity requirements and touch passthrough behavior — official docs are clear but AYN Thor-specific behavior (custom Android build) needs hardware verification.

### Phase Ordering Rationale

- **Phase 1 before everything:** MediaProjection, Presentation API, and OCR preprocessing are the hardest technical problems and everything else depends on them being correct.
- **Phase 2 before Phase 3:** Continuous capture must work reliably before adding the learning layer — a pipeline that burns battery or re-calls APIs constantly will overshadow any learning features.
- **Phase 3 before Phase 4:** Learning features must work before polishing per-game profile UX — there's nothing to profile-save until the features are proven.
- **Phase 5 last:** Overlay mode is a separate subsystem that adds complexity without helping the primary use case. Building it before the primary display mode is stable creates two partially-broken systems instead of one solid one.

### Research Flags

Phases likely needing `/gsd:research-phase` during planning:
- **Phase 1:** Presentation API + ComposeView lifecycle wiring is niche — fewer reference implementations than standard Compose patterns. AYN Thor-specific display behavior (top=primary, bottom=TYPE_PRESENTATION) needs hardware validation.
- **Phase 3:** Kuromoji/MeCab morphological analysis on Android — JVM library size and performance on mobile needs validation before committing to this dependency.
- **Phase 5:** Android 12+ overlay touch passthrough opacity rules on AYN Thor's custom Android build.

Phases with standard patterns (skip research-phase):
- **Phase 2:** Perceptual hashing, coroutine loops, Room caching — all well-documented.
- **Phase 4:** Per-game profiles (key-value store), EncryptedSharedPreferences, list memory management — all standard Android patterns.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All dependencies verified on Maven Central/official release notes. Version compatibility matrix confirmed. No risky bets. |
| Features | MEDIUM-HIGH | Competitor analysis is MEDIUM (Play Store listings, not internal data). Table stakes and differentiators are well-reasoned. AYN Thor ecosystem gap is HIGH confidence from community sources. |
| Architecture | MEDIUM-HIGH | Official Android APIs are HIGH confidence. Presentation API + ComposeView integration pattern is MEDIUM (niche, fewer reference implementations). Dual-screen + OCR pipeline integration has limited prior art. |
| Pitfalls | HIGH | All critical pitfalls verified against official Android documentation. MediaProjection, ImageReader, and Presentation API behaviors are official-doc-verified. OCR accuracy issues corroborated by community sources. |

**Overall confidence:** HIGH for the approach; MEDIUM for two specific integration points (Presentation+Compose lifecycle wiring, morphological analysis on Android).

### Gaps to Address

- **Morphological analysis library for Android:** Kuromoji is the obvious JVM choice but its size and performance on mobile is unverified. Validate during Phase 3 planning — may need a lighter alternative or a different segmentation approach.
- **AYN Thor display enumeration behavior:** The bottom screen must be confirmed as a `DISPLAY_CATEGORY_PRESENTATION` display. Research says it is (community sources, MEDIUM confidence), but needs hardware verification in Phase 1. If it isn't a TYPE_PRESENTATION display, the entire architecture shifts.
- **Furigana rendering in Compose:** Custom ruby text (furigana above base kanji) requires a custom Compose component — there is no built-in support. FuriganaTextView (community library) exists but maintenance status is uncertain. Budget time to build a custom `FuriganaText` Compose component if the library is inadequate.
- **Tesseract4Android as fallback:** Available via JitPack (MEDIUM confidence). Test integration before making it a committed part of Phase 1. If it adds excessive APK size or has integration issues, defer to Phase 3+ or drop it.
- **Android 15+ lock screen projection stop:** On Android 15 QPR1+, MediaProjection auto-stops on lock screen. Recovery flow (detect -> notify -> user re-grants) must be designed and tested. Behavior may differ on AYN Thor's Android version.

## Sources

### Primary (HIGH confidence)
- [Android Developers: MediaProjection](https://developer.android.com/media/grow/media-projection) — token lifecycle, foreground service types, Android 14+ consent requirements
- [Android Developers: Presentation API](https://developer.android.com/reference/android/app/Presentation) — Dialog inheritance, display lifecycle, TYPE_PRESENTATION behavior
- [Android Developers: Jetpack Compose BOM](https://developer.android.com/develop/ui/compose/bom) — BOM 2026.02.01 library versions
- [Android Developers: AGP 9.0.1 Release Notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) — built-in Kotlin support, Gradle 9.1.0+ requirement
- [Android Developers: Hilt](https://developer.android.com/training/dependency-injection/hilt-android) — version 2.57.1, KSP support
- [Android Developers: Room](https://developer.android.com/jetpack/androidx/releases/room) — 2.7.x, Kotlin codegen
- [Android Developers: ImageReader](https://developer.android.com/reference/android/media/ImageReader) — maxImages, acquireLatestImage, buffer exhaustion behavior
- [Android 14/15 Behavior Changes](https://developer.android.com/about/versions/14/behavior-changes-14) — MediaProjection single-use token enforcement
- [Google Developers: ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) — Japanese OCR, bundled model, 16.0.1
- [WaniKani API v2 Documentation](https://docs.api.wanikani.com/) — rate limits, pagination, assignments endpoint, updated_after filter
- [DeepL API Documentation](https://developers.deepl.com/docs) — REST v2, free tier limits, JA>EN quality
- [JetBrains: Ktor 3.4.0](https://blog.jetbrains.com/kotlin/2026/01/ktor-3-4-0-is-now-available/) — current Ktor release

### Secondary (MEDIUM confidence)
- [Kaku - Japanese OCR Dictionary](https://kaku.fuwafuwa.ca/) — feature baseline for Android Japanese OCR tools
- [YomiNinja - GitHub](https://github.com/matt-m-o/YomiNinja) — desktop competitor feature analysis
- [Game2Text](https://www.game2text.com/welcome/) — desktop competitor; Anki integration patterns
- [Translumo - GitHub](https://github.com/ramjke/Translumo) — multi-engine OCR scoring approach
- [GameSentenceMiner - GitHub](https://github.com/bpwhelan/GameSentenceMiner) — sentence mining community demand validation
- [Retro Game Corps: Dual-Screen Android Handheld Guide](https://retrogamecorps.com/2025/10/27/dual-screen-android-handheld-guide/) — AYN Thor display architecture (top=primary, bottom=TYPE_PRESENTATION)
- [Presentation API + Compose (Medium)](https://medium.com/@ibrahimethemsen/using-android-presentation-api-with-jetpack-compose-998adeae1130) — ComposeView lifecycle wiring pattern
- [CJK OCR challenges in 2026 (DEV Community)](https://dev.to/joe_wang_6a4a3e51566e8b52/why-ocr-for-cjk-languages-is-still-a-hard-problem-in-2026-and-how-im-tackling-it-5fge) — vertical text accuracy, preprocessing requirements
- [aallam/openai-kotlin GitHub](https://github.com/aallam/openai-kotlin) — OpenAI Kotlin client 4.0.1
- [Tesseract4Android GitHub](https://github.com/adaptech-cz/Tesseract4Android) — version 4.9.0, JitPack distribution

### Tertiary (LOW confidence)
- [xemantic/anthropic-sdk-kotlin GitHub](https://github.com/xemantic/anthropic-sdk-kotlin) — unofficial Claude Kotlin client; validate before use, or call REST directly via Ktor

---
*Research completed: 2026-03-02*
*Ready for roadmap: yes*
