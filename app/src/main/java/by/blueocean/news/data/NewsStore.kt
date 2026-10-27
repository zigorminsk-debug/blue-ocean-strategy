package by.blueocean.news.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.util.Locale

/**
 * Локальное хранилище на SQLite без сторонних ORM: быстрее собирается,
 * меньше зависимостей и никакой кодогенерации.
 */
class NewsStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE articles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                titleRu TEXT NOT NULL DEFAULT '',
                summary TEXT NOT NULL DEFAULT '',
                summaryRu TEXT NOT NULL DEFAULT '',
                link TEXT NOT NULL UNIQUE,
                imageUrl TEXT NOT NULL DEFAULT '',
                sourceId TEXT NOT NULL,
                sourceName TEXT NOT NULL,
                region TEXT NOT NULL,
                category TEXT NOT NULL,
                lang TEXT NOT NULL,
                publishedAt INTEGER NOT NULL,
                fetchedAt INTEGER NOT NULL,
                score INTEGER NOT NULL DEFAULT 0,
                moneyTags TEXT NOT NULL DEFAULT '',
                isRead INTEGER NOT NULL DEFAULT 0,
                isSaved INTEGER NOT NULL DEFAULT 0,
                fullText TEXT NOT NULL DEFAULT '',
                fullTextRu TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_articles_published ON articles(publishedAt)")
        db.execSQL("CREATE INDEX idx_articles_score ON articles(score)")
        db.execSQL(
            """
            CREATE TABLE translations (
                hash TEXT PRIMARY KEY,
                result TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE articles ADD COLUMN fullText TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE articles ADD COLUMN fullTextRu TEXT NOT NULL DEFAULT ''")
        }
    }

    // ─────────────── Статьи ───────────────

    fun insertNew(items: List<ArticleEntity>): List<ArticleEntity> {
        if (items.isEmpty()) return emptyList()
        val db = writableDatabase
        val inserted = mutableListOf<ArticleEntity>()
        db.beginTransaction()
        try {
            for (item in items) {
                val id = db.insertWithOnConflict(TABLE_ARTICLES, null, toValues(item), SQLiteDatabase.CONFLICT_IGNORE)
                if (id > 0) inserted.add(item.copy(id = id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    fun latest(limit: Int = 1500): List<ArticleEntity> {
        val result = mutableListOf<ArticleEntity>()
        readableDatabase.rawQuery(
            "SELECT * FROM articles ORDER BY publishedAt DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor))
            }
        }
        return result
    }

    fun since(timestamp: Long): List<ArticleEntity> {
        val result = mutableListOf<ArticleEntity>()
        readableDatabase.rawQuery(
            "SELECT * FROM articles WHERE publishedAt >= ? ORDER BY publishedAt DESC",
            arrayOf(timestamp.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(fromCursor(cursor))
        }
        return result
    }

    fun byLink(link: String): ArticleEntity? {
        readableDatabase.rawQuery(
            "SELECT * FROM articles WHERE link = ? LIMIT 1",
            arrayOf(link)
        ).use { cursor ->
            return if (cursor.moveToFirst()) fromCursor(cursor) else null
        }
    }

    fun byId(id: Long): ArticleEntity? {
        readableDatabase.rawQuery(
            "SELECT * FROM articles WHERE id = ? LIMIT 1",
            arrayOf(id.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) fromCursor(cursor) else null
        }
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM articles", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /** Сколько материалов ещё без перевода заголовка. */
    fun pendingTranslationCount(): Int = countOf(
        "SELECT COUNT(*) FROM articles WHERE lang <> 'ru' AND titleRu = ''"
    )

    /** Сколько материалов с непереведённым описанием (заголовок уже переведён). */
    fun pendingSummaryCount(): Int = countOf(
        "SELECT COUNT(*) FROM articles WHERE lang <> 'ru' AND titleRu <> '' AND summary <> '' AND summaryRu = ''"
    )

    fun translatedCount(): Int = countOf(
        "SELECT COUNT(*) FROM articles WHERE lang = 'ru' OR titleRu <> ''"
    )

    private fun countOf(sql: String): Int {
        readableDatabase.rawQuery(sql, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun pendingTranslation(limit: Int): List<ArticleEntity> {
        val result = mutableListOf<ArticleEntity>()
        readableDatabase.rawQuery(
            """
            SELECT * FROM articles
            WHERE lang <> 'ru' AND (titleRu = '' OR (summary <> '' AND summaryRu = ''))
            ORDER BY (score >= 8) DESC, publishedAt DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(fromCursor(cursor))
        }
        return result
    }

    fun markRead(id: Long) {
        writableDatabase.execSQL("UPDATE articles SET isRead = 1 WHERE id = ?", arrayOf(id))
    }

    fun setSaved(id: Long, saved: Boolean) {
        writableDatabase.execSQL(
            "UPDATE articles SET isSaved = ? WHERE id = ?",
            arrayOf(if (saved) 1 else 0, id)
        )
    }

    fun applyAnalysis(id: Long, titleRu: String, summaryRu: String, score: Int, tags: String, category: String) {
        val values = ContentValues().apply {
            put("titleRu", titleRu)
            put("summaryRu", summaryRu)
            put("score", score)
            put("moneyTags", tags)
            put("category", category)
        }
        writableDatabase.update(TABLE_ARTICLES, values, "id = ?", arrayOf(id.toString()))
    }

    fun applyTitleTranslation(id: Long, titleRu: String) {
        val values = ContentValues().apply { put("titleRu", titleRu) }
        writableDatabase.update(TABLE_ARTICLES, values, "id = ?", arrayOf(id.toString()))
    }

    fun applySummaryTranslation(id: Long, summaryRu: String) {
        val values = ContentValues().apply { put("summaryRu", summaryRu) }
        writableDatabase.update(TABLE_ARTICLES, values, "id = ?", arrayOf(id.toString()))
    }

    fun applyCategory(id: Long, category: String) {
        val values = ContentValues().apply { put("category", category) }
        writableDatabase.update(TABLE_ARTICLES, values, "id = ?", arrayOf(id.toString()))
    }

    fun clearFullText(id: Long) {
        writableDatabase.execSQL(
            "UPDATE articles SET fullText = '', fullTextRu = '' WHERE id = ?",
            arrayOf(id)
        )
    }

    fun setFullText(id: Long, original: String, translated: String) {
        val values = ContentValues().apply {
            put("fullText", original)
            put("fullTextRu", translated)
        }
        writableDatabase.update(TABLE_ARTICLES, values, "id = ?", arrayOf(id.toString()))
    }

    fun pruneArticles(before: Long) {
        writableDatabase.execSQL(
            "DELETE FROM articles WHERE publishedAt < ? AND isSaved = 0",
            arrayOf(before)
        )
    }

    // ─────────────── Кэш переводов ───────────────

    fun translationGet(text: String): String? {
        readableDatabase.rawQuery(
            "SELECT result FROM translations WHERE hash = ? LIMIT 1",
            arrayOf(hashOf(text))
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    fun translationPut(text: String, result: String) {
        val values = ContentValues().apply {
            put("hash", hashOf(text))
            put("result", result)
            put("createdAt", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(TABLE_TRANSLATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun pruneTranslations(before: Long) {
        writableDatabase.execSQL("DELETE FROM translations WHERE createdAt < ?", arrayOf(before))
    }

    private fun toValues(item: ArticleEntity): ContentValues = ContentValues().apply {
        put("title", item.title)
        put("titleRu", item.titleRu)
        put("summary", item.summary)
        put("summaryRu", item.summaryRu)
        put("link", item.link)
        put("imageUrl", item.imageUrl)
        put("sourceId", item.sourceId)
        put("sourceName", item.sourceName)
        put("region", item.region)
        put("category", item.category)
        put("lang", item.lang)
        put("publishedAt", item.publishedAt)
        put("fetchedAt", item.fetchedAt)
        put("score", item.score)
        put("moneyTags", item.moneyTags)
        put("isRead", if (item.isRead) 1 else 0)
        put("isSaved", if (item.isSaved) 1 else 0)
        put("fullText", item.fullText)
        put("fullTextRu", item.fullTextRu)
    }

    private fun fromCursor(cursor: Cursor): ArticleEntity = ArticleEntity(
        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
        titleRu = cursor.getString(cursor.getColumnIndexOrThrow("titleRu")),
        summary = cursor.getString(cursor.getColumnIndexOrThrow("summary")),
        summaryRu = cursor.getString(cursor.getColumnIndexOrThrow("summaryRu")),
        link = cursor.getString(cursor.getColumnIndexOrThrow("link")),
        imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("imageUrl")),
        sourceId = cursor.getString(cursor.getColumnIndexOrThrow("sourceId")),
        sourceName = cursor.getString(cursor.getColumnIndexOrThrow("sourceName")),
        region = cursor.getString(cursor.getColumnIndexOrThrow("region")),
        category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
        lang = cursor.getString(cursor.getColumnIndexOrThrow("lang")),
        publishedAt = cursor.getLong(cursor.getColumnIndexOrThrow("publishedAt")),
        fetchedAt = cursor.getLong(cursor.getColumnIndexOrThrow("fetchedAt")),
        score = cursor.getInt(cursor.getColumnIndexOrThrow("score")),
        moneyTags = cursor.getString(cursor.getColumnIndexOrThrow("moneyTags")),
        isRead = cursor.getInt(cursor.getColumnIndexOrThrow("isRead")) == 1,
        isSaved = cursor.getInt(cursor.getColumnIndexOrThrow("isSaved")) == 1,
        fullText = cursor.getString(cursor.getColumnIndexOrThrow("fullText")),
        fullTextRu = cursor.getString(cursor.getColumnIndexOrThrow("fullTextRu"))
    )

    private fun hashOf(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    companion object {
        private const val DB_NAME = "blue_ocean.db"
        private const val DB_VERSION = 2
        private const val TABLE_ARTICLES = "articles"
        private const val TABLE_TRANSLATIONS = "translations"

        @Volatile
        private var instance: NewsStore? = null

        fun get(context: Context): NewsStore = instance ?: synchronized(this) {
            instance ?: NewsStore(context.applicationContext).also { instance = it }
        }
    }
}
