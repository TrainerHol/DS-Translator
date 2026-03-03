# Architecture Research

**Domain:** Android dual-screen real-time translation system
**Researched:** 2026-03-02
**Confidence:** MEDIUM-HIGH (official Android APIs well-documented; dual-screen + OCR pipeline integration is niche with fewer reference implementations)

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          UI Layer                                       │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐      │
│  │  MainActivity     │  │  Presentation    │  │  OverlayWindow   │      │
│  │  (Control Panel)  │  │  Display (Bottom)│  │  (Floating UI)   │      │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘      │
│           │                     │                      │                │
├───────────┴─────────────────────┴──────────────────────┴────────────────┤
│                       ViewModel Layer                                    │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐      │
│  │ CaptureViewModel │  │TranslationVM     │  │  SettingsVM      │      │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘      │
│           │                     │                      │                │
├───────────┴─────────────────────┴──────────────────────┴────────────────┤
│                       Domain Layer (Use Cases)                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │CaptureUseCase│  │TranslateUse  │  │FuriganaUse   │                  │
│  │              │  │Case          │  │Case           │                  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                  │
│         │                 │                  │                          │
├─────────┴─────────────────┴──────────────────┴──────────────────────────┤
│                       Data / Service Layer                               │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐          │
│  │CaptureSvc  │ │ OCR Engine │ │Translation │ │ WaniKani   │          │
│  │(Foreground)│ │ (ML Kit /  │ │ Engine     │ │ Repository │          │
│  │            │ │  Cloud)    │ │ (DeepL/GPT)│ │            │          │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘          │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐                         │
│  │ TTS Engine │ │ Room DB    │ │ DataStore  │                         │
│  │            │ │ (Cache)    │ │ (Settings) │                         │
│  └────────────┘ └────────────┘ └────────────┘                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| **MainActivity** | App entry point, permission requests, capture consent, settings access, service lifecycle control | Single Activity with Compose UI; handles MediaProjection consent flow via `ActivityResultLauncher` |
| **PresentationDisplay** | Renders translation output on secondary screen (AYN Thor bottom display) | Extends `android.app.Presentation`; hosts a `ComposeView` for the translation list UI |
| **OverlayWindow** | Floating bubble + expandable panel for dual-screen games (DS/3DS) where bottom screen is game content | `WindowManager.addView()` with `TYPE_APPLICATION_OVERLAY`; managed by the foreground service |
| **CaptureService** | Long-running foreground service owning MediaProjection, ImageReader, VirtualDisplay lifecycle | `@AndroidEntryPoint` service with `foregroundServiceType="mediaProjection"` |
| **OCR Engine** | Text extraction from captured bitmaps; pluggable interface for different backends | Interface `OcrEngine` with `MlKitOcrEngine` default impl; Japanese text recognition library |
| **Translation Engine** | Japanese-to-English translation; pluggable backends | Interface `TranslationEngine` with DeepL, OpenAI, Claude, and local implementations |
| **WaniKani Repository** | Fetches user's kanji knowledge, caches locally, determines furigana visibility | Retrofit + Room cache; queries assignments endpoint filtered by SRS stage |
| **Furigana Processor** | Annotates Japanese text with readings; filters based on WaniKani knowledge level | Combines morphological analysis with WaniKani data to produce annotated text segments |
| **TTS Engine** | Japanese text-to-speech playback | Wraps `android.speech.tts.TextToSpeech` with `Locale.JAPAN` |
| **Room Database** | Caches WaniKani data, translation history, OCR results | Room with DAOs for kanji cache, translation cache, and session history |
| **DataStore** | User preferences and settings | Jetpack DataStore (Preferences) for API keys, capture interval, OCR engine selection |

## Recommended Project Structure

