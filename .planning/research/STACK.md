# Stack Research

**Domain:** Native Android dual-screen Japanese game translator
**Researched:** 2026-03-02
**Confidence:** HIGH

## Recommended Stack

### Core Platform

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| Kotlin | 2.3.10 | Primary language | Latest stable (Feb 2026). AGP 9.0 has built-in Kotlin support -- no separate plugin needed. Coroutines-first, null-safe, and the only language Google actively supports for Android. | HIGH |
| Android Gradle Plugin (AGP) | 9.0.1 | Build system | Current stable (Jan 2026). Built-in Kotlin support, targets API 36.1. Requires Gradle 9.1.0+ and JDK 17+. | HIGH |
| Gradle | 9.1.x | Build orchestration | Minimum required by AGP 9.0.1. Use Kotlin DSL (`build.gradle.kts`) exclusively -- Groovy DSL is legacy. | HIGH |
| compileSdk / targetSdk | 36 | API level targeting | Latest supported by AGP 9.0. Required for foreground service type enforcement on Android 14+. | HIGH |
| minSdk | 28 (Android 9) | Minimum device support | AYN Thor runs Android 11+. Setting minSdk 28 ensures modern API access (WindowMetrics, Presentation API improvements) while excluding ancient devices. | MEDIUM |

### UI Framework

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| Jetpack Compose | BOM 2026.02.01 | Declarative UI | Current stable BOM. Contains Compose 1.10 and Material 3 v1.4. Declarative UI fits the reactive translation pipeline -- state changes in OCR/translation automatically reflect in UI. | HIGH |
| Material 3 (M3) | 1.4.x (via BOM) | Design system | Included in Compose BOM. Provides theming, components, adaptive layouts. Use M3 for the translation list, settings screens, and overlay panels. | HIGH |
| Compose Navigation | 2.9.x | Screen navigation | Type-safe navigation with Kotlin Serialization. Single-Activity architecture is the 2026 standard. Use for Settings, Main, and any configuration screens on the primary display. | MEDIUM |

### Architecture & DI

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| Hilt (Dagger) | 2.57.1 | Dependency injection | Google-recommended DI for Android. Compile-time correctness, strong Android Studio support, and seamless ViewModel injection. Use KSP (not KAPT) for annotation processing. | HIGH |
| AndroidX Hilt | 1.2.0 | Hilt + Jetpack integration | Provides `@HiltViewModel`, WorkManager injection, and Compose integration. | HIGH |
| AndroidX Lifecycle + ViewModel | 2.10.0 | State management | Stable release. Use `ViewModel` + `StateFlow` for all screen state. `viewModelScope` for coroutine lifecycle. | HIGH |
| KSP | 2.3.10-1.0.x | Symbol processing | Replaces KAPT for Hilt, Room. 2x+ faster build times. Required version must match Kotlin version prefix. | HIGH |

### Screen Capture & Display

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| MediaProjection API | Platform API (Android 5+) | Screen capture | Only official way to capture screen content on Android. Requires foreground service with `FOREGROUND_SERVICE_MEDIA_PROJECTION` type on Android 14+. Each session needs fresh user consent. | HIGH |
| Presentation API | Platform API (Android 4.2+) | Secondary display | The correct API for TYPE_PRESENTATION displays like AYN Thor's second screen. Use with ComposeView by manually wiring ViewTreeLifecycleOwner, ViewTreeViewModelStoreOwner, and ViewTreeSavedStateRegistryOwner. | HIGH |
| DisplayManager | Platform API | Display detection | Use `DisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)` to discover the secondary screen. Register `DisplayListener` for hot-plug events. | HIGH |
| WindowManager | Platform API | Screen metrics | Use `WindowManager.getMaximumWindowMetrics()` for capture dimensions. Avoid deprecated `Display.getRealMetrics()`. | HIGH |
| SYSTEM_ALERT_WINDOW | Platform permission | Overlay mode | Required for floating bubble overlay on game screen (DS/3DS dual-screen fallback). On Android 15+, foreground services can only start when overlay is visible. Use `TYPE_APPLICATION_OVERLAY` window type. | HIGH |

### OCR (Pluggable)

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| ML Kit Text Recognition v2 (Japanese) | 16.0.1 | Default OCR engine | Google's on-device Japanese OCR. ~140ms average latency, supports horizontal and vertical Japanese text. Bundled model adds ~4MB, unbundled ~260KB + download. Use bundled for offline reliability. | HIGH |
| Tesseract4Android | 4.9.0 | Fallback OCR engine | Open-source alternative for when ML Kit fails on stylized game fonts. Supports custom training data for specific font styles. ~220ms latency on clean text, slower on complex layouts. Use OpenMP variant for multi-core performance. Available via JitPack. | MEDIUM |

