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

    /** Минимум источников, в которых встретился термин. */
    private const val MIN_SOURCES = 3

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
        "arxiv", "abstract", "announce", "announced", "announces", "item", "items", "post",
        "posts", "appeared", "while", "across", "type", "types", "insights", "research",
        "researchers", "paper", "papers", "study", "authors", "based", "using", "show",
        "shows", "shown", "according", "including", "added", "says", "said", "could",
        "still", "well", "back", "even", "every", "among", "within", "without", "under",
        "between", "during", "because", "these", "those", "other", "others", "such",
        "points", "point", "comments", "comment", "thread", "reading", "read", "reply",
        "replies", "upvote", "upvotes", "href", "here", "link", "links", "original",
        "full", "story", "stories", "article", "articles", "editor", "via", "per",
        "item", "discuss", "discussion", "submit", "submitted", "karma", "ask", "show",
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
        "вас", "мы", "вы", "они", "она", "оно", "это", "весь", "вся", "всё", "против", "благодаря",
        "чтобы", "далее", "читать", "статьи", "статья", "статей", "баллы", "балл", "комментарии",
        "комментариев", "комментарий", "пост", "поста", "посты", "автор", "авторы", "источник",
        "источника", "подробнее", "например", "которые", "которая", "которое", "которого",
        "является", "являются", "может", "можно", "нужно", "также", "кроме", "более", "менее",
        "самое", "самых", "очень", "будет", "будут", "были", "было", "была", "был", "есть",
        "этого", "этому", "этим", "такой", "такая", "такие", "таких", "него", "нему", "если",
        "чем-то", "что-то", "кто-то", "где-то", "когда-то", "почему", "поэтому", "однако",
        "именно", "просто", "теперь", "затем", "потом", "снова", "опять", "даже", "ведь",
        "больше", "меньше", "свой", "своя", "свою", "своё", "свое", "своих", "своим", "своими",
        "новое", "новая-", "впервые", "определяет", "определяют", "работ", "работа", "работы",
        "работу", "работе", "задач", "задача", "задачи", "сайт", "сайта", "сайте", "сайты",
        "основ", "основа", "основе", "основы", "систем", "система", "системы", "системе",
        "данных", "данные", "данным", "клиент", "клиента", "клиенты", "платформ", "платформа",
        "объявление", "объявления", "миллион", "миллиона", "долларов", "доллара", "впервые",
        "получил", "получила", "получили", "рассказал", "рассказали", "сообщил", "сообщила",
        "показал", "показала", "выяснил", "выяснили", "назвал", "назвали", "представил",
        "представила", "выпустил", "выпустила", "запустил", "запустила", "создал", "создала",
        "стало", "станет", "можно", "нельзя", "нужен", "нужна", "нужны", "важно", "главное",
        "крупнейший", "крупнейшая", "крупных", "крупный", "первый", "первая", "первое",
        "который", "которая", "которое", "которым", "которой", "котором", "создана", "создан",
        "создано", "созданы", "несколько", "место", "места", "генеральный", "генерального",
        "сообщения", "сообщение", "сообщений", "результат", "результаты", "результатов",
        "клиентам", "клиентами", "расширение", "расширения", "комментарий", "мнению", "мнение",
        "данным", "словам", "информации", "информация", "представители", "представитель",
        "руководитель", "основатель", "основателя", "директора", "компанию", "компанией",
        "проект", "проекта", "проекты", "проектов", "сервис", "сервиса", "сервисы", "сервисов",
        "пользователей", "пользователи", "пользователь", "приложение", "приложения",
        "возможность", "возможности", "развитие", "развития", "после", "которые", "которая"
    )

    /**
     * Тренды: русские термины (с лёгкой нормализацией окончаний) и узнаваемые
     * англоязычные бренды/термины. Служебные слова и текст фидов отсеиваются.
     */
    fun words(articles: List<ArticleEntity>, days: Int = 7, limit: Int = 60): List<TrendWord> {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 3600 * 1000L
        val currentFrom = now - days * dayMs
        val previousFrom = now - 2 * days * dayMs

        val current = mutableMapOf<String, Int>()
        val previous = mutableMapOf<String, Int>()
        val sourcesPerWord = mutableMapOf<String, MutableSet<String>>()
        val displayForm = mutableMapOf<String, String>()

        for (article in articles) {
            val text = (article.displayTitle + " " + article.displaySummary).lowercase(Locale.ROOT)
            val isCurrent = article.publishedAt >= currentFrom
            val isPrevious = article.publishedAt in previousFrom until currentFrom
            if (!isCurrent && !isPrevious) continue
            val seen = mutableSetOf<String>()
            for (token in tokenize(text)) {
                if (token.length < 4) continue
                if (token in stopWords) continue
                if (token.all { it.isDigit() }) continue
                if (!isMeaningful(token)) continue
                val key = normalize(token)
                if (!seen.add(key)) continue
                if (isCurrent) {
                    current[key] = (current[key] ?: 0) + 1
                    sourcesPerWord.getOrPut(key) { mutableSetOf() }.add(article.sourceId)
                    val shown = displayForm[key]
                    if (shown == null || token.length < shown.length) displayForm[key] = token
                } else {
                    previous[key] = (previous[key] ?: 0) + 1
                }
            }
        }

        return current.entries
            // Термин должен встречаться в материалах разных источников: так отсеивается
            // служебный текст конкретной ленты (arXiv, Hacker News и т.п.)
            .filter { (sourcesPerWord[it.key]?.size ?: 0) >= MIN_SOURCES }
            .sortedByDescending { it.value }
            .take(limit)
            .map {
                TrendWord(
                    word = displayForm[it.key] ?: it.key,
                    count = it.value,
                    previous = previous[it.key] ?: 0
                )
            }
    }

    /** Оставляем русские слова и понятные всем ИТ-термины/бренды на латинице. */
    private fun isMeaningful(token: String): Boolean {
        val cyrillic = token.count { it in '\u0400'..'\u04FF' }
        if (cyrillic >= token.length / 2) return true
        return token in latinAllowlist
    }

    private val latinAllowlist = setOf(
        "openai", "anthropic", "nvidia", "google", "apple", "amazon", "microsoft", "meta",
        "tesla", "spacex", "openai", "chatgpt", "gpt", "llm", "ai", "agi", "gpu", "cpu",
        "npu", "tpu", "chip", "chips", "cloud", "saas", "api", "startup", "startups",
        "crypto", "bitcoin", "ethereum", "blockchain", "stablecoin", "robot", "robots",
        "robotics", "drone", "drones", "quantum", "semiconductor", "datacenter", "aws",
        "azure", "ios", "android", "linux", "windows", "python", "rust", "kotlin",
        "github", "gitlab", "figma", "notion", "slack", "zoom", "tiktok", "youtube",
        "iphone", "ipad", "macbook", "pixel", "gemini", "claude", "llama", "mistral",
        "deepseek", "qwen", "hugging", "pytorch", "tensorflow", "kubernetes", "docker",
        "postgres", "mysql", "redis", "kafka", "saas"
    )

    /** Лёгкая нормализация русских окончаний, чтобы «интеллекта» и «интеллект» считались вместе. */
    fun normalize(token: String): String {
        if (token.any { it in '\u0400'..'\u04FF' }) {
            for (ending in ruEndings) {
                if (token.length - ending.length < 5 || !token.endsWith(ending)) continue
                val base = token.dropLast(ending.length)
                // «данные» → «данны», «акции» → «акци» и подобные обрубки не нужны
                if (base.length >= 2 && base[base.length - 1] == base[base.length - 2]) continue
                if (ending.length == 1 && base.last() in "ыи") continue
                return base
            }
        }
        return token
    }

    private val ruEndings = listOf(
        "иями", "ями", "ами", "ыми", "ими", "ого", "его", "ому", "ему", "ыми", "их", "ых",
        "ая", "яя", "ое", "ее", "ые", "ие", "ую", "юю", "ов", "ев", "ам", "ям", "ах", "ях",
        "ом", "ем", "ой", "ей", "ий", "ый", "ия", "ии", "ью", "а", "я", "ы", "и", "о", "е", "у", "ю"
    )

    fun tokenize(text: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        for (ch in text) {
            if (ch.isLetter()) {
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
