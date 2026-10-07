package ru.staschig.guitar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.ui.screens.CourseScreen
import ru.staschig.guitar.ui.screens.LessonScreen
import ru.staschig.guitar.ui.screens.OnboardingScreen
import ru.staschig.guitar.ui.screens.ProgressScreen
import ru.staschig.guitar.ui.screens.TodayScreen
import ru.staschig.guitar.ui.screens.ToolsScreen
import ru.staschig.guitar.ui.theme.GuitarTheme

/**
 * Скриншоты экранов (Paparazzi). В CI: ./gradlew recordPaparazziDebug → app/src/test/snapshots/images.
 * Нужны, чтобы проверять вёрстку без телефона — в обеих ориентациях.
 */
abstract class ScreensBase(orientation: ScreenOrientation) {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(orientation = orientation),
        theme = "android:Theme.Material.NoActionBar",
    )

    private val store by lazy { AppStore(paparazzi.context) }

    private fun shot(name: String, content: @Composable () -> Unit) = paparazzi.snapshot(name) {
        GuitarTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
        }
    }

    @Test
    fun onboardingLevel() = shot("onboarding_level") { OnboardingScreen(store, {}, initialStep = 1) }

    @Test
    fun onboardingDone() = shot("onboarding_done") { OnboardingScreen(store, {}, initialStep = 4) }

    @Test
    fun today() = shot("today") { TodayScreen(store, {}, {}, {}) }

    @Test
    fun course() = shot("course") { CourseScreen(store) {} }

    @Test
    fun tools() = shot("tools") { ToolsScreen {} }

    @Test
    fun lessonTune() = shot("lesson_tune") { LessonScreen(store, "b2", {}, { _, _ -> }, {}, {}, { _, _, _ -> }) }

    @Test
    fun lessonExercise() = shot("lesson_exercise") {
        store.markTuned()
        LessonScreen(store, "b4", {}, { _, _ -> }, {}, {}, { _, _, _ -> })
    }

    @Test
    fun progress() = shot("progress") { ProgressScreen(store) }
}

class ScreenshotPortraitTest : ScreensBase(ScreenOrientation.PORTRAIT)

class ScreenshotLandscapeTest : ScreensBase(ScreenOrientation.LANDSCAPE)
