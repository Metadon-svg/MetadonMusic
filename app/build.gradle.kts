plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python") // Убедись, что плагин есть в корневом build.gradle.kts
}

android {
    namespace = "com.metadon.music"
    compileSdk = 34

    // Важно для GitHub Actions
    ndkVersion = "25.1.8937393"

    defaultConfig {
        applicationId = "com.metadon.music"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // ИСПРАВЛЕННЫЙ БЛОК NDK
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        // ИСПРАВЛЕННЫЙ БЛОК PYTHON
        python {
            version = "3.10"
            pip {
                install("ytmusicapi")
                install("yt-dlp")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Прямые версии Compose для стабильности
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    
    // Media3 для звука
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-session:1.2.0")
    
    // Coil для обложек
    implementation("io.coil-kt:coil-compose:2.5.0")
}
