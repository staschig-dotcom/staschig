package ru.staschig.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
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

        findViewById<Button>(R.id.access_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

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
        Widgets.updateAll(this)
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
}
