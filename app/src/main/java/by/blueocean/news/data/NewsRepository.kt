package by.blueocean.news.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Сбор новостей и перевод:
 * параллельно тянет включённые RSS-источники, чистит, оценивает, переводит на русский
 * и складывает в базу. Также умеет вытаскивать полный текст статьи и переводить его.
 */
class NewsRepository(
    private val store: NewsStore,
    private val prefs: AppPrefs
) {

    data class SyncResult(
        val inserted: Int,
        val translated: Int,
        val sourcesOk: Int,
        val sourcesFailed: Int,
        val message: String,
        val success: Boolean
    )

    data class ReaderResult(
        val originalText: String,
        val translatedText: String,
        val error: String? = null
    )

    private val translator = Translator(store)
    private val semaphore = Semaphore(8)

    // ─────────────── Синхронизация ───────────────

    suspend fun sync(onProgress: (String) -> Unit = {}): SyncResult = withContext(Dispatchers.IO) {
        val sources = Sources.enabled(prefs)
        if (sources.isEmpty()) {
            return@withContext SyncResult(0, 0, 0, 0, "Все источники отключены", false)
        }

        onProgress("Загружаю ${sources.size} источников…")

        var ok = 0
        var failed = 0
        val parsed = coroutineScope {
            sources.map { source ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val xml = Http.get(source.url)
                        if (xml.isNullOrBlank()) {
                            failed++
                            emptyList()
                        } else {
                            val items = runCatching { FeedParser.parse(xml, source.id) }
                                .getOrDefault(emptyList())
                            if (items.isEmpty()) failed++ else ok++
                            items.map { source to it }
                        }
                    }
                }
            }.awaitAll().flatten()
        }

        onProgress("Нашёл ${parsed.size} материалов, отбираю свежие…")

        val now = System.currentTimeMillis()
        val maxAge = 45L * 24 * 3600 * 1000
        val perSource = prefs.maxItemsPerSource

        val candidates = parsed
            .filter { it.second.link.startsWith("http") && it.second.title.isNotBlank() }
            .filter { it.second.publishedAt > now - maxAge }
            .groupBy { it.first.id }
            .values
            .flatMap { list ->
                list.distinctBy { it.second.link }
                    .sortedByDescending { it.second.publishedAt }
                    .take(perSource)
            }

        if (candidates.isEmpty()) {
            return@withContext SyncResult(0, 0, ok, failed, "Новых материалов не найдено", false)
        }

        val entities = candidates.map { (source, item) ->
            val (score, tags) = Analyzer.scoreFor(item, source)
            val category = Analyzer.detectCategory(item.title, item.summary, source.category)
            ArticleEntity(
                title = item.title,
                titleRu = if (source.lang == "ru") item.title else "",
                summary = item.summary,
                summaryRu = if (source.lang == "ru") item.summary else "",
                link = item.link,
                imageUrl = item.imageUrl,
                sourceId = source.id,
                sourceName = source.name,
                region = source.region,
                category = category,
                lang = source.lang,
                publishedAt = item.publishedAt.coerceAtMost(now),
                fetchedAt = now,
                score = score,
                moneyTags = tags.joinToString(", "),
                fullText = item.fullText
            )
        }

        val fresh = store.insertNew(entities)

        onProgress("Добавлено ${fresh.size} новых. Перевожу на русский…")

        if (fresh.isNotEmpty()) {
            translatePending(limit = 40) { done, total ->
                onProgress("Перевод: $done из $total…")
            }
        }

        runCatching { store.pruneArticles(now - 60L * 24 * 3600 * 1000) }
        runCatching { store.pruneTranslations(now - 120L * 24 * 3600 * 1000) }

        prefs.lastSyncAt = now
        val message = "Обновлено: ${fresh.size} новых из $ok источников" +
            if (failed > 0) " ($failed недоступны)" else ""
        prefs.lastSyncInfo = message
        onProgress(message)

        SyncResult(fresh.size, fresh.size, ok, failed, message, true)
    }

    // ─────────────── Перевод ленты ───────────────

    /**
     * Переводит до [limit] материалов, у которых нет русского заголовка или описания.
     * @return сколько материалов удалось перевести
     */
    suspend fun translatePending(
        limit: Int = 20,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val pending = store.pendingTranslation(limit)
        if (pending.isEmpty()) return@withContext 0
        var count = 0
        var consecutiveFailures = 0
        for ((index, article) in pending.withIndex()) {
            val ok = runCatching { translateArticle(article) }.getOrDefault(false)
            if (ok) {
                count++
                consecutiveFailures = 0
            } else {
                consecutiveFailures++
                if (consecutiveFailures >= 3) break
            }
            onProgress(index + 1, pending.size)
        }
        count
    }

    suspend fun translateOne(articleId: Long): Boolean = withContext(Dispatchers.IO) {
        val article = store.byId(articleId) ?: return@withContext false
        runCatching { translateArticle(article) }.getOrDefault(false)
    }

    /**
     * Переводит заголовок и описание. Сохраняет частичные результаты,
     * поэтому при сбое сети уже переведённый заголовок остаётся.
     */
    private suspend fun translateArticle(article: ArticleEntity): Boolean {
        if (article.lang == "ru") return false

        var changed = false
        var titleRu = article.titleRu
        var summaryRu = article.summaryRu

        if (titleRu.isBlank()) {
            // Один запрос на заголовок + описание, чтобы экономить лимиты сервиса
            val summaryPiece = article.summary.take(900)
            val combined = if (summaryPiece.isBlank()) {
                article.title
            } else {
                article.title + "\n\n" + SEPARATOR + "\n\n" + summaryPiece
            }
            val translated = translator.translate(combined, article.lang) ?: return false
            val parts = translated.split(SEPARATOR)
            if (parts.size >= 2) {
                titleRu = parts[0].trim()
                val summaryTranslated = parts.drop(1).joinToString(" ").trim()
                if (summaryTranslated.isNotBlank()) {
                    store.applySummaryTranslation(article.id, summaryTranslated)
                    summaryRu = summaryTranslated
                }
            } else {
                titleRu = translated.trim()
            }
            store.applyTitleTranslation(article.id, titleRu)
            changed = true

            // Если разделитель «потерялся» — переводим описание отдельно
            if (summaryPiece.isNotBlank() && summaryRu.isBlank()) {
                val summaryOnly = translator.translate(summaryPiece, article.lang)
                if (!summaryOnly.isNullOrBlank()) {
                    store.applySummaryTranslation(article.id, summaryOnly)
                    summaryRu = summaryOnly
                }
            }
        } else if (summaryRu.isBlank() && article.summary.isNotBlank()) {
            val summaryOnly = translator.translate(article.summary.take(900), article.lang)
            if (!summaryOnly.isNullOrBlank()) {
                store.applySummaryTranslation(article.id, summaryOnly)
                summaryRu = summaryOnly
                changed = true
            }
        }

        if (changed) {
            val category = Analyzer.detectCategory(
                titleRu,
                summaryRu,
                Analyzer.detectCategory(article.title, article.summary, article.category)
            )
            store.applyCategory(article.id, category)
        }
        return changed
    }

    // ─────────────── Полный текст статьи ───────────────

    /**
     * Загружает полный текст статьи со страницы источника и переводит его.
     * Кэшируется в базе, повторное открытие — мгновенное.
     */
    suspend fun readFullArticle(
        articleId: Long,
        onStage: (stage: String, progress: Int) -> Unit = { _, _ -> }
    ): ReaderResult = withContext(Dispatchers.IO) {
        val article = store.byId(articleId)
            ?: return@withContext ReaderResult("", "", "Статья не найдена")

        if (article.fullTextRu.isNotBlank()) {
            return@withContext ReaderResult(article.fullText, article.fullTextRu, null)
        }

        onStage("Загружаю полный текст…", 5)

        var original = article.fullText
        if (original.length < 300) {
            val html = Http.get(article.link)
            if (!html.isNullOrBlank()) {
                val extracted = runCatching { ArticleExtractor.extract(html, article.link) }
                    .getOrNull()
                if (extracted != null && extracted.text.length >= 300) {
                    original = extracted.text
                    store.setFullText(article.id, original, "")
                }
            }
        }

        var note: String? = null
        if (original.length < 300) {
            // Источник закрыт пейволом или отдаёт контент только JS
            val fallback = listOf(article.summary, article.title)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            if (fallback.isBlank()) {
                return@withContext ReaderResult("", "", "Не удалось получить текст статьи")
            }
            return@withContext ReaderResult(
                fallback,
                "",
                "Источник не отдал полный текст — показываю анонс. Откройте оригинал по кнопке ниже."
            )
        }

        onStage("Перевожу статью на русский…", 10)
        val translated = translator.translateLong(original, article.lang) { percent ->
            onStage("Перевожу статью на русский…", 10 + (percent * 0.85).toInt())
        }
        if (translated.isNullOrBlank()) {
            return@withContext ReaderResult(
                original,
                "",
                "Сервис перевода недоступен. Текст загружен, перевод повторится при следующем открытии."
            )
        }

        store.setFullText(article.id, original, translated)
        onStage("Готово", 100)
        ReaderResult(original, translated, note)
    }

    fun isRussianText(text: String): Boolean = translator.isRussian(text)

    companion object {
        private const val SEPARATOR = "[[[SEP]]]"

        @Volatile
        private var instance: NewsRepository? = null

        fun get(context: Context): NewsRepository = instance ?: synchronized(this) {
            instance ?: NewsRepository(
                NewsStore.get(context.applicationContext),
                AppPrefs(context.applicationContext)
            ).also { instance = it }
        }
    }
}
