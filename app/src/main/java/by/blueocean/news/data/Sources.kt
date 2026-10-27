package by.blueocean.news.data

/**
 * Источник новостей — RSS/Atom лента.
 *
 * @param lang язык оригинала (для перевода)
 * @param region регион/страна
 * @param category базовая категория (уточняется анализатором)
 */
data class Source(
    val id: String,
    val name: String,
    val url: String,
    val lang: String = "en",
    val region: String = "Global",
    val category: String = "Технологии",
    val weight: Float = 1f
)

object Sources {

    const val REGION_GLOBAL = "Весь мир"
    const val REGION_US = "США"
    const val REGION_EU = "Европа"
    const val REGION_ASIA = "Азия"
    const val REGION_CIS = "Россия и СНГ"
    const val REGION_AFRICA = "Африка"
    const val REGION_LATAM = "Латинская Америка"
    const val REGION_MENA = "Ближний Восток"

    val ALL: List<Source> = listOf(
        // ─────────────── Мировые IT-издания ───────────────
        Source("techcrunch", "TechCrunch", "https://techcrunch.com/feed/", "en", REGION_US, "Стартапы", 1.2f),
        Source("tc_startups", "TechCrunch Startups", "https://techcrunch.com/category/startups/feed/", "en", REGION_US, "Финансирование", 1.2f),
        Source("tc_ai", "TechCrunch AI", "https://techcrunch.com/category/artificial-intelligence/feed/", "en", REGION_US, "ИИ", 1.2f),
        Source("tc_venture", "TechCrunch Venture", "https://techcrunch.com/category/venture/feed/", "en", REGION_US, "Финансирование", 1.2f),
        Source("venturebeat", "VentureBeat", "https://venturebeat.com/feed/", "en", REGION_US, "Технологии", 1.0f),
        Source("vb_ai", "VentureBeat AI", "https://venturebeat.com/category/ai/feed/", "en", REGION_US, "ИИ", 1.0f),
        Source("verge", "The Verge", "https://www.theverge.com/rss/index.xml", "en", REGION_US, "Продукты", 1.0f),
        Source("ars", "Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "en", REGION_US, "Технологии", 1.0f),
        Source("wired", "WIRED", "https://www.wired.com/feed/rss", "en", REGION_US, "Технологии", 0.9f),
        Source("engadget", "Engadget", "https://www.engadget.com/rss.xml", "en", REGION_US, "Железо", 0.9f),
        Source("zdnet", "ZDNET", "https://www.zdnet.com/news/rss.xml", "en", REGION_US, "Технологии", 0.8f),
        Source("mit_tr", "MIT Technology Review", "https://www.technologyreview.com/feed/", "en", REGION_GLOBAL, "Наука", 1.1f),
        Source("ieee", "IEEE Spectrum", "https://spectrum.ieee.org/feeds/feed.rss", "en", REGION_GLOBAL, "Наука", 1.0f),
        Source("bloomberg_tech", "Bloomberg Technology", "https://feeds.bloomberg.com/technology/news.rss", "en", REGION_GLOBAL, "Технологии", 1.1f),
        Source("hn", "Hacker News", "https://hnrss.org/frontpage", "en", REGION_GLOBAL, "Технологии", 0.7f),
        Source("hn_show", "Show HN (новые проекты)", "https://hnrss.org/show", "en", REGION_GLOBAL, "Стартапы", 0.8f),
        Source("producthunt", "Product Hunt", "https://www.producthunt.com/feed", "en", REGION_GLOBAL, "Продукты", 0.9f),

        // ─────────────── Деньги, венчур, сделки ───────────────
        Source("crunchbase", "Crunchbase News", "https://news.crunchbase.com/feed/", "en", REGION_US, "Финансирование", 1.3f),
        Source("finsmes", "FinSMEs", "https://www.finsmes.com/feed", "en", REGION_GLOBAL, "Финансирование", 1.0f),
        Source("pehub", "PE Hub", "https://www.pehub.com/feed/", "en", REGION_US, "Финансирование", 0.9f),
        Source("a16z", "Andreessen Horowitz", "https://a16z.com/feed/", "en", REGION_US, "Идеи", 1.0f),
        Source("yc_blog", "Y Combinator", "https://www.ycombinator.com/blog/rss/", "en", REGION_US, "Идеи", 1.0f),
        Source("firstround", "First Round Review", "https://review.firstround.com/feed.xml", "en", REGION_US, "Идеи", 0.8f),
        Source("cbinsights", "CB Insights", "https://www.cbinsights.com/research/feed/", "en", REGION_GLOBAL, "Идеи", 1.0f),
        Source("thehustle", "The Hustle", "https://thehustle.co/feed/", "en", REGION_US, "Идеи", 0.8f),

        // ─────────────── Европа ───────────────
        Source("sifted", "Sifted (EU)", "https://sifted.eu/feed", "en", REGION_EU, "Стартапы", 1.1f),
        Source("techeu", "Tech.eu", "https://tech.eu/feed/", "en", REGION_EU, "Финансирование", 1.0f),
        Source("eustartups", "EU-Startups", "https://www.eu-startups.com/feed/", "en", REGION_EU, "Стартапы", 1.0f),

        // ─────────────── Азия ───────────────
        Source("techinasia", "Tech in Asia", "https://www.techinasia.com/feed", "en", REGION_ASIA, "Стартапы", 1.0f),
        Source("krasia", "KrASIA", "https://kr-asia.com/feed", "en", REGION_ASIA, "Стартапы", 0.9f),
        Source("technode", "TechNode (China)", "https://technode.com/feed/", "en", REGION_ASIA, "Стартапы", 0.9f),
        Source("inc42", "Inc42 (India)", "https://inc42.com/feed/", "en", REGION_ASIA, "Стартапы", 1.0f),
        Source("yourstory", "YourStory (India)", "https://yourstory.com/feed", "en", REGION_ASIA, "Стартапы", 0.8f),
        Source("techwireasia", "TechWire Asia", "https://techwireasia.com/feed/", "en", REGION_ASIA, "Технологии", 0.8f),
        Source("koreatechdesk", "KoreaTechDesk", "https://koreatechdesk.com/feed/", "en", REGION_ASIA, "Стартапы", 0.8f),
        Source("japantimes", "The Japan Times", "https://www.japantimes.co.jp/feed/", "en", REGION_ASIA, "Технологии", 0.7f),

        // ─────────────── Африка, Латинская Америка, Ближний Восток ───────────────
        Source("techcabal", "TechCabal (Africa)", "https://techcabal.com/feed/", "en", REGION_AFRICA, "Стартапы", 0.9f),
        Source("techpoint", "Techpoint Africa", "https://techpoint.africa/feed/", "en", REGION_AFRICA, "Стартапы", 0.8f),
        Source("nocamels", "NoCamels (Israel)", "https://nocamels.com/feed/", "en", REGION_MENA, "Стартапы", 0.8f),
        Source("restofworld", "Rest of World", "https://restofworld.org/feed/latest/", "en", REGION_GLOBAL, "Технологии", 1.0f),

        // ─────────────── Наука, ИИ, глубокие технологии ───────────────
        Source("arxiv_ai", "arXiv cs.AI", "https://rss.arxiv.org/rss/cs.AI", "en", REGION_GLOBAL, "Наука", 0.9f),
        Source("arxiv_lg", "arXiv cs.LG", "https://rss.arxiv.org/rss/cs.LG", "en", REGION_GLOBAL, "Наука", 0.9f),
        Source("huggingface", "Hugging Face", "https://huggingface.co/blog/feed.xml", "en", REGION_GLOBAL, "ИИ", 1.0f),
        Source("openai", "OpenAI", "https://openai.com/news/rss.xml", "en", REGION_GLOBAL, "ИИ", 1.0f),
        Source("googleai", "Google AI", "https://blog.google/technology/ai/rss/", "en", REGION_GLOBAL, "ИИ", 1.0f),
        Source("decoder", "The Decoder", "https://the-decoder.com/feed/", "en", REGION_EU, "ИИ", 0.9f),
        Source("nature", "Nature", "https://www.nature.com/nature.rss", "en", REGION_GLOBAL, "Наука", 1.0f),
        Source("sciencedaily", "ScienceDaily Tech", "https://www.sciencedaily.com/rss/top/technology.xml", "en", REGION_GLOBAL, "Наука", 0.8f),
        Source("physorg", "Phys.org Tech", "https://phys.org/rss-feed/technology-news/", "en", REGION_GLOBAL, "Наука", 0.8f),

        // ─────────────── Россия и СНГ (перевод не требуется) ───────────────
        Source("habr", "Хабр", "https://habr.com/ru/rss/news/?fl=ru", "ru", REGION_CIS, "Технологии", 1.0f),
        Source("habr_all", "Хабр — всё", "https://habr.com/ru/rss/all/all/?fl=ru", "ru", REGION_CIS, "Технологии", 0.8f),
        Source("vc", "vc.ru", "https://vc.ru/rss", "ru", REGION_CIS, "Стартапы", 1.0f),
        Source("firrma", "Firrma", "https://firrma.ru/rss/", "ru", REGION_CIS, "Финансирование", 0.9f),
        Source("cnews", "CNews", "https://www.cnews.ru/inc/rss/news.xml", "ru", REGION_CIS, "Технологии", 0.8f),
        Source("rb", "RB.ru", "https://rb.ru/rss/", "ru", REGION_CIS, "Стартапы", 0.8f),
        Source("dtf", "DTF", "https://dtf.ru/rss", "ru", REGION_CIS, "Продукты", 0.6f),
        Source("nplus1", "N+1", "https://nplus1.ru/rss", "ru", REGION_CIS, "Наука", 0.8f),
        Source("devby", "dev.by (Беларусь)", "https://dev.by/rss", "ru", REGION_CIS, "Технологии", 0.8f),
        Source("onliner", "Onliner.by", "https://tech.onliner.by/rss", "ru", REGION_CIS, "Железо", 0.7f),
        Source("dou", "DOU.ua", "https://dou.ua/feed/", "ru", REGION_CIS, "Технологии", 0.7f)
    )

    val CATEGORIES = listOf(
        "Все",
        "ИИ",
        "Финансирование",
        "Стартапы",
        "Идеи",
        "Продукты",
        "Железо",
        "Наука",
        "Регулирование",
        "Кибербезопасность",
        "Космос",
        "Биотех",
        "Энергия"
    )

    val REGIONS = listOf(
        REGION_GLOBAL, REGION_US, REGION_EU, REGION_ASIA, REGION_CIS,
        REGION_AFRICA, REGION_LATAM, REGION_MENA
    )

    fun enabled(prefs: AppPrefs): List<Source> {
        val disabled = prefs.disabledSources()
        return ALL.filter { it.id !in disabled }
    }

    fun byId(id: String): Source? = ALL.firstOrNull { it.id == id }
}
