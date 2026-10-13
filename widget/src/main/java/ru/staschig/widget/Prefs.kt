package ru.staschig.widget

import android.content.Context

object Prefs {
    private const val FILE = "settings"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun place(ctx: Context): Place? {
        val p = sp(ctx)
        val name = p.getString("place_name", null) ?: return null
        return Place(name, p.getFloat("place_lat", 0f).toDouble(), p.getFloat("place_lon", 0f).toDouble())
    }

    fun setPlace(ctx: Context, place: Place) {
        sp(ctx).edit()
            .putString("place_name", place.name)
            .putFloat("place_lat", place.lat.toFloat())
            .putFloat("place_lon", place.lon.toFloat())
            .remove("weather")
            .apply()
    }

    fun weather(ctx: Context): Weather? = sp(ctx).getString("weather", null)?.let { Weather.fromJson(it) }

    fun setWeather(ctx: Context, w: Weather) {
        sp(ctx).edit().putString("weather", w.toJson()).apply()
    }
}
