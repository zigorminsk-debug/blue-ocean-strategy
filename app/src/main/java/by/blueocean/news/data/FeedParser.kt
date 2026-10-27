package by.blueocean.news.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Разбор RSS 2.0 / Atom / RDF лент. Jsoup в XML-режиме терпим к кривой разметке,
 * поэтому подходит и для «грязных» фидов.
 */
object FeedParser {

    data class Item(
        val title: String,
        val summary: String,
        val link: String,
        val imageUrl: String,
        val publishedAt: Long,
        val fullText: String = ""
    )

    private val rfc1123: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)

    private val rfc1123Short: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm zzz", Locale.ENGLISH)

    private val isoZone: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]XXX", Locale.ENGLISH)

    fun parse(xml: String, sourceId: String): List<Item> {
        if (xml.isBlank()) return emptyList()
        val doc = try {
            Jsoup.parse(xml, "", Parser.xmlParser())
        } catch (t: Throwable) {
            return emptyList()
        }

        val nodes: List<Element> = when {
            doc.select("entry").isNotEmpty() -> doc.select("entry")
            doc.select("item").isNotEmpty() -> doc.select("item")
            else -> emptyList()
        }

        return nodes.mapNotNull { node ->
            runCatching { parseNode(node, sourceId) }.getOrNull()
        }.filter { it.link.isNotBlank() && it.title.isNotBlank() }
    }

    private fun parseNode(node: Element, sourceId: String): Item {
        val title = clean(node.selectFirst("title")?.text().orEmpty())

        val link = run {
            val direct = node.selectFirst("link")
            val fromText = direct?.text()?.trim().orEmpty()
            val fromHref = direct?.attr("href")?.trim().orEmpty()
            val alternate = node.select("link[rel=alternate]").firstOrNull()?.attr("href").orEmpty()
            val guid = node.selectFirst("guid")?.text()?.trim().orEmpty()
            when {
                alternate.startsWith("http") -> alternate
                fromHref.startsWith("http") -> fromHref
                fromText.startsWith("http") -> fromText
                guid.startsWith("http") -> guid
                else -> ""
            }
        }

        val rawSummary = sequenceOf("description", "summary", "content", "encoded")
            .mapNotNull { tag -> firstText(node, tag) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        val summary = cleanHtml(rawSummary).take(900)

        // Полный текст из ленты (content:encoded часто содержит всю статью)
        val rawContent = sequenceOf("encoded", "content")
            .mapNotNull { tag -> firstText(node, tag) }
            .maxByOrNull { it.length }
            .orEmpty()
        val feedFullText = if (rawContent.length > rawSummary.length + 300) {
            cleanHtml(rawContent).take(12000)
        } else {
            ""
        }

        val image = findImage(node)

        val dateText = sequenceOf("pubDate", "published", "updated", "date", "dc:date")
            .mapNotNull { tag -> firstText(node, tag) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        return Item(
            title = title.take(300),
            summary = summary,
            link = link,
            imageUrl = image,
            publishedAt = parseDate(dateText),
            fullText = feedFullText
        )
    }

    private fun firstText(node: Element, tag: String): String? =
        node.getElementsByTag(tag).firstOrNull()?.text()

    private fun findImage(node: Element): String {
        val mediaTags = listOf("media:content", "media:thumbnail", "enclosure", "image", "thumbnail")
        for (tag in mediaTags) {
            val el = node.getElementsByTag(tag).firstOrNull() ?: continue
            val url = el.attr("url").ifBlank { el.text() }
            if (url.startsWith("http") && looksLikeImage(url)) return url
        }
        // Первая картинка внутри описания
        val html = node.selectFirst("description")?.text()
            ?: node.selectFirst("content")?.text()
            ?: node.selectFirst("encoded")?.text()
        if (!html.isNullOrBlank()) {
            val img = runCatching {
                Jsoup.parse(html).selectFirst("img")?.attr("src").orEmpty()
            }.getOrDefault("")
            if (img.startsWith("http")) return img
        }
        return ""
    }

    private fun looksLikeImage(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") ||
            lower.contains(".webp") || lower.contains(".gif") || lower.contains("image")
    }

    fun cleanHtml(html: String): String {
        if (html.isBlank()) return ""
        val text = runCatching { Jsoup.parse(html).text() }.getOrDefault(html)
        return clean(text)
    }

    private fun clean(text: String): String = text
        .replace("\u00a0", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun parseDate(raw: String): Long {
        val value = raw.trim()
        if (value.isEmpty()) return System.currentTimeMillis()
        // Уже число (Unix time в секундах или миллисекундах)
        value.toLongOrNull()?.let { number ->
            return if (number < 100_000_000_000L) number * 1000 else number
        }
        val candidates = listOf(
            runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() },
            runCatching { OffsetDateTimeCompat(value) },
            runCatching { ZonedDateTime.parse(value, rfc1123).toInstant() },
            runCatching { ZonedDateTime.parse(value, rfc1123Short).toInstant() },
            runCatching { ZonedDateTime.parse(value, isoZone).toInstant() },
            runCatching { Instant.parse(value) }
        )
        for (c in candidates) {
            val instant = c.getOrNull() ?: continue
            return instant.toEpochMilli()
        }
        return System.currentTimeMillis()
    }

    private fun OffsetDateTimeCompat(value: String): Instant =
        java.time.OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
}
