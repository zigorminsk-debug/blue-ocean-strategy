package by.blueocean.news

import by.blueocean.news.data.Analyzer
import by.blueocean.news.data.ArticleExtractor
import by.blueocean.news.data.ArticleEntity
import by.blueocean.news.data.Directions
import by.blueocean.news.data.FeedParser
import by.blueocean.news.data.Sources
import by.blueocean.news.data.Trends
import by.blueocean.news.ui.Format
import by.blueocean.news.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedParserTest {

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
          <channel>
            <title>Tech Feed</title>
            <item>
              <title>OpenAI raises ${'$'}6B in new round</title>
              <link>https://example.com/ai-round</link>
              <description>&lt;p&gt;The &lt;b&gt;startup&lt;/b&gt; raised a Series C round at a ${'$'}40B valuation.&lt;/p&gt;</description>
              <pubDate>Mon, 27 Oct 2025 10:00:00 GMT</pubDate>
              <media:content url="https://example.com/cover.jpg" type="image/jpeg" />
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private val atom = """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Atom Feed</title>
          <entry>
            <title>Robot startup launches warehouse pilot</title>
            <link rel="alternate" href="https://example.org/robots"/>
            <summary>A robotics company started a pilot project with logistics operators.</summary>
            <published>2025-10-25T08:30:00Z</published>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun parsesRssItem() {
        val items = FeedParser.parse(rss, "test")
        assertEquals(1, items.size)
        val item = items.first()
        assertTrue(item.title.contains("OpenAI"))
        assertEquals("https://example.com/ai-round", item.link)
        assertTrue(item.summary.contains("Series C"))
        assertFalse(item.summary.contains("<b>"))
        assertEquals("https://example.com/cover.jpg", item.imageUrl)
        assertTrue(item.publishedAt > 0)
    }

    @Test
    fun parsesAtomEntry() {
        val items = FeedParser.parse(atom, "test")
        assertEquals(1, items.size)
        assertEquals("https://example.org/robots", items.first().link)
        assertTrue(items.first().title.contains("Robot"))
    }

    @Test
    fun extractsFullTextFromEncodedContent() {
        val longText = (1..40).joinToString(" ") { "Предложение номер $it про технологии и деньги." }
        val xml = """
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <item>
                  <title>Big story</title>
                  <link>https://example.com/big</link>
                  <description>Короткий анонс</description>
                  <content:encoded><![CDATA[<p>$longText</p>]]></content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val item = FeedParser.parse(xml, "test").first()
        assertTrue("full text must be extracted, got ${item.fullText.length}", item.fullText.length > 500)
        assertFalse(item.fullText.contains("<p>"))
    }

    @Test
    fun brokenFeedDoesNotThrow() {
        assertTrue(FeedParser.parse("<html><body>oops", "test").isEmpty())
        assertTrue(FeedParser.parse("", "test").isEmpty())
    }

    @Test
    fun parsesNumericTimestamp() {
        val value = FeedParser.parseDate("1761559200")
        assertEquals(1761559200000L, value)
    }
}

class AnalyzerTest {

    @Test
    fun detectsAiCategory() {
        val category = Analyzer.detectCategory(
            "OpenAI запустила нового ИИ-агента",
            "Нейросеть автоматизирует работу с документами",
            "Технологии"
        )
        assertEquals("ИИ", category)
    }

    @Test
    fun detectsFundingSignals() {
        val (score, tags) = Analyzer.moneyScore(
            "Стартап привлёк ${'$'}40 млн в раунде Series B",
            "Инвесторы оценили компанию в ${'$'}300 млн, выручка выросла вдвое",
            1f
        )
        assertTrue("score must be positive, got $score", score > 0)
        assertTrue("tags: $tags", tags.contains("Раунд"))
    }

    @Test
    fun normalNewsScoresLow() {
        val (score, _) = Analyzer.moneyScore("Вышло обновление приложения", "Мелкие исправления", 1f)
        assertTrue("score must be small, got $score", score <= 6)
    }
}

class TrendsTest {

    private fun article(
        id: Long,
        title: String,
        publishedAt: Long,
        sourceId: String = "test"
    ) = ArticleEntity(
        id = id,
        title = title,
        summary = "",
        link = "https://example.com/$id",
        sourceId = sourceId,
        sourceName = "Test $sourceId",
        region = "Весь мир",
        category = "Технологии",
        lang = "en",
        publishedAt = publishedAt,
        fetchedAt = publishedAt
    )

    @Test
    fun countsWordsAcrossPeriods() {
        val now = System.currentTimeMillis()
        val day = 24 * 3600 * 1000L
        val list = listOf(
            article(1, "Робот научился готовить кофе", now, "a"),
            article(2, "Роботы заменили курьеров в доставке", now, "b"),
            article(3, "Робот-манипулятор на складе", now, "c"),
            article(4, "Робот в прошлой неделе", now - 10 * day, "d")
        )
        val words = Trends.words(list, days = 7, limit = 50)
        val robot = words.firstOrNull { it.word.startsWith("робот") }
        assertNotNull("робот must be in trends", robot)
        assertTrue("count: ${robot!!.count}", robot.count >= 3)
        assertTrue("previous: ${robot.previous}", robot.previous >= 1)
    }