```
app/src/main/java/com/dstranslator/
├── di/                          # Hilt dependency injection modules
│   ├── AppModule.kt             # App-scoped singletons (Room, DataStore, Retrofit)
│   ├── ServiceModule.kt         # Service-scoped deps (MediaProjection helpers)
│   ├── OcrModule.kt             # OCR engine bindings
│   └── TranslationModule.kt    # Translation engine bindings
├── domain/                      # Pure Kotlin, no Android deps
│   ├── model/                   # Domain models
│   │   ├── RecognizedText.kt    # OCR output: text + bounding box + confidence
│   │   ├── TranslatedSegment.kt # Original + translation + furigana annotations
│   │   ├── KanjiKnowledge.kt    # WaniKani-derived kanji familiarity
│   │   └── CaptureRegion.kt     # User-defined dialog region bounds
│   ├── usecase/                 # Business logic orchestration
│   │   ├── CaptureAndRecognizeUseCase.kt
│   │   ├── TranslateTextUseCase.kt
│   │   ├── AnnotateFuriganaUseCase.kt
│   │   └── ProcessPipelineUseCase.kt  # Orchestrates full capture->display flow
│   └── repository/              # Repository interfaces
│       ├── OcrRepository.kt
│       ├── TranslationRepository.kt
│       ├── WaniKaniRepository.kt
│       └── SettingsRepository.kt
├── data/                        # Data layer implementations
│   ├── ocr/                     # OCR engine implementations
│   │   ├── OcrEngine.kt         # Interface: suspend fun recognize(bitmap, region?): RecognizedText
│   │   ├── MlKitOcrEngine.kt    # On-device ML Kit Japanese text recognition
│   │   └── CloudOcrEngine.kt    # Cloud Vision API fallback
│   ├── translation/             # Translation engine implementations
│   │   ├── TranslationEngine.kt # Interface: suspend fun translate(text): String
│   │   ├── DeepLEngine.kt       # DeepL API via Retrofit
│   │   ├── OpenAiEngine.kt      # GPT API for context-aware translation
│   │   ├── ClaudeEngine.kt      # Claude API for nuanced translation
│   │   └── LocalEngine.kt       # On-device fallback (ML Kit Translate)
│   ├── wanikani/                # WaniKani API integration
│   │   ├── WaniKaniApi.kt       # Retrofit interface for WaniKani v2 API
│   │   ├── WaniKaniRepositoryImpl.kt
│   │   └── model/               # API response DTOs
│   ├── local/                   # Local storage
│   │   ├── AppDatabase.kt       # Room database definition
│   │   ├── dao/                 # Room DAOs
│   │   │   ├── KanjiDao.kt
│   │   │   ├── TranslationCacheDao.kt
│   │   │   └── SessionHistoryDao.kt
│   │   └── entity/              # Room entities
│   └── settings/                # DataStore preferences
│       └── SettingsRepositoryImpl.kt
├── capture/                     # Screen capture subsystem
│   ├── CaptureService.kt        # Foreground service managing MediaProjection
│   ├── ScreenCaptureManager.kt  # ImageReader + VirtualDisplay lifecycle
│   ├── RegionCropper.kt         # Crops captured bitmap to dialog region
│   └── ChangeDetector.kt        # Compares frames to detect new text
├── processing/                  # OCR + Translation pipeline
│   ├── TranslationPipeline.kt   # Orchestrates: capture -> crop -> OCR -> translate -> annotate
│   ├── TextDiffEngine.kt        # Detects new/changed text vs previous OCR result
│   └── BatchProcessor.kt        # Handles multiple text regions efficiently
├── ui/                          # Compose UI
│   ├── main/                    # Primary screen (control panel)
│   │   ├── MainScreen.kt
│   │   ├── SettingsScreen.kt
│   │   └── RegionSelectorOverlay.kt  # Draw dialog region on game screen
│   ├── presentation/            # Secondary display (translation output)
│   │   ├── TranslationPresentation.kt  # Extends Presentation, hosts ComposeView
│   │   ├── TranslationListScreen.kt    # Scrollable translated text list
│   │   └── FuriganaText.kt             # Custom Compose component for ruby text
│   ├── overlay/                 # SYSTEM_ALERT_WINDOW overlay
│   │   ├── OverlayManager.kt    # WindowManager add/remove overlay views
│   │   ├── FloatingBubble.kt    # Draggable bubble trigger
│   │   └── OverlayPanel.kt      # Expandable translation panel
│   ├── viewmodel/               # ViewModels
│   │   ├── MainViewModel.kt
│   │   ├── TranslationViewModel.kt
│   │   ├── CaptureViewModel.kt
│   │   └── SettingsViewModel.kt
│   └── theme/                   # Material3 theming
└── util/                        # Cross-cutting utilities
    ├── BitmapUtils.kt           # Image manipulation helpers
    └── CoroutineDispatchers.kt  # Injectable dispatchers for testing
```