### Translation APIs (Pluggable)

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| Ktor Client | 3.4.0 | HTTP client | Kotlin-native, coroutine-first. Use OkHttp engine for Android. Handles all translation API calls, WaniKani API, and cloud OCR requests through a single HTTP stack. | HIGH |
| Ktor Content Negotiation + kotlinx.serialization | 1.9.0 | JSON serialization | Kotlin-native JSON. Compile-time safe, no reflection. Use for all API request/response models. Pairs naturally with Ktor. | HIGH |
| DeepL API | REST v2 | Primary translation | Best Japanese-to-English quality among dedicated translation APIs. Free tier: 500K chars/month. Call via Ktor directly -- the `deepl-jvm` community library (0.1.4) is too outdated and thin to justify a dependency. | MEDIUM |
| OpenAI API | REST | LLM translation | Use `aallam/openai-kotlin` 4.0.1 -- mature multiplatform Kotlin client with coroutine support. GPT-4o provides context-aware translation with gaming/cultural nuance. | MEDIUM |
| Anthropic Claude API | REST | LLM translation | No official Kotlin SDK. Use `xemantic/anthropic-sdk-kotlin` (unofficial multiplatform) or call REST directly via Ktor. Claude excels at nuanced Japanese literary/game translation. | LOW |

### WaniKani Integration

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| WaniKani API v2 | Revision 20170710 | Kanji knowledge | REST API at `api.wanikani.com/v2/`. Bearer token auth. 60 req/min rate limit. Fetch `/subjects` for kanji data and `/assignments` for user's SRS progress to determine known vs unknown kanji. | HIGH |

### Text-to-Speech

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| Android TextToSpeech | Platform API | Japanese TTS | Built-in Android TTS with Japanese support via Google TTS engine (pre-installed on most devices). Zero additional dependencies. Quality is adequate for sentence-level pronunciation. Initialize with `Locale.JAPANESE` and check `isLanguageAvailable()`. | HIGH |

### Local Storage

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| Room | 2.7.x (stable) | Structured data | Translation history, cached WaniKani subjects, dialog region presets. Use KSP for code generation (enable `room.generateKotlin`). Kotlin coroutine Flow return types for reactive queries. | HIGH |
| DataStore Preferences | 1.2.0 | Key-value settings | API keys, capture interval, TTS voice preference, OCR engine selection. Replaces SharedPreferences with coroutine-safe, type-checked access. | HIGH |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Coil | 3.4.0 | Image loading/caching | Cache and display captured screen regions for OCR review. Use `coil-compose` for Compose integration and `coil-network-okhttp` for network layer. |
| Timber | 5.0.1 | Logging | Replace all `Log.*` calls. Auto-tags with class name. Disable in release builds with no-op tree. |
| kotlinx.coroutines | 1.10.2 | Async programming | Foundation for all async work: screen capture loop, OCR processing, API calls, UI state management. Use `StateFlow` for UI state, `Flow` for data streams. |
| WorkManager | 2.11.0 | Background scheduling | Optional: periodic WaniKani data sync when app is not running. Not needed for core translation loop (that runs in foreground service). |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Android Studio Ladybug+ | IDE | Latest stable with AGP 9.0 support, Compose preview, and layout inspector for dual-display debugging. |
| LeakCanary | Memory leak detection | Critical for long-running foreground service + MediaProjection sessions. Catches Surface/VirtualDisplay leaks. |
| KtLint / Detekt | Code quality | KtLint for formatting, Detekt for static analysis. Enforce via pre-commit hooks. |
| Compose Preview | UI development | Use `@Preview` annotations for translation list items, overlay bubbles, and settings screens. Cannot preview Presentation display content directly. |

## Version Compatibility Matrix

| Package | Requires | Notes |
|---------|----------|-------|
| AGP 9.0.1 | Gradle 9.1.0+, JDK 17+ | Built-in Kotlin support (no separate plugin) |
| Kotlin 2.3.10 | AGP 9.0+ | Version aligned with AGP 9.0 requirements |
| KSP | Must match Kotlin version prefix (2.3.10-1.0.x) | Check Maven Central for exact matching version |
| Hilt 2.57.1 | KSP (not KAPT) | KAPT is deprecated, KSP is 2x faster |
| Room 2.7.x | KSP, `room.generateKotlin=true` | Enable Kotlin codegen for idiomatic output |
| Compose BOM 2026.02.01 | Kotlin 2.3.x | BOM manages all Compose library versions |
| Ktor 3.4.0 | kotlinx.coroutines 1.10.x | OkHttp 5.1.0 engine for Android |
| ML Kit Japanese 16.0.1 | Google Play Services | Bundled variant requires no Play Services download |
| Tesseract4Android 4.9.0 | JitPack repository | Available in standard and OpenMP variants |

## Gradle Dependencies

