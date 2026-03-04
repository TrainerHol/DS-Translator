plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.dstranslator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dstranslator"
        minSdk = 28
        targetSdk = 35
        versionCode = 8
        versionName = "1.07"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val isCi = System.getenv("CI")?.equals("true", ignoreCase = true) == true
    if (isCi) {
        signingConfigs {
            create("release") {
                val keystorePath =
                    requireNotNull(System.getenv("ANDROID_KEYSTORE_PATH")) { "Missing ANDROID_KEYSTORE_PATH" }
                val keystorePassword =
                    requireNotNull(System.getenv("ANDROID_KEYSTORE_PASSWORD")) { "Missing ANDROID_KEYSTORE_PASSWORD" }
                val keyAlias =
                    requireNotNull(System.getenv("ANDROID_KEY_ALIAS")) { "Missing ANDROID_KEY_ALIAS" }
                val keyPassword =
                    requireNotNull(System.getenv("ANDROID_KEY_PASSWORD")) { "Missing ANDROID_KEY_PASSWORD" }

                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (isCi) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ML Kit
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.translate)

    // DeepL
    implementation(libs.deepl.java)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Security
    implementation(libs.security.crypto)

    // Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)

    // Lifecycle
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Coil
    implementation(libs.coil.compose)

    // OkHttp
    implementation(libs.okhttp)

    // Sudachi (Japanese morphological analysis)
    implementation(libs.sudachi)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.json)
}