### Structure Rationale

- **`domain/`:** Pure Kotlin with no Android dependencies. Use cases orchestrate business logic and are easily unit-testable. Repository interfaces defined here ensure the data layer can be swapped without touching business logic.
- **`data/`:** All external integrations (APIs, Room, DataStore) live here behind repository interfaces. Each OCR and translation engine is a separate implementation of a shared interface, enabling the pluggable architecture.
- **`capture/`:** Separated from `data/` because screen capture is a complex Android-specific subsystem with its own lifecycle (foreground service, VirtualDisplay, ImageReader). This is the most Android-coupled code in the app.
- **`processing/`:** The pipeline that connects capture output to OCR to translation. Separated because it orchestrates across multiple data layer components and contains non-trivial logic (text diffing, batching, throttling).
- **`ui/presentation/`:** The secondary display UI is structurally different from the main UI -- it extends `Presentation` rather than being a normal Compose screen, so it warrants its own package.
- **`ui/overlay/`:** The overlay system is entirely `WindowManager`-based and lives outside the normal Activity/Compose lifecycle, so it needs clear separation.

## Architectural Patterns

### Pattern 1: Pluggable Engine Interface

**What:** Define abstract interfaces for OCR and translation backends, with concrete implementations swapped via Hilt.
**When to use:** Any component where the user selects between multiple backends (OCR engine, translation API).
**Trade-offs:** Adds a layer of indirection but is essential for this app -- game text varies wildly and no single OCR/translation engine works for all cases.

**Example:**
```kotlin
// domain/repository/OcrRepository.kt
interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap, region: Rect? = null): RecognizedText
    val engineName: String
    val isOnDevice: Boolean
}

// data/ocr/MlKitOcrEngine.kt
class MlKitOcrEngine @Inject constructor() : OcrEngine {
    private val recognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )

    override suspend fun recognize(bitmap: Bitmap, region: Rect?): RecognizedText {
        val cropped = region?.let { BitmapUtils.crop(bitmap, it) } ?: bitmap
        val input = InputImage.fromBitmap(cropped, 0)
        val result = recognizer.process(input).await()
        return result.toRecognizedText()
    }

    override val engineName = "ML Kit (On-Device)"
    override val isOnDevice = true
}

// di/OcrModule.kt
@Module
@InstallIn(SingletonComponent::class)
object OcrModule {
    @Provides
    fun provideOcrEngine(settings: SettingsRepository): OcrEngine {
        return when (settings.selectedOcrEngine) {
            OcrEngineType.ML_KIT -> MlKitOcrEngine()
            OcrEngineType.CLOUD_VISION -> CloudOcrEngine()
        }
    }
}
```

### Pattern 2: Service-to-UI Communication via SharedFlow/StateFlow

**What:** The foreground `CaptureService` emits pipeline results (captured frames, OCR text, translations) through `SharedFlow`/`StateFlow` held in a shared repository. ViewModels collect these flows. The service and UI never directly reference each other.
**When to use:** All communication between the long-running capture service and the UI layer (main screen, presentation display, overlay).
**Trade-offs:** More setup than a bound service with direct callbacks, but survives configuration changes, works with Compose `collectAsState()`, and maintains clean separation.

