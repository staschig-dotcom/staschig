package ru.staschig.guitar

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.ScreenMode
import ru.staschig.guitar.reminders.Reminders
import ru.staschig.guitar.ui.GuitarApp
import ru.staschig.guitar.ui.theme.GuitarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = AppStore(applicationContext)
        Reminders.schedule(this, store.settings)
        setContent {
            val mode = store.settings.screenMode
            LaunchedEffect(mode) {
                requestedOrientation = when (mode) {
                    ScreenMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    ScreenMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    ScreenMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                }
            }
            GuitarTheme {
                GuitarApp(store)
            }
        }
    }
}
