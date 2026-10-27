package by.blueocean.news.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Перевод на русский язык с кэшем в SQLite.
 *
 * Основной канал — публичный эндпоинт Google Translate (тот же, что у веб-виджета),
 * резервный — MyMemory. Возвращает null при ошибке, чтобы вызывающий код мог
 * повторить перевод позже и не сохранял английский текст как «перевод».
 */
class Translator(private val store: NewsStore) {

    private val rateLock = Mutex()

    /**
     * Переводит короткий текст (заголовок, описание).
     * @return перевод или null, если сервис недоступен.
     */
    suspend fun translate(text: String, sourceLang: String): String? {
        val clean = text.trim()
        if (clean.isEmpty()) return ""
        if (sourceLang == "ru" || isRussian(clean)) return clean
        if (clean.length < 2) return clean

        val cacheKey = "$sourceLang|$clean"
        store.translationGet(cacheKey)?.let { return it }

        val limit = if (clean.length > 4500) clean.take(4500) else clean
        var result = google(limit)
        if (result.isNullOrBlank()) result = myMemory(limit)
        val finalResult = result?.takeIf { it.isNotBlank() } ?: return null

        store.translationPut(cacheKey, finalResult)
        return finalResult
    }

    /**
     * Переводит длинный текст (полная статья) частями по абзацам.
     * @param onProgress процент выполненного перевода 0..100
     */
    suspend fun translateLong(
        text: String,
        sourceLang: String,
        maxChars: Int = MAX_FULLTEXT_CHARS,
        onProgress: (Int) -> Unit = {}
    ): String? {
        if (text.isBlank()) return ""
        if (sourceLang == "ru" || isRussian(text)) return text

        val limited = if (text.length > maxChars) text.take(maxChars) else text
        val chunks = chunk(limited, 3400)
        if (chunks.isEmpty()) return null

        val out = StringBuilder()
        chunks.forEachIndexed { index, chunk ->
            val part = translate(chunk, sourceLang) ?: return null
            out.append(part.trim()).append("\n\n")
            onProgress(((index + 1) * 100) / chunks.size)
        }
        return out.toString().trim()
    }

    suspend fun isAvailable(): Boolean = google("hello") != null

    private suspend fun google(text: String): String? = withContext(Dispatchers.IO) {
        rateLock.withLock {
            try {
                delay(40)
                val url = "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=auto&tl=ru&dt=t&q=" + Uri.encode(text)
                val body = Http.getJson(url) ?: return@withLock null
                val root = JSONArray(body)
                val segments = root.optJSONArray(0) ?: return@withLock null
                val sb = StringBuilder()
                for (i in 0 until segments.length()) {
                    val segment = segments.optJSONArray(i) ?: continue
                    sb.append(segment.optString(0, ""))
                }
                sb.toString().trim().ifBlank { null }
            } catch (t: Throwable) {
                null
            }
        }
    }

    private suspend fun myMemory(text: String): String? = withContext(Dispatchers.IO) {
        rateLock.withLock {
            try {
                val sb = StringBuilder()
                for (chunk in splitForApi(text, 450)) {
                    delay(120)
                    val url = "https://api.mymemory.translated.net/get?q=" + Uri.encode(chunk) +
                        "&langpair=en%7Cru"
                    val body = Http.getJson(url) ?: break
                    val translated = org.json.JSONObject(body)
                        .optJSONObject("responseData")
                        ?.optString("translatedText")
                        .orEmpty()
                    if (translated.isBlank()) break
                    sb.append(unescapeHtml(translated)).append(' ')
                }
                sb.toString().trim().ifBlank { null }
            } catch (t: Throwable) {
                null
            }
        }
    }

    /** Делит текст по абзацам/предложениям на куски не длиннее max. */
    fun chunk(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val chunks = mutableListOf<String>()
        val paragraphs = text.split("\n\n")
        val current = StringBuilder()

        fun flush() {
            if (current.isNotBlank()) {
                chunks.add(current.toString().trim())
                current.setLength(0)
            }
        }

        for (paragraph in paragraphs) {
            if (paragraph.length > max) {
                flush()
                var rest = paragraph
                while (rest.length > max) {
                    var cut = rest.lastIndexOf(". ", max)
                    if (cut < max / 2) cut = rest.lastIndexOf(' ', max)
                    if (cut < max / 3) cut = max
                    chunks.add(rest.substring(0, cut + 1).trim())
                    rest = rest.substring(cut + 1)
                }
                if (rest.isNotBlank()) chunks.add(rest.trim())
                continue
            }
            if (current.length + paragraph.length + 2 > max) flush()
            current.append(paragraph.trim()).append("\n\n")
        }
        flush()
        return chunks
    }

    private fun splitForApi(text: String, max: Int): List<String> = chunk(text, max)

    fun isRussian(text: String): Boolean {
        if (text.isBlank()) return false
        var cyrillic = 0
        var latin = 0
        for (ch in text) {
            when {
                ch in '\u0400'..'\u04FF' -> cyrillic++
                ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
            }
        }
        return cyrillic > 0 && cyrillic >= latin
    }

    private fun unescapeHtml(text: String): String = text
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")

    companion object {
        const val MAX_FULLTEXT_CHARS = 14000
    }
}