**Example:**
```kotlin
// Shared between Service and ViewModels via Hilt @Singleton
@Singleton
class TranslationPipelineState @Inject constructor() {
    // Latest pipeline output -- StateFlow for current state
    private val _translations = MutableStateFlow<List<TranslatedSegment>>(emptyList())
    val translations: StateFlow<List<TranslatedSegment>> = _translations.asStateFlow()

    // One-shot events (errors, new text detected) -- SharedFlow for events
    private val _events = MutableSharedFlow<PipelineEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PipelineEvent> = _events.asSharedFlow()

    // Pipeline status
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    suspend fun emitTranslation(segments: List<TranslatedSegment>) {
        _translations.value = segments
    }

    suspend fun emitEvent(event: PipelineEvent) {
        _events.emit(event)
    }
}
```

### Pattern 3: Coroutine-Based Capture Loop with Change Detection

**What:** The capture service runs a coroutine loop that acquires frames from ImageReader, compares them to the previous frame (or a hash of the dialog region), and only triggers OCR when meaningful change is detected. This avoids burning CPU/battery on identical frames.
**When to use:** Continuous capture mode (the default operating mode).
**Trade-offs:** Change detection adds complexity but is critical for battery life and API cost control. A naive approach (OCR every frame) would be unusable.

**Example:**
```kotlin
// capture/ScreenCaptureManager.kt
class ScreenCaptureManager @Inject constructor(
    private val changeDetector: ChangeDetector,
    private val dispatchers: CoroutineDispatchers
) {
    private var lastFrameHash: Long = 0

    fun startCaptureLoop(
        imageReader: ImageReader,
        region: Rect?,
        intervalMs: Long = 500
    ): Flow<Bitmap> = flow {
        while (currentCoroutineContext().isActive) {
            val image = imageReader.acquireLatestImage()
            if (image != null) {
                val bitmap = image.toBitmap()
                image.close() // CRITICAL: always close to avoid stalling ImageReader

                val croppedRegion = region?.let { BitmapUtils.crop(bitmap, it) } ?: bitmap
                val frameHash = changeDetector.computeHash(croppedRegion)

                if (frameHash != lastFrameHash) {
                    lastFrameHash = frameHash
                    emit(croppedRegion)
                }

                if (bitmap !== croppedRegion) bitmap.recycle()
            }
            delay(intervalMs)
        }
    }.flowOn(dispatchers.io)
}
```

### Pattern 4: Presentation Display with ComposeView

**What:** The `Presentation` subclass hosts a `ComposeView` to render Jetpack Compose UI on the secondary display. It receives data via the same `StateFlow` that feeds the main UI, keeping both screens in sync.
**When to use:** Rendering the translation list on the AYN Thor bottom screen.
**Trade-offs:** `Presentation` is an older API (predates Compose), so bridging requires manual `ComposeView` setup with lifecycle owner wiring. This is a known pattern but requires care with lifecycle management.

**Example:**
```kotlin
// ui/presentation/TranslationPresentation.kt
class TranslationPresentation(
    context: Context,
    display: Display,
    private val pipelineState: TranslationPipelineState
) : Presentation(context, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composeView = ComposeView(context).apply {
            setContent {
                val translations by pipelineState.translations.collectAsState()
                DSTranslatorTheme {
                    TranslationListScreen(
                        segments = translations,
                        onPlayAudio = { /* TTS trigger */ },
                        onSegmentTap = { /* show details */ }
                    )
                }
            }
        }
        setContentView(composeView)
    }
}
```

**Lifecycle note:** The `Presentation` is owned by the `CaptureService` (not an Activity), because the service outlives activity configuration changes. The service creates the Presentation when it detects a secondary display via `DisplayManager`, and dismisses it when the display is removed or service stops.

## Data Flow

### Primary Pipeline: Capture -> OCR -> Translate -> Display

