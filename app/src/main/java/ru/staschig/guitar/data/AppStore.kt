package ru.staschig.guitar.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import ru.staschig.guitar.lessons.Curriculum
import ru.staschig.guitar.lessons.Focus
import ru.staschig.guitar.lessons.Lesson
import ru.staschig.guitar.lessons.Level
import java.time.LocalDate

/** Ориентация экрана. */
enum class ScreenMode(val title: String) {
    LANDSCAPE("Горизонтальная"),
    PORTRAIT("Вертикальная"),
    AUTO("Как повернут телефон"),
}

data class Settings(
    val level: Level = Level.BEGINNER,
    val dailyMinutes: Int = 20,
    val daysPerWeek: Int = 5,
    val focus: Focus = Focus.BALANCED,
    val tuningId: String = "standard",
    val a4: Int = 440,
    /** Задержка «звук → динамик → микрофон», мс (null — не откалибровано). */
    val latencyMs: Int? = null,
    val reminderOn: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    /** Дни недели напоминаний: 1 = понедельник … 7 = воскресенье. */
    val reminderDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val screenMode: ScreenMode = ScreenMode.LANDSCAPE,
)

/** Лучший результат в режиме игры для пьесы/таба. */
data class PlayBest(val stars: Int, val accuracy: Int, val speed: Int)

data class RhythmRun(val date: String, val bpm: Int, val accuracy: Int, val meanMs: Int, val stdMs: Int)

data class SavedTab(val title: String, val url: String, val lessonId: String? = null)

/**
 * Простое локальное хранилище на SharedPreferences + JSON.
 * Все поля — Compose-состояние, экраны перерисовываются автоматически.
 */
