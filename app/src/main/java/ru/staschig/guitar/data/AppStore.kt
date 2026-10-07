package ru.staschig.guitar.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import ru.staschig.guitar.lessons.Focus
import ru.staschig.guitar.lessons.Level
import java.time.LocalDate

data class Settings(
    val level: Level = Level.BEGINNER,
    val dailyMinutes: Int = 20,
    val daysPerWeek: Int = 5,
    val focus: Focus = Focus.BALANCED,
    val tuningId: String = "standard",
    val a4: Int = 440,
)

data class SavedTab(val title: String, val url: String, val lessonId: String? = null)

/**
 * Простое локальное хранилище на SharedPreferences + JSON.
 * Все поля — Compose-состояние, экраны перерисовываются автоматически.
 */
class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("guitar_practice", Context.MODE_PRIVATE)

    var settings by mutableStateOf(loadSettings())
        private set
    /** id урока -> дата завершения (ISO). */
    var completed by mutableStateOf(loadMap("completed"))
        private set
    /** дата (ISO) -> минут практики. */
    var practiceLog by mutableStateOf(loadIntMap("practice_log"))
        private set
    /** id упражнения -> лучший темп. */
    var bpmRecords by mutableStateOf(loadIntMap("bpm_records"))
        private set
    var tabs by mutableStateOf(loadTabs())
        private set

    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(settings)
        settings = s
        prefs.edit()
            .putString("level", s.level.name)
            .putInt("daily_minutes", s.dailyMinutes)
            .putInt("days_per_week", s.daysPerWeek)
            .putString("focus", s.focus.name)
            .putString("tuning", s.tuningId)
            .putInt("a4", s.a4)
            .apply()
    }

    fun completeLesson(id: String) {
        completed = completed + (id to LocalDate.now().toString())
        saveMap("completed", completed)
    }

    fun uncompleteLesson(id: String) {
        completed = completed - id
        saveMap("completed", completed)
    }

    fun addPracticeMinutes(minutes: Int) {
        if (minutes <= 0) return
        val today = LocalDate.now().toString()
        practiceLog = practiceLog + (today to (practiceLog[today] ?: 0) + minutes)
        saveIntMap("practice_log", practiceLog)
    }

    fun recordBpm(exerciseId: String, bpm: Int) {
        if (bpm <= (bpmRecords[exerciseId] ?: 0)) return
        bpmRecords = bpmRecords + (exerciseId to bpm)
        saveIntMap("bpm_records", bpmRecords)
    }

    fun addTab(tab: SavedTab) {
        if (tabs.any { it.url == tab.url && it.lessonId == tab.lessonId }) return
        tabs = tabs + tab
        saveTabs()
    }

    fun removeTab(tab: SavedTab) {
        tabs = tabs - tab
        saveTabs()
    }

    fun resetProgress() {
        completed = emptyMap(); practiceLog = emptyMap(); bpmRecords = emptyMap()
        saveMap("completed", completed)
        saveIntMap("practice_log", practiceLog)
        saveIntMap("bpm_records", bpmRecords)
    }

    // ---- Аналитика прогресса ----

    fun minutesOn(date: LocalDate): Int = practiceLog[date.toString()] ?: 0

    fun minutesToday(): Int = minutesOn(LocalDate.now())

    fun minutesThisWeek(): Int {
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return (0..6).sumOf { minutesOn(monday.plusDays(it.toLong())) }
    }

    /** Серия дней подряд с практикой (сегодня можно ещё не заниматься). */
    fun streak(): Int {
        var day = LocalDate.now()
        if (minutesOn(day) == 0) day = day.minusDays(1)
        var count = 0
        while (minutesOn(day) > 0) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun totalMinutes(): Int = practiceLog.values.sum()

    // ---- Загрузка/сохранение ----

    private fun loadSettings() = Settings(
        level = runCatching { Level.valueOf(prefs.getString("level", null)!!) }.getOrDefault(Level.BEGINNER),
        dailyMinutes = prefs.getInt("daily_minutes", 20),
        daysPerWeek = prefs.getInt("days_per_week", 5),
        focus = runCatching { Focus.valueOf(prefs.getString("focus", null)!!) }.getOrDefault(Focus.BALANCED),
        tuningId = prefs.getString("tuning", "standard") ?: "standard",
        a4 = prefs.getInt("a4", 440),
    )

    private fun loadMap(key: String): Map<String, String> {
        val json = runCatching { JSONObject(prefs.getString(key, "{}")!!) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    private fun saveMap(key: String, map: Map<String, String>) {
        prefs.edit().putString(key, JSONObject(map).toString()).apply()
    }

    private fun loadIntMap(key: String): Map<String, Int> {
        val json = runCatching { JSONObject(prefs.getString(key, "{}")!!) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().associateWith { json.getInt(it) }
    }

    private fun saveIntMap(key: String, map: Map<String, Int>) {
        prefs.edit().putString(key, JSONObject(map as Map<*, *>).toString()).apply()
    }

    private fun loadTabs(): List<SavedTab> {
        val arr = runCatching { JSONArray(prefs.getString("tabs", "[]")!!) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SavedTab(
                title = o.optString("title"),
                url = o.optString("url"),
                lessonId = o.optString("lesson").ifEmpty { null },
            )
        }
    }

    private fun saveTabs() {
        val arr = JSONArray()
        tabs.forEach {
            arr.put(JSONObject().put("title", it.title).put("url", it.url).put("lesson", it.lessonId ?: ""))
        }
        prefs.edit().putString("tabs", arr.toString()).apply()
    }
}