```
┌─────────────────┐
│  CaptureService  │  (Foreground Service, owns MediaProjection)
│  ┌─────────────┐ │
│  │ ImageReader  │─┤──> acquireLatestImage() every ~500ms
│  └─────────────┘ │
└────────┬─────────┘
         │ Bitmap
         v
┌─────────────────┐
│ ChangeDetector   │  Perceptual hash comparison
│                  │  Skip if frame unchanged
└────────┬─────────┘
         │ Changed Bitmap (dialog region cropped)
         v
┌─────────────────┐
│ OCR Engine       │  ML Kit Japanese TextRecognizer (on-device)
│ (pluggable)      │  Returns: text blocks with bounding boxes + confidence
└────────┬─────────┘
         │ RecognizedText
         v
┌─────────────────┐
│ TextDiffEngine   │  Compare with previous OCR result
│                  │  Only translate NEW or CHANGED text
└────────┬─────────┘
         │ New text segments
         v
┌─────────────────┐
│ Translation      │  DeepL / GPT / Claude / Local
│ Engine           │  Returns: English translation
│ (pluggable)      │
└────────┬─────────┘
         │ TranslatedSegment (jp + en)
         v
┌─────────────────┐
│ Furigana         │  Cross-reference kanji with WaniKani cache
│ Processor        │  Add readings above unknown kanji only
└────────┬─────────┘
         │ AnnotatedSegment (jp + en + furigana)
         v
┌─────────────────┐
│ PipelineState    │  @Singleton SharedFlow/StateFlow
│ (shared)         │  Emits to all collectors
└──┬─────────┬─────┘
   │         │
   v         v
┌──────┐  ┌─────────────┐
│Pres. │  │OverlayPanel │  (when in overlay mode)
│Display│  │             │
└──────┘  └─────────────┘
```

### WaniKani Data Flow

```
App Launch / Periodic Refresh
         │
         v
┌─────────────────┐
│ WaniKani API     │  GET /v2/assignments?srs_stages=1,2,3,4,5,6,7,8,9
│ (Retrofit)       │  GET /v2/subjects?types=kanji
└────────┬─────────┘
         │ Rate limit: 60 req/min
         v
┌─────────────────┐
│ Room Database    │  Cache assignments + subjects locally
│ (kanji_cache)    │  Invalidate after 24h or manual refresh
└────────┬─────────┘
         │
         v
┌─────────────────┐
│ KanjiKnowledge   │  In-memory lookup: Set<Char> of known kanji
│ Index             │  Built from cached assignments where srs_stage >= threshold
└─────────────────┘
```

### Display Mode Decision Flow

```
App Start
    │
    v
┌──────────────────┐
│ DisplayManager    │  Check for secondary display
│ .getDisplays()    │
└──────┬───────────┘
       │
       ├─── Secondary display found ──> Launch Presentation on secondary
       │                                 (Translation list UI)
       │
       └─── No secondary display ──> Prompt user:
                                      "Use overlay mode?"
                                      └──> Launch floating bubble overlay
```

### Key Data Flows

1. **Continuous capture loop:** CaptureService runs a coroutine that polls ImageReader at configurable intervals (default 500ms). Each frame is hashed and compared to the previous frame's hash. Only changed frames enter the OCR pipeline. This is the hot path and must stay responsive.

2. **Translation pipeline:** Runs on `Dispatchers.IO`. OCR is CPU-bound (ML Kit on-device) while translation is network-bound (API call). These can overlap: while frame N is being translated, frame N+1 can be OCR'd. Use a `Channel` with `CONFLATED` capacity to drop stale frames if the pipeline falls behind.

3. **Furigana annotation:** Happens after translation, before display. Needs morphological segmentation of the Japanese text (to identify individual kanji within compound words). The WaniKani knowledge set is queried in-memory (pre-built from Room cache) so this step is fast.

4. **TTS playback:** User-triggered (tap a sentence on the bottom screen). Uses Android's built-in `TextToSpeech` engine with `Locale.JAPAN`. Queue mode `QUEUE_FLUSH` for immediate playback of tapped sentence.

