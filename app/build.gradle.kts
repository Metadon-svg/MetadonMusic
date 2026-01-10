plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python") // Подключаем Python
}

android {
    namespace = "com.metadon.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.metadon.music"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Настройка Python
        ndk { abiFilters("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
        python {
            version = "3.10"
            pip {
                install("ytmusicapi")
                install("yt-dlp")
            }
        }
    }
    
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.media3:media3-exoplayer:1.2.0")
}