    @Test
    fun findsOpportunitiesByScore() {
        val now = System.currentTimeMillis()
        val list = listOf(
            article(1, "Стартап привлёк раунд", now).copy(score = 30),
            article(2, "Обычная новость", now).copy(score = 2)
        )
        val opportunities = Trends.opportunities(list, minScore = 8)
        assertEquals(1, opportunities.size)
        assertEquals(30, opportunities.first().score)
    }
}

class TrendsFilterTest {

    private fun article(id: Long, title: String, sourceId: String) = ArticleEntity(
        id = id,
        title = title,
        summary = "",
        link = "https://example.com/t$id",
        sourceId = sourceId,
        sourceName = "Src $sourceId",
        region = "Весь мир",
        category = "Технологии",
        lang = "ru",
        publishedAt = System.currentTimeMillis(),
        fetchedAt = System.currentTimeMillis()
    )

    @Test
    fun ignoresWordsFromSingleSource() {
        val list = listOf(
            article(1, "abstract announcement arxiv", "arxiv"),
            article(2, "abstract announcement arxiv", "arxiv2"),
            article(3, "искусственный интеллект растёт", "a"),
            article(4, "искусственный интеллект и рынок", "b"),
            article(5, "интеллект в промышленности", "c")
        )
        val words = Trends.words(list, days = 7, limit = 50).map { it.word }
        assertFalse("abstract must be filtered: $words", words.contains("abstract"))
        assertTrue("интеллект expected in: $words", words.any { it.startsWith("интеллект") })
    }

    @Test
    fun normalizesRussianEndings() {
        assertEquals(Trends.normalize("интеллекта"), Trends.normalize("интеллект"))
        assertEquals(Trends.normalize("стартапы"), Trends.normalize("стартап"))
    }
}

class DirectionsTest {

    @Test
    fun matchesAiAgentsDirection() {
        val matched = Directions.match("OpenAI выпустил агента для автоматизации workflow")
        assertTrue(matched.any { it.id == "ai_agents" })
    }

    @Test
    fun allDirectionsHaveContent() {
        assertTrue(Directions.ALL.size >= 15)
        Directions.ALL.forEach { direction ->
            assertTrue(direction.keywords.isNotEmpty())
            assertTrue(direction.why.length > 40)
            assertTrue(direction.howToEarn.length > 20)
            assertTrue(direction.entry.length > 10)
        }
    }
}

class SourcesTest {

    @Test
    fun sourcesAreUniqueAndValid() {
        assertTrue("sources: ${Sources.ALL.size}", Sources.ALL.size >= 40)
        assertEquals(Sources.ALL.size, Sources.ALL.map { it.id }.toSet().size)
        assertEquals(Sources.ALL.size, Sources.ALL.map { it.url }.toSet().size)
        Sources.ALL.forEach { source ->
            assertTrue(source.url, source.url.startsWith("https://") || source.url.startsWith("http://"))
        }
    }
}

class ArticleExtractorTest {

    @Test
    fun extractsArticleBodyFromHtml() {
        val paragraph = (1..12).joinToString(" ") { "Это содержательный абзац номер $it о рынке технологий." }
        val html = """
            <html><head><title>Заголовок статьи</title></head>
            <body>
              <nav><a href="/">Главная</a><a href="/news">Новости</a></nav>
              <article>
                <h1>Заголовок статьи</h1>
                <p>$paragraph</p>
                <p>$paragraph</p>
                <p>$paragraph</p>
                <div class="share">Поделиться</div>
              </article>
              <footer>Все права защищены</footer>
            </body></html>
        """.trimIndent()
        val result = ArticleExtractor.extract(html, "https://example.com/a")
        assertTrue("text: ${result.text.take(200)}", result.text.length > 300)
        assertTrue(result.text.contains("содержательный абзац"))
        assertEquals("Заголовок статьи", result.title)
    }

    @Test
    fun emptyHtmlGivesEmptyText() {
        assertEquals("", ArticleExtractor.extract("", "https://example.com").text)
    }

    @Test
    fun chunkSplitsLongText() {
        val translatorText = (1..60).joinToString(" ") { "Предложение $it." }
        val chunks = TranslatorChunkHelper.chunk(translatorText, 100)
        assertTrue(chunks.size > 5)
        chunks.forEach { assertTrue("chunk too long: ${it.length}", it.length <= 130) }
    }
}

class TranslatorChunkHelper {
    companion object {
        fun chunk(text: String, max: Int): List<String> {
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
    }
}

class UpdateVersionTest {

    @Test
    fun comparesVersions() {
        assertTrue(UpdateManager.isNewer("1.0.12", "1.0.11"))
        assertTrue(UpdateManager.isNewer("1.1.0", "1.0.99"))
        assertTrue(UpdateManager.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateManager.isNewer("1.0.2", "1.0.2"))
        assertFalse(UpdateManager.isNewer("1.0.1", "1.0.2"))
    }
}

class FormatTest {

    @Test
    fun formatsRelativeTime() {
        val now = System.currentTimeMillis()
        assertEquals("только что", Format.ago(now - 10_000, now))
        assertEquals("2 мин назад", Format.ago(now - 120_000, now))
        assertEquals("3 ч назад", Format.ago(now - 3 * 3_600_000, now))
    }
}
