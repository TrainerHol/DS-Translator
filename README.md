# DS-Translator
A dual screen translator for the AYN Thor

## Releases

APKs are built and published automatically when the version changes on the `main` branch.

### How to change the version

Edit `app/build.gradle.kts` and update both `versionCode` and `versionName` in the `defaultConfig` block:

```kotlin
defaultConfig {
    applicationId = "com.dstranslator"
    minSdk = 28
    targetSdk = 35
    versionCode = 2          // Increment by 1 each release
    versionName = "1.1"      // Semantic version string
    ...
}
```

- **`versionCode`** — Integer that must increase with every release. Used by Android to determine update order.
- **`versionName`** — Human-readable version string shown to users (e.g. `"1.0"`, `"1.1"`, `"2.0"`).

Commit the change and push to `main`. The GitHub Action will detect the `versionName` change, build the APK, and create a new GitHub Release tagged `v<versionName>`.
