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
 * Сбор новостей: параллельно тянет включённые RSS-источники, чистит, оценивает,
 * переводит на русский и складывает в базу.
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

    private val translator = Translator(store)
    private val semaphore = Semaphore(8)

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
                summary = item.summary,
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
                moneyTags = tags.joinToString(", ")
            )
        }

        val fresh = store.insertNew(entities)

        onProgress("Добавлено ${fresh.size} новых. Перевожу на русский…")

        var translatedCount = 0
        val toTranslate = fresh.sortedByDescending { it.score }.take(70)
        for ((index, article) in toTranslate.withIndex()) {
            try {
                translateAndAnalyze(article)
                translatedCount++
            } catch (t: Throwable) {
                // один сбойный материал не должен ломать весь синк
            }
            if (index % 5 == 0) {
                onProgress("Перевод: ${index + 1} из ${toTranslate.size}…")
            }
        }

        runCatching { store.pruneArticles(now - 60L * 24 * 3600 * 1000) }
        runCatching { store.pruneTranslations(now - 120L * 24 * 3600 * 1000) }

        prefs.lastSyncAt = now
        val message = "Обновлено: ${fresh.size} новых из $ok источников" +
            if (failed > 0) " ($failed недоступны)" else ""
        prefs.lastSyncInfo = message
        onProgress(message)

        SyncResult(fresh.size, translatedCount, ok, failed, message, true)
    }

    private suspend fun translateAndAnalyze(article: ArticleEntity) {
        val summaryPiece = article.summary.take(700)
        val combined = if (summaryPiece.isBlank()) article.title else article.title + "\n\n" + summaryPiece
        val translated = translator.translate(combined, article.lang)

        val parts = translated.split("\n\n", limit = 2)
        val titleRu: String
        val summaryRu: String
        if (parts.size == 2) {
            titleRu = parts[0].trim()
            summaryRu = parts[1].trim()
        } else {
            val byLine = translated.split("\n", limit = 2)
            if (byLine.size == 2) {
                titleRu = byLine[0].trim()
                summaryRu = byLine[1].trim()
            } else {
                titleRu = translated.trim()
                summaryRu = ""
            }
        }

        val category = Analyzer.detectCategory(
            titleRu,
            summaryRu,
            Analyzer.detectCategory(article.title, article.summary, article.category)
        )

        store.applyAnalysis(
            id = article.id,
            titleRu = titleRu,
            summaryRu = summaryRu,
            score = article.score,
            tags = article.moneyTags,
            category = category
        )
    }

    suspend fun translatePending(limit: Int = 20): Int = withContext(Dispatchers.IO) {
        val pending = store.pendingTranslation(limit)
        var count = 0
        for (article in pending) {
            runCatching {
                translateAndAnalyze(article)
                count++
            }
        }
        count
    }

    fun isRussianText(text: String): Boolean = translator.isRussian(text)

    companion object {
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
