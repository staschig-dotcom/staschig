package ru.staschig.guitar

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.tabedit.EdBeat
import ru.staschig.guitar.tabedit.EdNote
import ru.staschig.guitar.tabedit.EdScore
import ru.staschig.guitar.tabedit.NoteEffect
import ru.staschig.guitar.ui.screens.BackingScreen
import ru.staschig.guitar.ui.screens.CourseScreen
import ru.staschig.guitar.ui.screens.LessonScreen
import ru.staschig.guitar.ui.screens.OnboardingScreen
import ru.staschig.guitar.ui.screens.ProgressScreen
import ru.staschig.guitar.ui.screens.TabEditorScreen
import ru.staschig.guitar.ui.screens.TabsScreen
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

    /** В layoutlib нет Activity — даём пустой реестр, чтобы работали запросы разрешений. */
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) = Unit
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) = paparazzi.snapshot(name) {
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
            GuitarTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
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

    @Test
    fun tabsAdd() = shot("tabs") { TabsScreen(store, {}, {}, {}, {}, {}) }

    @Test
    fun tabEditor() = shot("tab_editor") {
        // Заполненный таб: открываем редактор с моделью из библиотеки.
        val doc = store.library.saveEdited(
            null, "Мой риф", "Я", sampleScore().toAlphaTex(), sampleScore().toJson(),
        )
        TabEditorScreen(store, doc.id, {}, {})
    }

    @Test
    fun backing() = shot("backing") { BackingScreen(store) {} }

    private fun sampleScore() = EdScore(
        title = "Мой риф",
        beats = listOf(
            EdBeat(8, notes = listOf(EdNote(6, 0))),
            EdBeat(8, notes = listOf(EdNote(6, 3, NoteEffect.HAMMER))),
            EdBeat(8, notes = listOf(EdNote(5, 2, NoteEffect.SLIDE))),
            EdBeat(8, notes = listOf(EdNote(5, 5))),
            EdBeat(2, notes = listOf(EdNote(5, 0), EdNote(4, 2), EdNote(3, 2), EdNote(2, 1), EdNote(1, 0))),
            EdBeat(4, notes = listOf(EdNote(3, 7, NoteEffect.BEND))),
            EdBeat(4),
        ),
    )
}

class ScreenshotPortraitTest : ScreensBase(ScreenOrientation.PORTRAIT)

class ScreenshotLandscapeTest : ScreensBase(ScreenOrientation.LANDSCAPE)