5. **Settings changes:** Flow from DataStore through `SettingsRepository` as a `Flow<Settings>`. When the user changes OCR engine or translation backend, the pipeline reconstructs the relevant engine. Service does not restart -- only the engine instance swaps.

## Scaling Considerations

This is a single-user on-device app, so traditional server scaling does not apply. The relevant "scaling" concerns are computational and battery related.

| Concern | Light Use (manual capture) | Medium Use (auto-capture, 1fps) | Heavy Use (auto-capture, 2fps + overlay) |
|---------|---------------------------|--------------------------------|------------------------------------------|
| **CPU** | Negligible -- OCR on tap only | ML Kit ~100-200ms per frame; manageable | May need to drop to 1fps; throttle when battery low |
| **Memory** | ~50-80MB (app + ML Kit model) | ~80-120MB (frame buffers + model) | ~120-180MB (overlay views + multiple buffers) |
| **Battery** | Minimal impact | Moderate -- foreground service + OCR | Significant -- consider auto-pause when idle |
| **API costs** | Pennies per session | ~$0.01-0.10/session (DeepL) | Text diffing essential to avoid redundant calls |
| **Network** | Burst on translate | Steady low-bandwidth | Same -- text payloads are tiny |

### Scaling Priorities

1. **First bottleneck: OCR latency on stylized game text.** ML Kit's Japanese model is slower than Latin. For pixel-art fonts or stylized text, accuracy drops. Mitigation: pre-process bitmaps (upscale, sharpen, binarize) before OCR. Allow cloud fallback for difficult text.

2. **Second bottleneck: Translation API latency.** Network round-trip adds 200-800ms per call. Mitigation: aggressive translation caching in Room (same Japanese text = same translation). Batch adjacent text segments into a single API call where possible.

3. **Third bottleneck: Memory pressure from ImageReader.** Failing to close Images from `acquireLatestImage()` stalls the ImageReader pipeline. Must close every Image promptly -- this is the most common source of bugs in MediaProjection apps.

## Anti-Patterns

### Anti-Pattern 1: Capturing Every Frame

**What people do:** Hook into every VSYNC callback from ImageReader and run OCR on every frame (60fps).
**Why it's wrong:** OCR takes 100-300ms per frame. At 60fps you get a 17ms budget. The pipeline falls hopelessly behind, ImageReader stalls, memory explodes, and the device heats up.
**Do this instead:** Use `acquireLatestImage()` (not `acquireNextImage()`) on a fixed interval (500ms-2000ms). `acquireLatestImage()` discards all older buffered frames, giving you only the most recent one. Combine with perceptual hashing to skip unchanged frames entirely.

### Anti-Pattern 2: Putting OCR/Translation on the Main Thread

**What people do:** Call ML Kit's `process()` or network translation APIs synchronously or on `Dispatchers.Main`.
**Why it's wrong:** ANR (Application Not Responding) within seconds. ML Kit Japanese OCR can take 200ms+ and translation APIs have network latency.
**Do this instead:** All pipeline work runs on `Dispatchers.IO` or `Dispatchers.Default`. The only main-thread work should be collecting StateFlow in Compose UI. Use `withContext(Dispatchers.IO)` for all OCR and network calls.

### Anti-Pattern 3: Activity-Owned MediaProjection

**What people do:** Hold the `MediaProjection` token in the Activity. When the user rotates the device or the Activity is recreated, the projection dies.
**Why it's wrong:** MediaProjection tokens are single-use (Android 14+). Once the Activity recreates, you must re-prompt the user for consent. During gameplay this is extremely disruptive.
**Do this instead:** Activity obtains consent, immediately passes the result intent to a foreground service. The service creates and owns the `MediaProjection`, `VirtualDisplay`, and `ImageReader`. The service survives Activity lifecycle changes. The Activity communicates with the service through shared Hilt singletons (flows/state).

### Anti-Pattern 4: Translating Unchanged Text Repeatedly

