package by.blueocean.news.data

/**
 * Материал ленты. Хранится в SQLite.
 *
 * titleRu/summaryRu — перевод заголовка и описания,
 * fullText/fullTextRu — полный текст статьи и его перевод (для чтения внутри приложения).
 */
data class ArticleEntity(
    val id: Long = 0,
    val title: String,
    val titleRu: String = "",
    val summary: String = "",
    val summaryRu: String = "",
    val link: String,
    val imageUrl: String = "",
    val sourceId: String,
    val sourceName: String,
    val region: String,
    val category: String,
    val lang: String,
    val publishedAt: Long,
    val fetchedAt: Long,
    val score: Int = 0,
    val moneyTags: String = "",
    val isRead: Boolean = false,
    val isSaved: Boolean = false,
    val fullText: String = "",
    val fullTextRu: String = ""
) {
    val displayTitle: String get() = titleRu.ifBlank { title }
    val displaySummary: String get() = summaryRu.ifBlank { summary }
    val hasTranslation: Boolean get() = titleRu.isNotBlank()
    val hasFullTextTranslation: Boolean get() = fullTextRu.isNotBlank()
}
