package by.blueocean.news.data

import java.util.Locale

data class TrendWord(
    val word: String,
    val count: Int,
    val previous: Int
) {
    val delta: Int get() = count - previous
    val isHot: Boolean get() = count >= 4 && count > previous
}

data class DirectionStat(
    val direction: Direction,
    val count: Int,
    val score: Int,
    val latest: ArticleEntity?
)

/**
 * Расчёт трендов «на лету»: частотность терминов и сопоставление с направлениями.
 */
object Trends {

    private val stopWords = setOf(
        // английские
        "the", "and", "for", "with", "that", "this", "from", "have", "has", "will", "are", "was",
        "were", "been", "but", "not", "you", "your", "its", "their", "our", "his", "her", "they",
        "them", "than", "then", "into", "over", "more", "most", "some", "such", "can", "could",
        "should", "would", "about", "after", "before", "here", "there", "when", "where", "which",
        "who", "what", "why", "how", "all", "any", "new", "now", "out", "get", "got", "just",
        "like", "make", "made", "use", "used", "using", "way", "ways", "also", "even", "only",
        "very", "much", "many", "may", "might", "must", "need", "one", "two", "three", "first",
        "last", "next", "said", "says", "say", "according", "report", "reports", "reported",
        "company", "companies", "business", "technology", "tech", "news", "week", "year", "years",
        "day", "days", "time", "times", "people", "world", "global", "million", "billion",
        "https", "http", "www", "com", "amp", "utm", "source", "read", "image", "photo",
        "getty", "shutterstock", "reuters", "bloomberg", "techcrunch",
        // русские
        "это", "как", "что", "для", "или", "при", "над", "под", "без", "про", "через", "между",
        "также", "более", "менее", "самых", "самый", "самая", "самое", "который", "которая",
        "которые", "которых", "его", "её", "ее", "их", "этот", "эта", "эти", "того", "тому",
        "всех", "все", "всё", "уже", "ещё", "еще", "будет", "будут", "быть", "был", "была",
        "было", "были", "есть", "можно", "нужно", "может", "могут", "после", "перед", "только",
        "очень", "много", "другой", "другие", "сейчас", "сегодня", "вчера", "завтра", "года",
        "год", "лет", "день", "дня", "дней", "раз", "раза", "компания", "компании", "компаний",
        "рынок", "рынка", "новый", "новая", "новые", "новых", "технологии", "технология",
        "технологий", "мира", "мир", "стал", "стала", "стали", "свои", "своих", "себя", "этом",
        "этого", "этой", "том", "тем", "чем", "кто", "где", "когда", "почему", "зачем", "из-за",
        "кроме", "среди", "около", "почти", "около", "всё", "него", "нее", "неё", "них", "нас",
        "вас", "мы", "вы", "они", "она", "оно", "это", "весь", "вся", "всё", "против", "благодаря"
    )

    fun words(articles: List<ArticleEntity>, days: Int = 7, limit: Int = 60): List<TrendWord> {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 3600 * 1000L
        val currentFrom = now - days * dayMs
        val previousFrom = now - 2 * days * dayMs

        val current = mutableMapOf<String, Int>()
        val previous = mutableMapOf<String, Int>()

        for (article in articles) {
            val text = (article.displayTitle + " " + article.displaySummary).lowercase(Locale.ROOT)
            val tokens = tokenize(text)
            val isCurrent = article.publishedAt >= currentFrom
            val isPrevious = article.publishedAt in previousFrom until currentFrom
            if (!isCurrent && !isPrevious) continue
            val seen = mutableSetOf<String>()
            for (token in tokens) {
                if (token.length < 4) continue
                if (token in stopWords) continue
                if (token.all { it.isDigit() }) continue
                if (!seen.add(token)) continue
                if (isCurrent) current[token] = (current[token] ?: 0) + 1
                else previous[token] = (previous[token] ?: 0) + 1
            }
        }

        return current.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { TrendWord(it.key, it.value, previous[it.key] ?: 0) }
    }

    fun tokenize(text: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        for (ch in text) {
            if (ch.isLetter() || ch == '-') {
                sb.append(ch)
            } else if (sb.isNotEmpty()) {
                result.add(sb.toString().trim('-'))
                sb.setLength(0)
            }
        }
        if (sb.isNotEmpty()) result.add(sb.toString().trim('-'))
        return result.filter { it.isNotBlank() }
    }

    fun directionStats(articles: List<ArticleEntity>, days: Int = 14): List<DirectionStat> {
        val from = System.currentTimeMillis() - days * 24 * 3600 * 1000L
        val recent = articles.filter { it.publishedAt >= from }
        val stats = mutableListOf<DirectionStat>()
        for (direction in Directions.ALL) {
            val matched = recent.filter { article ->
                val text = (article.displayTitle + " " + article.displaySummary).lowercase(Locale.ROOT)
                direction.keywords.count { text.contains(it) } >= 2
            }
            if (matched.isEmpty()) continue
            stats.add(
                DirectionStat(
                    direction = direction,
                    count = matched.size,
                    score = matched.sumOf { it.score },
                    latest = matched.maxByOrNull { it.publishedAt }
                )
            )
        }
        return stats.sortedWith(
            compareByDescending<DirectionStat> { it.count }.thenByDescending { it.score }
        )
    }

    fun opportunities(articles: List<ArticleEntity>, minScore: Int = 8, limit: Int = 40): List<ArticleEntity> =
        articles.filter { it.score >= minScore }.sortedByDescending { it.score }.take(limit)
}
