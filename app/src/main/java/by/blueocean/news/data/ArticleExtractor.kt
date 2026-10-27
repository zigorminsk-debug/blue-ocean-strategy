package by.blueocean.news.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

/**
 * Вытаскивает основной текст статьи со страницы источника.
 *
 * Алгоритм без внешних библиотек: убираем служебные блоки, ищем контейнер статьи
 * по семантическим тегам и типичным классам, оцениваем кандидатов по объёму
 * осмысленного текста, собираем абзацы и заголовки.
 */
object ArticleExtractor {

    private val junkSelectors = listOf(
        "script", "style", "noscript", "iframe", "svg", "form", "nav", "header", "footer",
        "aside", "button", "select", "input", "video", "audio", "canvas", "template",
        "[class*=cookie]", "[class*=subscribe]", "[class*=newsletter]", "[class*=paywall]",
        "[class*=social]", "[class*=share]", "[class*=comment]", "[class*=related]",
        "[class*=advert]", "[class*=promo]", "[class*=banner]", "[id*=cookie]",
        "[class*=author-bio]", "[class*=tags]", "[class*=breadcrumb]"
    )

    private val containerSelectors = listOf(
        "[itemprop=articleBody]", "article", "main",
        "[class*=article-body]", "[class*=article__body]", "[class*=articleBody]",
        "[class*=post-content]", "[class*=post__content]", "[class*=entry-content]",
        "[class*=entry__content]", "[class*=content-body]", "[class*=story-body]",
        "[class*=article-content]", "[class*=article_content]", "[class*=prose]",
        "[class*=body-text]", "[class*=single-content]", "[class*=news-text]"
    )

    data class Result(
        val text: String,
        val title: String
    )

    fun extract(html: String, url: String): Result {
        if (html.isBlank()) return Result("", "")
        val doc: Document = runCatching { Jsoup.parse(html, url) }.getOrElse { return Result("", "") }
        val pageTitle = doc.title().trim()

        for (selector in junkSelectors) {
            runCatching { doc.select(selector).remove() }
        }

        var best: Element? = null
        var bestScore = 0
        for (selector in containerSelectors) {
            val candidates = runCatching { doc.select(selector) }.getOrDefault(emptyList())
            for (candidate in candidates) {
                val score = quality(candidate)
                if (score > bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
        }

        val root: Element = if (best != null && bestScore >= 400) {
            best
        } else {
            // Фолбэк: собираем абзацы со всей страницы
            val body = doc.body() ?: return Result("", pageTitle)
            body
        }

        val text = buildText(root)
        return Result(text, pageTitle)
    }

    private fun quality(element: Element): Int {
        var score = 0
        for (paragraph in element.select("p")) {
            val length = paragraph.text().trim().length
            if (length >= 60) score += length
        }
        // Штрафуем блоки, где много ссылок (навигация, анонсы)
        val textLength = element.text().length.coerceAtLeast(1)
        val linkLength = element.select("a").sumOf { it.text().length }
        if (linkLength * 2 > textLength) score /= 3
        return score
    }

    private fun buildText(root: Element): String {
        val builder = StringBuilder()
        val blocks = root.select("h1, h2, h3, p, blockquote, li")
        val seen = mutableSetOf<String>()

        if (blocks.isEmpty()) {
            val raw = root.text()
            return normalize(raw).take(MAX_CHARS)
        }

        for (block in blocks) {
            val text = normalize(block.text())
            if (text.length < 25) continue
            if (!seen.add(text)) continue
            if (isBoilerplate(text)) continue
            builder.append(text).append("\n\n")
            if (builder.length > MAX_CHARS) break
        }

        val result = builder.toString().trim()
        if (result.length >= 300) return result.take(MAX_CHARS)

        // Если абзацев мало — берём весь текст контейнера
        val fallback = normalize(root.text())
        return fallback.take(MAX_CHARS)
    }

    private fun isBoilerplate(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        val markers = listOf(
            "подпис", "subscribe", "newsletter", "cookie", "все права защищены",
            "all rights reserved", "читайте также", "read more", "share this",
            "следите за", "follow us", "реклама", "advertisement"
        )
        if (markers.any { lower.contains(it) } && text.length < 160) return true
        // Отбрасываем строки-меню с большим числом коротких слов без знаков препинания
        if (text.length < 80 && !text.contains('.') && !text.contains('。') && text.split(" ").size < 12) {
            return false
        }
        return false
    }

    private fun normalize(text: String): String = text
        .replace('\u00a0', ' ')
        .replace(SPACED_DIGITS, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Считает примерное время чтения по русскому тексту. */
    fun readingMinutes(text: String): Int = (text.split(" ").size / 180).coerceAtLeast(1)

    private const val MAX_CHARS = 18000

    /** Сноски вида «лесов1,2,3» — отделяем цифры от слова. */
    private val SPACED_DIGITS = Regex("""(?<=[а-яёa-z])(?=\d+([,;]\d+)*[\s.,]|$)""")
}
