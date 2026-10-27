package by.blueocean.news.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Бесплатный переводчик на русский язык с кэшем в SQLite.
 *
 * Используется публичный эндпоинт Google Translate (тот же, что у веб-виджета),
 * при ошибке — резервный сервис MyMemory. Все переводы кэшируются, поэтому
 * повторный синк не тратит трафик и не упирается в лимиты.
 */
class Translator(private val store: NewsStore) {

    private val rateLock = Mutex()

    suspend fun translate(text: String, sourceLang: String): String {
        val clean = text.trim()
        if (clean.isEmpty()) return ""
        if (sourceLang == "ru" || isRussian(clean)) return clean
        if (clean.length < 3) return clean

        val cacheKey = "$sourceLang|$clean"
        store.translationGet(cacheKey)?.let { return it }

        val limit = if (clean.length > 4500) clean.take(4500) else clean
        var result = google(limit)
        if (result.isNullOrBlank()) result = myMemory(limit)
        val finalResult = result?.takeIf { it.isNotBlank() } ?: return clean

        store.translationPut(cacheKey, finalResult)
        return finalResult
    }

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

    private fun splitForApi(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val chunks = mutableListOf<String>()
        var rest = text
        while (rest.length > max) {
            var cut = rest.lastIndexOf(". ", max)
            if (cut < max / 2) cut = rest.lastIndexOf(' ', max)
            if (cut < max / 3) cut = max
            chunks.add(rest.substring(0, cut + 1).trim())
            rest = rest.substring(cut + 1)
        }
        if (rest.isNotBlank()) chunks.add(rest.trim())
        return chunks
    }

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
}
