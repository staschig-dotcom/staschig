plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Детская игра «Совёнок Уху»: HTML-игра из assets внутри WebView, работает офлайн.
android {
    namespace = "ru.staschig.zvezdy"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.staschig.zvezdy"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "5.1"
    }

    // Тот же постоянный ключ, что у основного приложения: обновления ставятся поверх.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources {
        // Шрифты и html не сжимать — WebView читает их напрямую.
        noCompress += listOf("ttf", "html")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
}
