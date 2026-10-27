package by.blueocean.news

import by.blueocean.news.data.Analyzer
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

    private fun article(id: Long, title: String, publishedAt: Long) = ArticleEntity(
        id = id,
        title = title,
        summary = "",
        link = "https://example.com/$id",
        sourceId = "test",
        sourceName = "Test",
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
            article(1, "Робот научился готовить кофе", now),
            article(2, "Робот заменил курьера в Доставке", now - 10 * day)
        )
        val words = Trends.words(list, days = 7, limit = 50)
        val robot = words.firstOrNull { it.word.startsWith("робот") }
        assertNotNull("робот must be in trends", robot)
        assertEquals(1, robot!!.count)
        assertEquals(1, robot.previous)
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
