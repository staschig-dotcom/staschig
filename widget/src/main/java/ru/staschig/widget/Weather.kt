package ru.staschig.widget

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

data class Place(val name: String, val lat: Double, val lon: Double)

data class Weather(
    val place: String,
    val temp: Double,
    val feels: Double,
    val code: Int,
    val isDay: Boolean,
    val wind: Double,
    val min: Double,
    val max: Double,
    val updated: Long,
) {
    val headline: String get() = "${WeatherCodes.icon(code, isDay)} ${deg(temp)}"

    val details: String
        get() = "${WeatherCodes.text(code)}\n${deg(min)}…${deg(max)}, ветер ${wind.roundToInt()} м/с\n$place"

    fun toJson(): String = JSONObject()
        .put("place", place).put("temp", temp).put("feels", feels).put("code", code)
        .put("isDay", isDay).put("wind", wind).put("min", min).put("max", max)
        .put("updated", updated)
        .toString()

    companion object {
        fun deg(v: Double): String {
            val r = v.roundToInt()
            return if (r > 0) "+$r°" else "$r°"
        }

        fun fromJson(s: String): Weather? = try {
            val o = JSONObject(s)
            Weather(
                o.getString("place"), o.getDouble("temp"), o.getDouble("feels"), o.getInt("code"),
                o.getBoolean("isDay"), o.getDouble("wind"), o.getDouble("min"), o.getDouble("max"),
                o.getLong("updated"),
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** Open-Meteo: бесплатно, без ключа. https://open-meteo.com */
object WeatherApi {

    @Throws(IOException::class)
    fun geocode(query: String): Place? {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val body = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=ru&format=json")
        return parsePlace(body)
    }

    @Throws(IOException::class)
    fun forecast(place: Place, now: Long = System.currentTimeMillis()): Weather {
        // Locale.US: иначе в русской локали дробная часть уйдёт через запятую и API вернёт ошибку.
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&wind_speed_unit=ms&timezone=auto&forecast_days=1",
            place.lat, place.lon,
        )
        return parseForecast(get(url), place.name, now)
    }

    fun parsePlace(json: String): Place? {
        val results = JSONObject(json).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val r = results.getJSONObject(0)
        return Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
    }

    fun parseForecast(json: String, place: String, now: Long): Weather {
        val o = JSONObject(json)
        val c = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val temp = c.getDouble("temperature_2m")
        return Weather(
            place = place,
            temp = temp,
            feels = c.getDouble("apparent_temperature"),
            code = c.getInt("weather_code"),
            isDay = c.optInt("is_day", 1) == 1,
            wind = c.getDouble("wind_speed_10m"),
            min = d.getJSONArray("temperature_2m_min").optDouble(0, temp),
            max = d.getJSONArray("temperature_2m_max").optDouble(0, temp),
            updated = now,
        )
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/** Коды погоды WMO, которые отдаёт Open-Meteo. */
object WeatherCodes {
    fun text(code: Int): String = when (code) {
        0 -> "Ясно"
        1 -> "Малооблачно"
        2 -> "Переменная облачность"
        3 -> "Пасмурно"
        45, 48 -> "Туман"
        51, 53, 55 -> "Морось"
        56, 57 -> "Ледяная морось"
        61 -> "Небольшой дождь"
        63 -> "Дождь"
        65 -> "Сильный дождь"
        66, 67 -> "Ледяной дождь"
        71 -> "Небольшой снег"
        73 -> "Снег"
        75 -> "Сильный снег"
        77 -> "Снежная крупа"
        80, 81 -> "Ливень"
        82 -> "Сильный ливень"
        85, 86 -> "Снегопад"
        95 -> "Гроза"
        96, 99 -> "Гроза с градом"
        else -> "—"
    }

    fun icon(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1 -> if (isDay) "🌤️" else "🌙"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57, 61, 63, 66, 80, 81 -> "🌦️"
        65, 67, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }
}
