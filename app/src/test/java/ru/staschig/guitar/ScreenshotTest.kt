package ru.staschig.guitar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.ui.screens.LessonsScreen
import ru.staschig.guitar.ui.screens.ProgressScreen
import ru.staschig.guitar.ui.theme.GuitarTheme

/**
 * Скриншоты экранов (Paparazzi). В CI: ./gradlew recordPaparazziDebug → app/src/test/snapshots/images.
 * Нужны, чтобы проверять вёрстку без телефона.
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    private val store by lazy { AppStore(paparazzi.context) }

    private fun shot(name: String, content: @Composable () -> Unit) = paparazzi.snapshot(name) {
        GuitarTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
        }
    }

    @Test
    fun lessons() = shot("lessons") { LessonsScreen(store, {}, {}) }

    @Test
    fun progress() = shot("progress") { ProgressScreen(store) }
}
