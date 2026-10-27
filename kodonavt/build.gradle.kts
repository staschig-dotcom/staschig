plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// «Кодонавт»: HTML-игра (three.js + Pyodide) внутри WebView, работает офлайн.
// Файлы игры лежат в python-game/ и подключаются как assets — отдельной копии нет.
android {
    namespace = "ru.staschig.kodonavt"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.staschig.kodonavt"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }

    // Тот же постоянный ключ, что у остальных приложений: обновления ставятся поверх.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    sourceSets {
        getByName("main").assets.srcDir(rootProject.file("python-game"))
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
        // Python и 3D читаются WebView напрямую — не сжимать. Тесты и описание в APK не нужны.
        noCompress += listOf("wasm", "zip", "js", "json", "html", "css", "svg", "png")
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:!test:!README.md:!sw.js"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
}