```kotlin
// build.gradle.kts (project-level)
plugins {
    alias(libs.plugins.android.application) version "9.0.1" apply false
    alias(libs.plugins.google.dagger.hilt) version "2.57.1" apply false
    alias(libs.plugins.google.devtools.ksp) version "2.3.10-1.0.x" apply false
    alias(libs.plugins.kotlin.serialization) version "2.3.10" apply false
}

// build.gradle.kts (app-level)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.dagger.hilt)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 36
    defaultConfig {
        minSdk = 28
        targetSdk = 36
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Architecture
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")

    // DI
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-compiler:2.57.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // OCR
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("cz.adaptech.tesseract4android:tesseract4android-openmp:4.9.0")

    // Networking
    implementation("io.ktor:ktor-client-core:3.4.0")
    implementation("io.ktor:ktor-client-okhttp:3.4.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // OpenAI (optional translation backend)
    implementation("com.aallam.openai:openai-client:4.0.1")

    // Storage
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // Image
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")

    // Background
    implementation("androidx.work:work-runtime-ktx:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
}
```

## Alternatives Considered

| Category | Recommended | Alternative | Why Not Alternative |
|----------|-------------|-------------|---------------------|
| Language | Kotlin | Java | Google has made Kotlin the primary Android language. Java lacks coroutines, null safety, and modern Android API support is Kotlin-first. |
| UI | Jetpack Compose | XML Views | Compose is the modern standard. Reactive state management fits the real-time translation pipeline. XML Views require more boilerplate and lack first-class StateFlow integration. |
| DI | Hilt | Koin | Hilt provides compile-time safety and is Google's official recommendation. Koin is reflection-based (slower startup) and lacks Android Studio integration for navigation/inspection. |
| HTTP | Ktor Client | Retrofit + OkHttp | Ktor is Kotlin-native, coroutine-first, and avoids annotation processing overhead. For this project's simple REST API calls (translation, WaniKani), Ktor's programmatic API is cleaner than Retrofit's interface declarations. |
| JSON | kotlinx.serialization | Moshi / Gson | kotlinx.serialization is compile-time, no reflection, multiplatform-ready, and pairs natively with Ktor. Moshi is close but adds another annotation processor. Gson is reflection-based and effectively unmaintained. |
| OCR | ML Kit + Tesseract | Google Cloud Vision API | Cloud Vision requires network and costs money per request. ML Kit runs on-device with ~140ms latency. Tesseract provides open-source fallback for offline/custom scenarios. Cloud Vision is a valid *cloud option* but not the default. |
| Navigation | Compose Navigation | Compose Destinations | Official library with type-safe routes is now sufficient. Third-party Compose Destinations added value when official nav was string-based, but that gap is closed. |
| Image Loading | Coil | Glide | Coil is Kotlin-first, coroutine-native, lighter weight. Glide's Java-centric API and larger footprint are unnecessary here. |
| Preferences | DataStore | SharedPreferences | SharedPreferences is not coroutine-safe, not type-safe, and blocks the main thread. DataStore is its direct replacement. |
| Database | Room | SQLDelight | Room is Google's official Android ORM, deeply integrated with Lifecycle/ViewModel/Flow. SQLDelight is excellent for KMP but unnecessary complexity for Android-only projects. |
| Logging | Timber | Android Log | Timber auto-tags, supports planting/removing trees (disable in release), and has a cleaner API. No reason to use raw Log. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| KAPT (Kotlin Annotation Processing) | Deprecated, 2x slower than KSP, blocks incremental compilation | KSP for all annotation processing (Hilt, Room) |
| Gson | Reflection-based, slow, effectively unmaintained since 2022 | kotlinx.serialization |
| SharedPreferences | Not thread-safe, blocks main thread, no type safety | DataStore Preferences |
| LiveData | Legacy reactive type, poor Compose integration, limited operators | StateFlow / Flow |
| AsyncTask | Deprecated since API 30, memory leak prone | Kotlin Coroutines |
| Groovy Gradle DSL | Legacy, no IDE autocompletion, harder to maintain | Kotlin DSL (build.gradle.kts) |
| View Binding / Data Binding | XML-era tools, unnecessary with Compose | Jetpack Compose |
| Volley | Outdated HTTP library, no coroutine support | Ktor Client |
| RxJava | Steep learning curve, large dependency, replaced by Flow in Android ecosystem | Kotlin Flow + Coroutines |
| Firebase ML Kit | Legacy namespace, migrated to standalone ML Kit | google.mlkit (standalone) |
| tess-two | Abandoned predecessor to Tesseract4Android | Tesseract4Android 4.9.0 |

## Stack Patterns by Variant

**If building for offline-only use:**
- Use ML Kit bundled model (not unbundled) -- adds ~4MB but requires no Google Play Services download
- Use Tesseract4Android as primary OCR -- fully offline, can bundle trained data for game fonts
- Skip DeepL/OpenAI/Claude APIs -- implement local translation fallback only
- Increase Room caching aggressively for WaniKani data

