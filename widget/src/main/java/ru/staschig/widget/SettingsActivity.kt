package ru.staschig.widget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.io.IOException

class SettingsActivity : Activity() {

    private lateinit var cityInput: EditText
    private lateinit var weatherStatus: TextView
    private lateinit var accessStatus: TextView
    private lateinit var saveButton: Button
    private lateinit var calendarStatus: TextView
    private lateinit var calendarButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        cityInput = findViewById(R.id.city_input)
        weatherStatus = findViewById(R.id.weather_status)
        accessStatus = findViewById(R.id.access_status)
        saveButton = findViewById(R.id.city_save)

        Prefs.place(this)?.let { cityInput.setText(it.name) }
        saveButton.setOnClickListener { saveCity() }
        cityInput.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                saveCity()
                true
            } else {
                false
            }
        }

        calendarStatus = findViewById(R.id.calendar_status)
        calendarButton = findViewById(R.id.calendar_button)
        calendarButton.setOnClickListener { requestCalendar() }

        findViewById<Button>(R.id.access_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.miui_autostart).setOnClickListener { Xiaomi.openAutostart(this) }
        findViewById<Button>(R.id.miui_battery).setOnClickListener { Xiaomi.openAppDetails(this) }
        findViewById<View>(R.id.miui_block).visibility = if (Xiaomi.isXiaomi) View.VISIBLE else View.GONE

        val pin = findViewById<Button>(R.id.pin_button)
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            pin.setOnClickListener {
                manager.requestPinAppWidget(ComponentName(this, InfoWidgetProvider::class.java), null, null)
            }
        } else {
            pin.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        val access = Widgets.hasNotificationAccess(this)
        accessStatus.text = if (access) {
            "✅ Доступ выдан. В виджете — последние ${NotificationStore.MAX} уведомлений (кроме музыки, загрузок и фоновых)."
        } else {
            "❌ Нет доступа. Нажмите кнопку и включите «Инфо-виджет». " +
                "Тексты уведомлений хранятся только на телефоне и никуда не отправляются."
        }
        showWeather()
        showCalendar()
        Widgets.updateAll(this)
    }

    private fun showCalendar() {
        val granted = CalendarRepo.hasPermission(this)
        calendarStatus.text = if (granted) {
            "✅ Доступ выдан. В виджете — до 5 ближайших события на сегодня–завтра из всех календарей телефона " +
                "(Google-календарь, если аккаунт добавлен и синхронизация включена)."
        } else {
            "❌ Нет доступа. Нужно только чтение; данные не покидают телефон."
        }
        calendarButton.visibility = if (granted) View.GONE else View.VISIBLE
        if (granted) CalendarWatchWorker.schedule(this)
    }

    private fun requestCalendar() {
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        val askedBefore = prefs.getBoolean("calendar_asked", false)
        if (askedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
            // Система больше не показывает запрос («Больше не спрашивать») — только через настройки приложения.
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
            Toast.makeText(this, "Разрешения → Календарь → Разрешить", Toast.LENGTH_LONG).show()
            return
        }
        prefs.edit().putBoolean("calendar_asked", true).apply()
        requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQ_CALENDAR)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CALENDAR) {
            showCalendar()
            Widgets.updateAll(this)
        }
    }

    private fun showWeather() {
        val w = Prefs.weather(this)
        weatherStatus.text = when {
            w != null -> "${w.headline}, ощущается как ${Weather.deg(w.feels)}\n${w.details}"
            Prefs.place(this) != null -> "Загружаю погоду…"
            else -> "Город не задан."
        }
    }

    private fun saveCity() {
        val query = cityInput.text.toString().trim()
        if (query.isEmpty()) return
        saveButton.isEnabled = false
        weatherStatus.text = "Ищу «$query»…"
        Thread {
            val result: String = try {
                val place = WeatherApi.geocode(query)
                if (place == null) {
                    "Город «$query» не найден. Попробуйте иначе (например, латиницей)."
                } else {
                    Prefs.setPlace(this, place)
                    Prefs.setWeather(this, WeatherApi.forecast(place))
                    Widgets.updateAll(this)
                    WeatherWorker.schedule(this)
                    ""
                }
            } catch (e: IOException) {
                "Нет связи с сервером погоды: ${e.message}"
            } catch (e: Exception) {
                "Ошибка: ${e.message}"
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                saveButton.isEnabled = true
                if (result.isEmpty()) {
                    Prefs.place(this)?.let { cityInput.setText(it.name) }
                    showWeather()
                    Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
                } else {
                    weatherStatus.text = result
                }
            }
        }.start()
    }

    companion object {
        private const val REQ_CALENDAR = 1
    }
}