class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("guitar_practice", Context.MODE_PRIVATE)

    /** В рендере скриншотов (layoutlib) edit() возвращает null — тогда изменения просто не сохраняются. */
    private fun editor(): SharedPreferences.Editor = prefs.edit() ?: NoopEditor

    /** Запись настроек; в layoutlib методы редактора возвращают null — там запись пропускается. */
    private inline fun save(block: () -> Unit) {
        runCatching(block)
    }

    /** Офлайн-табы (файлы и тексты). */
    val library = TabLibrary(context)

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
    var rhythmRuns by mutableStateOf(loadRhythm())
        private set
    /** Пройдено знакомство с приложением (первый запуск). */
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false))
        private set
    private var tunedOn by mutableStateOf(prefs.getString("tuned_on", "") ?: "")

    /** Опыт за режим игры. */
    var xp by mutableStateOf(prefs.getInt("xp", 0))
        private set
    var playBest by mutableStateOf(loadPlayBest())
        private set

    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(settings)
        settings = s
        save {
            editor()
                .putString("level", s.level.name)
                .putInt("daily_minutes", s.dailyMinutes)
                .putInt("days_per_week", s.daysPerWeek)
                .putString("focus", s.focus.name)
                .putString("tuning", s.tuningId)
                .putInt("a4", s.a4)
                .putInt("latency_ms", s.latencyMs ?: Int.MIN_VALUE)
                .putBoolean("reminder_on", s.reminderOn)
                .putInt("reminder_hour", s.reminderHour)
                .putInt("reminder_minute", s.reminderMinute)
                .putString("reminder_days", s.reminderDays.sorted().joinToString(","))
                .putString("screen_mode", s.screenMode.name)
                .apply()
        }
    }

    fun finishOnboarding() {
        onboarded = true
        save {
            editor().putBoolean("onboarded", true).apply()
        }
    }

    fun tunedToday(): Boolean = tunedOn == LocalDate.now().toString()

    fun markTuned() {
        tunedOn = LocalDate.now().toString()
        save {
            editor().putString("tuned_on", tunedOn).apply()
        }
    }

    /** Следующий урок: первый непройденный на выбранном уровне, затем — на остальных. */
    fun nextLesson(): Lesson? =
        Curriculum.byLevel(settings.level).firstOrNull { it.id !in completed }
            ?: Curriculum.lessons.firstOrNull { it.id !in completed }

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

    /** Сохраняет итог прохождения; возвращает true, если это новый рекорд. */
    fun addPlayResult(id: String, stars: Int, accuracy: Int, speed: Int, xpGained: Int): Boolean {
        xp += xpGained
        val old = playBest[id]
        val better = old == null || stars > old.stars ||
            (stars == old.stars && (speed > old.speed || (speed == old.speed && accuracy > old.accuracy)))
        if (better) playBest = playBest + (id to PlayBest(stars, accuracy, speed))
        val o = JSONObject()
        playBest.forEach { (k, v) -> o.put(k, JSONObject().put("stars", v.stars).put("acc", v.accuracy).put("speed", v.speed)) }
        save {
            editor().putInt("xp", xp).putString("play_best", o.toString()).apply()
        }
        return better
    }

    private fun loadPlayBest(): Map<String, PlayBest> {
        val o = runCatching { JSONObject(prefs.getString("play_best", "{}")!!) }.getOrNull() ?: return emptyMap()
        return o.keys().asSequence().associateWith {
            val v = o.getJSONObject(it)
            PlayBest(v.optInt("stars"), v.optInt("acc"), v.optInt("speed", 100))
        }
    }

    fun addRhythmRun(run: RhythmRun) {
        rhythmRuns = (rhythmRuns + run).takeLast(50)
        val arr = JSONArray()
        rhythmRuns.forEach {
            arr.put(JSONObject().put("date", it.date).put("bpm", it.bpm).put("acc", it.accuracy)
                .put("mean", it.meanMs).put("std", it.stdMs))
        }
        save {
            editor().putString("rhythm_log", arr.toString()).apply()
        }
    }

    private fun loadRhythm(): List<RhythmRun> {
        val arr = runCatching { JSONArray(prefs.getString("rhythm_log", "[]")!!) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            RhythmRun(o.optString("date"), o.optInt("bpm"), o.optInt("acc"), o.optInt("mean"), o.optInt("std"))
        }
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
        latencyMs = prefs.getInt("latency_ms", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
        reminderOn = prefs.getBoolean("reminder_on", false),
        reminderHour = prefs.getInt("reminder_hour", 19),
        reminderMinute = prefs.getInt("reminder_minute", 0),
        reminderDays = (prefs.getString("reminder_days", "1,2,3,4,5") ?: "")
            .split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
        screenMode = runCatching { ScreenMode.valueOf(prefs.getString("screen_mode", null)!!) }
            .getOrDefault(ScreenMode.LANDSCAPE),
    )

    private fun loadMap(key: String): Map<String, String> {
        val json = runCatching { JSONObject(prefs.getString(key, "{}")!!) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    private fun saveMap(key: String, map: Map<String, String>) {
        save {
            editor().putString(key, JSONObject(map).toString()).apply()
        }
    }

    private fun loadIntMap(key: String): Map<String, Int> {
        val json = runCatching { JSONObject(prefs.getString(key, "{}")!!) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().associateWith { json.getInt(it) }
    }

    private fun saveIntMap(key: String, map: Map<String, Int>) {
        save {
            editor().putString(key, JSONObject(map as Map<*, *>).toString()).apply()
        }
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
        save {
            editor().putString("tabs", arr.toString()).apply()
        }
    }
}

private object NoopEditor : SharedPreferences.Editor {
    override fun putString(key: String?, value: String?) = this
    override fun putStringSet(key: String?, values: MutableSet<String>?) = this
    override fun putInt(key: String?, value: Int) = this
    override fun putLong(key: String?, value: Long) = this
    override fun putFloat(key: String?, value: Float) = this
    override fun putBoolean(key: String?, value: Boolean) = this
    override fun remove(key: String?) = this
    override fun clear() = this
    override fun commit() = true
    override fun apply() {}
}