**If prioritizing translation quality over speed:**
- Use OpenAI GPT-4o or Claude as primary translation backend -- provides context-aware, culturally nuanced translations
- Use DeepL as fast fallback when LLM latency is too high
- Implement request queuing with priority (new dialog text > background pre-translation)

**If the secondary display is unavailable (single-screen mode):**
- Fall back to SYSTEM_ALERT_WINDOW overlay mode
- Use floating bubble + expandable panel pattern
- Reduce translation display to compact card format
- On Android 15+, ensure overlay is visible before starting foreground service

## Manifest Requirements

```xml
<manifest>
    <!-- Screen capture -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />

    <!-- Overlay -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <!-- Network (translation APIs, WaniKani) -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application>
        <!-- Screen capture service -->
        <service
            android:name=".capture.ScreenCaptureService"
            android:foregroundServiceType="mediaProjection"
            android:exported="false" />
    </application>
</manifest>
```

## Sources

- [Android Developers: Media Projection](https://developer.android.com/media/grow/media-projection) -- MediaProjection API requirements, foreground service types, Android 14+ restrictions (HIGH confidence)
- [Android Developers: Presentation API Reference](https://developer.android.com/reference/android/app/Presentation) -- Secondary display API documentation (HIGH confidence)
- [Android Developers: Jetpack Compose BOM](https://developer.android.com/develop/ui/compose/bom) -- BOM 2026.02.01 verified (HIGH confidence)
- [Android Developers: AGP 9.0.1 Release Notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) -- AGP version, Kotlin built-in support (HIGH confidence)
- [Android Developers: Hilt](https://developer.android.com/training/dependency-injection/hilt-android) -- Hilt 2.57.1, KSP support (HIGH confidence)
- [Android Developers: Room Releases](https://developer.android.com/jetpack/androidx/releases/room) -- Room 2.7.x, KSP + Kotlin codegen (HIGH confidence)
- [Android Developers: DataStore Releases](https://developer.android.com/jetpack/androidx/releases/datastore) -- DataStore 1.2.0 (HIGH confidence)
- [Android Developers: Lifecycle Releases](https://developer.android.com/jetpack/androidx/releases/lifecycle) -- Lifecycle 2.10.0 stable (HIGH confidence)
- [Android Developers: SYSTEM_ALERT_WINDOW Restrictions](https://developer.android.com/about/versions/15/behavior-changes-15) -- Android 15 overlay + foreground service interaction (HIGH confidence)
- [Google Developers: ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) -- Japanese OCR, bundled/unbundled models (HIGH confidence)
- [Maven Central: ML Kit Japanese](https://mvnrepository.com/artifact/com.google.mlkit/text-recognition-japanese/16.0.1) -- Version 16.0.1 confirmed (HIGH confidence)
- [Tesseract4Android GitHub](https://github.com/adaptech-cz/Tesseract4Android) -- Version 4.9.0, JitPack distribution (MEDIUM confidence)
- [JetBrains: Ktor 3.4.0](https://blog.jetbrains.com/kotlin/2026/01/ktor-3-4-0-is-now-available/) -- Latest Ktor release (HIGH confidence)
- [JetBrains: Kotlin 2.3.10](https://kotlinlang.org/docs/releases.html) -- Latest stable Kotlin (HIGH confidence)
- [Kotlin/kotlinx.coroutines Releases](https://github.com/Kotlin/kotlinx.coroutines/releases) -- Version 1.10.2 (HIGH confidence)
- [WaniKani API Documentation](https://docs.api.wanikani.com/) -- API v2, endpoints, rate limits (HIGH confidence)
- [DeepL API Documentation](https://developers.deepl.com/docs) -- REST API, free tier limits (MEDIUM confidence)
- [aallam/openai-kotlin GitHub](https://github.com/aallam/openai-kotlin) -- OpenAI Kotlin client 4.0.1 (MEDIUM confidence)
- [xemantic/anthropic-sdk-kotlin GitHub](https://github.com/xemantic/anthropic-sdk-kotlin) -- Unofficial Claude Kotlin client (LOW confidence)
- [Presentation API + Compose Medium Article](https://medium.com/@ibrahimethemsen/using-android-presentation-api-with-jetpack-compose-998adeae1130) -- ComposeView lifecycle wiring pattern (MEDIUM confidence)
- [Coil GitHub](https://github.com/coil-kt/coil) -- Version 3.4.0 (HIGH confidence)
- [Timber GitHub](https://github.com/JakeWharton/timber) -- Version 5.0.1 (HIGH confidence)

---
*Stack research for: DS-Translator -- Android dual-screen Japanese game translator*
*Researched: 2026-03-02*
