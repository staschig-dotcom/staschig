package ru.staschig.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherApiTest {

    @Test
    fun parsesForecast() {
        val json = """
            {"current":{"temperature_2m":-3.6,"apparent_temperature":-8.2,"weather_code":71,
              "wind_speed_10m":4.4,"is_day":0},
             "daily":{"temperature_2m_max":[-1.2],"temperature_2m_min":[-7.5]}}
        """.trimIndent()
        val w = WeatherApi.parseForecast(json, "Москва", 42L)
        assertEquals(71, w.code)
        assertEquals("🌨️ -4°", w.headline)
        assertEquals("Небольшой снег\n-7°…-1°, ветер 4 м/с\nМосква", w.details)
        assertEquals(w, Weather.fromJson(w.toJson()))
    }

    @Test
    fun parsesPlace() {
        val p = WeatherApi.parsePlace("""{"results":[{"name":"Казань","latitude":55.79,"longitude":49.12}]}""")!!
        assertEquals("Казань", p.name)
        assertNull(WeatherApi.parsePlace("""{"generationtime_ms":0.5}"""))
    }

    @Test
    fun formatsDegrees() {
        assertEquals("+12°", Weather.deg(11.6))
        assertEquals("0°", Weather.deg(-0.2))
        assertTrue(WeatherCodes.text(999) == "—")
    }

    @Test
    fun parsesWttrFallback() {
        val json = """
            {"current_condition":[{"temp_C":"8","FeelsLikeC":"5","weatherCode":"296","windspeedKmph":"18"}],
             "nearest_area":[{"latitude":"55.752","longitude":"37.616","areaName":[{"value":"Moscow"}]}],
             "weather":[{"maxtempC":"10","mintempC":"3"}]}
        """.trimIndent()
        val p = Wttr.parsePlace(json, "москва")!!
        assertEquals("Москва", p.name)
        assertEquals(55.752, p.lat, 1e-6)
        val w = Wttr.parseForecast(json, p.name, 0L)
        assertEquals(61, w.code)
        assertEquals("Небольшой дождь\n+3°…+10°, ветер 5 м/с\nМосква", w.details)
        assertNull(Wttr.parsePlace("{}", "x"))
    }
}
