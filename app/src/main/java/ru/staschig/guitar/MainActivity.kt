package ru.staschig.guitar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.reminders.Reminders
import ru.staschig.guitar.ui.GuitarApp
import ru.staschig.guitar.ui.theme.GuitarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = AppStore(applicationContext)
        Reminders.schedule(this, store.settings)
        setContent {
            GuitarTheme {
                GuitarApp(store)
            }
        }
    }
}
