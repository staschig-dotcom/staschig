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

    /** Open-Meteo, при недоступности — wttr.in (у некоторых провайдеров и VPN Open-Meteo не открывается). */
    @Throws(IOException::class)
    fun geocode(query: String): Place? = withFallback(
        { openMeteoGeocode(query) },
        { Wttr.place(query) },
    )

    @Throws(IOException::class)
    fun forecast(place: Place, now: Long = System.currentTimeMillis()): Weather = withFallback(
        { openMeteoForecast(place, now) },
        { Wttr.forecast(place, now) },
    )

    private fun <T> withFallback(primary: () -> T, backup: () -> T): T {
        val first = try {
            return primary()
        } catch (e: IOException) {
            e
        }
        try {
            return backup()
        } catch (e: IOException) {
            throw IOException("Open-Meteo: ${describe(first)}; wttr.in: ${describe(e)}", e)
        }
    }

    /** «UnknownHostException: …» вместо голого текста — по типу сразу видно, DNS это, SSL или таймаут. */
    fun describe(e: Throwable): String = "${e.javaClass.simpleName}: ${e.message ?: ""}".trimEnd(' ', ':')

    private fun openMeteoGeocode(query: String): Place? {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val body = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=ru&format=json")
        return parsePlace(body)
    }

    private fun openMeteoForecast(place: Place, now: Long): Weather {
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

    internal fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "InfoWidget/0.1 (Android)")
        try {
            if (conn.responseCode == 404) return "{}"
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

/** Запасной источник: wttr.in (формат j1). Коды погоды WWO переводим в WMO, чтобы остальной код не менялся. */
object Wttr {

    @Throws(IOException::class)
    fun place(query: String): Place? {
        val q = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        return parsePlace(WeatherApi.get("https://wttr.in/$q?format=j1&lang=ru"), query.trim())
    }

    @Throws(IOException::class)
    fun forecast(place: Place, now: Long): Weather {
        val url = String.format(Locale.US, "https://wttr.in/%.4f,%.4f?format=j1&lang=ru", place.lat, place.lon)
        return parseForecast(WeatherApi.get(url), place.name, now)
    }

    fun parsePlace(json: String, query: String): Place? {
        val area = JSONObject(json).optJSONArray("nearest_area")?.optJSONObject(0) ?: return null
        val name = query.replaceFirstChar { it.uppercase() }
        return Place(name, area.getString("latitude").toDouble(), area.getString("longitude").toDouble())
    }

    fun parseForecast(json: String, place: String, now: Long): Weather {
        val o = JSONObject(json)
        val c = o.getJSONArray("current_condition").getJSONObject(0)
        val day = o.getJSONArray("weather").getJSONObject(0)
        val temp = c.getString("temp_C").toDouble()
        val hour = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)
        return Weather(
            place = place,
            temp = temp,
            feels = c.optString("FeelsLikeC", c.getString("temp_C")).toDouble(),
            code = toWmo(c.getString("weatherCode").toInt()),
            isDay = hour in 7..19,
            wind = c.optString("windspeedKmph", "0").toDouble() / 3.6,
            min = day.optString("mintempC", c.getString("temp_C")).toDouble(),
            max = day.optString("maxtempC", c.getString("temp_C")).toDouble(),
            updated = now,
        )
    }

    fun toWmo(wwo: Int): Int = when (wwo) {
        113 -> 0
        116 -> 2
        119, 122 -> 3
        143, 248, 260 -> 45
        176, 263, 266, 293, 296 -> 61
        299, 302 -> 63
        305, 308 -> 65
        353 -> 80
        356 -> 81
        359 -> 82
        179, 227, 323, 326 -> 71
        329, 332 -> 73
        230, 335, 338 -> 75
        368 -> 85
        371 -> 86
        182, 185, 281, 284, 311, 314, 317, 320, 350, 362, 365, 374, 377 -> 66
        200, 386, 389, 392, 395 -> 95
        else -> -1
    }
}