**What people do:** Every OCR result gets sent to the translation API, even if the text is identical to the previous frame.
**Why it's wrong:** Wastes API quota, costs money, adds unnecessary latency, and produces duplicate entries in the UI.
**Do this instead:** Implement a two-tier diff system: (1) frame-level hash comparison before OCR to skip identical frames, and (2) text-level comparison after OCR to skip translation of unchanged text segments. Cache translations in Room keyed by the Japanese source text.

### Anti-Pattern 5: Ignoring Presentation Lifecycle

**What people do:** Create a Presentation and forget about it, or tie it to the Activity lifecycle.
**Why it's wrong:** Presentation is auto-dismissed when its Display is removed. If tied to Activity, it dies on configuration change. If not properly dismissed, it leaks the window.
**Do this instead:** The foreground service owns the Presentation. Register a `DisplayManager.DisplayListener` to detect display addition/removal. Create Presentation on display connect, dismiss on disconnect. The service's `onDestroy()` always dismisses any active Presentation.

### Anti-Pattern 6: Monolithic Pipeline Without Error Boundaries

**What people do:** Chain capture -> OCR -> translate -> display as one monolithic coroutine. If OCR fails, the entire pipeline crashes.
**Why it's wrong:** Individual steps fail for different reasons (OCR: blurry image; translation: network timeout; furigana: unknown word). One failure should not kill the whole pipeline.
**Do this instead:** Each pipeline step returns a `Result<T>` or catches exceptions independently. If OCR fails, show a "could not read text" message. If translation fails, show the Japanese text with a retry button. If furigana fails, show text without furigana. Graceful degradation at every stage.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| **ML Kit Text Recognition** | On-device; `com.google.mlkit:text-recognition-japanese` dependency; `TextRecognition.getClient()` | Japanese model downloaded on first use (~16MB). Must handle model download failure gracefully. |
| **DeepL API** | REST via Retrofit; `POST https://api-free.deepl.com/v2/translate` | 128KB max request body. Auth via `DeepL-Auth-Key` header. Free tier: 500,000 chars/month. |
| **OpenAI / GPT API** | REST via Retrofit; `POST https://api.openai.com/v1/chat/completions` | Use system prompt for translation context. Can provide game-specific terminology hints. |
| **Claude API** | REST via Retrofit; `POST https://api.anthropic.com/v1/messages` | Strong at nuanced/contextual translation. Use for difficult passages. |
| **WaniKani API v2** | REST via Retrofit; `GET https://api.wanikani.com/v2/assignments` and `/subjects` | Bearer token auth. 60 req/min rate limit. Cache aggressively in Room -- data changes slowly (once per review session). |
| **Android TTS** | On-device; `android.speech.tts.TextToSpeech` | Check `isLanguageAvailable(Locale.JAPAN)` on init. Quality varies by device/TTS engine installed. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| **Activity <-> CaptureService** | Shared `@Singleton` StateFlow/SharedFlow via Hilt. Activity starts/stops service via Intent. | No direct binding -- flows provide loose coupling. Service intent carries MediaProjection consent result. |
| **CaptureService <-> PresentationDisplay** | Service creates/owns Presentation. Presentation collects from shared StateFlow. | Service detects display via DisplayManager. Presentation's ComposeView collects translations StateFlow. |
| **CaptureService <-> OverlayWindow** | Service manages overlay via WindowManager. Overlay collects from shared StateFlow. | Service adds/removes overlay views. Overlay click events emit to shared SharedFlow consumed by service. |
| **OCR Engine <-> Translation Engine** | Connected through TranslationPipeline in processing layer. No direct coupling. | Pipeline orchestrates: OCR output feeds translation input. Engines never reference each other. |
| **WaniKani Repo <-> Furigana Processor** | Furigana processor queries KanjiKnowledge (in-memory Set built from WaniKani Room cache). | Decoupled by the KanjiKnowledge domain model. Furigana works without WaniKani (shows all furigana). |
| **DataStore <-> Pipeline** | Settings changes flow via `Flow<Settings>` to pipeline, which swaps engine instances. | Non-destructive hot-swap -- pipeline drains current work, then uses new engine for next frame. |

