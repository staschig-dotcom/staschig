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
        versionCode = 17
        versionName = "9.0"
    }

    // Тот же постоянный ключ, что у основного приложения: обновления ставятся поверх.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Релизный ключ для RuStore. Хранится только в секретах GitHub,
        // в CI расшифровывается во временный файл; в репозитории его нет.
        create("release") {
            val ks = System.getenv("KIDS_KEYSTORE_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storeType = "pkcs12"
                storePassword = System.getenv("KIDS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KIDS_KEY_ALIAS") ?: "sovenok"
                keyPassword = System.getenv("KIDS_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Без ключа релизная сборка остаётся неподписанной (только проверка, что собирается).
            signingConfig = if (System.getenv("KIDS_KEYSTORE_FILE") != null) signingConfigs.getByName("release") else null
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
