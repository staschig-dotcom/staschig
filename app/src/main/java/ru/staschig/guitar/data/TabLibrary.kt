package ru.staschig.guitar.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

enum class TabKind(val title: String) {
    GP("Guitar Pro / MusicXML"),
    PDF("PDF"),
    TEXT("Текст"),
    OTHER("Файл"),
}

/** Таб, сохранённый на телефоне: файл (GP/PDF) или текст. */
data class TabDoc(
    val id: String,
    val title: String,
    val artist: String = "",
    val kind: TabKind,
    val fileName: String? = null,
    val text: String? = null,
    val sourceUrl: String? = null,
    val lessonId: String? = null,
    val created: Long = System.currentTimeMillis(),
)

/** Офлайн-библиотека табов в internal storage приложения. */
class TabLibrary(context: Context) {
    val dir = File(context.filesDir, "tabfiles").apply { mkdirs() }
    private val index = File(context.filesDir, "tabs_index.json")

    var docs by mutableStateOf(load())
        private set

    /** Черновик для редактора (передаётся между экранами без сериализации в аргументы навигации). */
    var draft: TabDoc? = null

    fun byId(id: String): TabDoc? = docs.firstOrNull { it.id == id }

    fun file(doc: TabDoc): File? = doc.fileName?.let { File(dir, it) }?.takeIf { it.exists() }

    fun save(doc: TabDoc) {
        docs = if (docs.any { it.id == doc.id }) docs.map { if (it.id == doc.id) doc else it } else docs + doc
        persist()
    }

    fun delete(doc: TabDoc) {
        file(doc)?.delete()
        docs = docs.filterNot { it.id == doc.id }
        persist()
    }

    fun newText(title: String, text: String, sourceUrl: String? = null, lessonId: String? = null) =
        TabDoc(UUID.randomUUID().toString(), title, kind = TabKind.TEXT, text = text, sourceUrl = sourceUrl, lessonId = lessonId)

    /** Сохраняет поток как файл библиотеки. */
    fun importStream(name: String, input: InputStream, sourceUrl: String? = null, lessonId: String? = null): TabDoc {
        val id = UUID.randomUUID().toString()
        val safe = name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80).ifBlank { "tab" }
        val fileName = "${id.take(8)}_$safe"
        File(dir, fileName).outputStream().use { out -> input.use { it.copyTo(out) } }
        val doc = TabDoc(
            id = id,
            title = safe.substringBeforeLast('.'),
            kind = kindOf(safe),
            fileName = fileName,
            sourceUrl = sourceUrl,
            lessonId = lessonId,
        )
        save(doc)
        return doc
    }

    /** Скачивание файла, который сайт отдаёт на загрузку (с куки WebView). */
    suspend fun download(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        lessonId: String?,
    ): TabDoc = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        userAgent?.let { conn.setRequestProperty("User-Agent", it) }
        CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            if (conn.contentLengthLong > MAX_BYTES) error("файл больше 30 МБ")
            val name = URLUtil.guessFileName(url, contentDisposition ?: conn.getHeaderField("Content-Disposition"),
                mimeType ?: conn.contentType)
            val bytes = conn.inputStream.use { it.readBytes() }
            // Состояние Compose меняем на главном потоке.
            withContext(Dispatchers.Main) { importStream(name, bytes.inputStream(), url, lessonId) }
        } finally {
            conn.disconnect()
        }
    }

    private fun load(): List<TabDoc> {
        if (!index.exists()) return emptyList()
        val arr = runCatching { JSONArray(index.readText()) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            runCatching {
                TabDoc(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    kind = TabKind.valueOf(o.optString("kind", "OTHER")),
                    fileName = o.optString("file").ifEmpty { null },
                    text = if (o.has("text")) o.getString("text") else null,
                    sourceUrl = o.optString("source").ifEmpty { null },
                    lessonId = o.optString("lesson").ifEmpty { null },
                    created = o.optLong("created"),
                )
            }.getOrNull()
        }
    }

    private fun persist() {
        val arr = JSONArray()
        docs.forEach { d ->
            val o = JSONObject()
                .put("id", d.id).put("title", d.title).put("artist", d.artist).put("kind", d.kind.name)
                .put("file", d.fileName ?: "").put("source", d.sourceUrl ?: "").put("lesson", d.lessonId ?: "")
                .put("created", d.created)
            d.text?.let { o.put("text", it) }
            arr.put(o)
        }
        index.writeText(arr.toString())
    }

    companion object {
        private const val MAX_BYTES = 30L * 1024 * 1024

        fun kindOf(name: String): TabKind = when (name.substringAfterLast('.', "").lowercase()) {
            "gp", "gp3", "gp4", "gp5", "gpx", "gp7", "xml", "musicxml", "mxl", "cap", "atex", "alphatex" -> TabKind.GP
            "pdf" -> TabKind.PDF
            "txt", "tab", "crd", "pro", "chopro" -> TabKind.TEXT
            else -> TabKind.OTHER
        }
    }
}