## Build Order (Dependency Chain)

The following ordering reflects technical dependencies -- each phase builds on what came before.

1. **Foreground Service + MediaProjection Capture** -- Everything depends on being able to capture the screen. This is the foundation and involves the most complex Android API interactions (permissions, foreground service type, VirtualDisplay, ImageReader lifecycle). Build this first and verify with a simple "save screenshot" test.

2. **OCR Integration (ML Kit)** -- Once you can capture frames, pipe them to ML Kit for text recognition. This validates the capture-to-text pipeline before adding translation complexity. Test with static screenshots first, then live capture.

3. **Translation Engine (DeepL first)** -- With OCR outputting Japanese text, add one translation backend. DeepL first because it has the simplest API and best Japanese translation quality. This completes the core pipeline: capture -> OCR -> translate.

4. **Presentation Display (Secondary Screen)** -- With translated text available, render it on the AYN Thor bottom screen. This is the primary differentiator of the app. Requires DisplayManager detection + Presentation + ComposeView setup.

5. **Pipeline Optimization (Change Detection, Caching, Text Diffing)** -- The naive pipeline from steps 1-4 will work but waste resources. Add frame hashing, text diffing, and translation caching to make continuous capture mode viable.

6. **WaniKani Integration + Furigana** -- Adds the learning dimension. Requires Room database for caching, Retrofit for API calls, and a furigana rendering component (custom Compose text or FuriganaTextView wrapper).

7. **TTS Engine** -- Straightforward Android API wrapping. Low dependency -- just needs the translated/recognized text available.

8. **Overlay Mode** -- The fallback for dual-screen games. Requires SYSTEM_ALERT_WINDOW permission, WindowManager overlay management. Most complex UI component but least-used mode (only for DS/3DS games using both screens).

9. **Additional Translation Backends** -- GPT, Claude, local. Each follows the same interface pattern established in step 3. Can be added independently.

10. **Settings, Polish, Dialog Region Presets** -- Quality-of-life features that refine the core experience.

## Sources

- [Android MediaProjection Official Guide](https://developer.android.com/media/grow/media-projection) -- HIGH confidence, official docs updated Jan 2025
- [Android Presentation API Reference](https://developer.android.com/reference/kotlin/android/app/Presentation) -- HIGH confidence, official API reference
- [ML Kit Text Recognition v2 for Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) -- HIGH confidence, official Google docs
- [ML Kit Supported Languages (Japanese)](https://developers.google.com/ml-kit/vision/text-recognition/v2/languages) -- HIGH confidence
- [Android SYSTEM_ALERT_WINDOW Changes](https://developer.android.com/develop/background-work/services/fgs/changes) -- HIGH confidence, official docs on Android 15 restrictions
- [WaniKani API v2 Documentation](https://docs.api.wanikani.com/) -- HIGH confidence, official API docs
- [Android TextToSpeech API Reference](https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech) -- HIGH confidence
- [DeepL API Documentation](https://developers.deepl.com/docs) -- HIGH confidence, official docs
- [Android StateFlow/SharedFlow Guide](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow) -- HIGH confidence, official Android guide
- [Android Room Persistence](https://developer.android.com/training/data-storage/room) -- HIGH confidence, official docs
- [Android Hilt Dependency Injection](https://developer.android.com/training/dependency-injection/hilt-android) -- HIGH confidence, official docs
- [FuriganaTextView](https://github.com/fkt3/FuriganaTextView) -- MEDIUM confidence, community library
- [deepl-jvm Kotlin Library](https://github.com/seratch/deepl-jvm) -- MEDIUM confidence, community library
- [Android Connected Displays Guide](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-connected-displays) -- HIGH confidence, official docs
- [Android ImageReader API Reference](https://developer.android.com/reference/android/media/ImageReader) -- HIGH confidence, official docs

---
*Architecture research for: Android dual-screen real-time translation system*
*Researched: 2026-03-02*
