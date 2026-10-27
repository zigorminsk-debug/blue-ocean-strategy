package by.blueocean.news.data

import java.util.Locale

/**
 * Локальный «аналитик»: определяет категорию, оценивает коммерческий потенциал
 * новости и выделяет денежные сигналы. Работает без внешних API.
 */
object Analyzer {

    private val categoryRules: List<Pair<String, List<String>>> = listOf(
        "ИИ" to listOf(
            "искусственн", "нейросет", "нейронн", "ии ", "ai ", " ai,", "llm", "gpt", "chatgpt",
            "machine learning", "deep learning", "нейро", "агент", "agentic", "openai", "anthropic",
            "deepmind", "midjourney", "stable diffusion", "трансформер", "инференс", "инференц"
        ),
        "Финансирование" to listOf(
            "раунд", "привлек", "привлёк", "инвестиц", "венчур", "оценк", "миллиард", "миллион",
            "funding", "raised", "raises", "series a", "series b", "series c", "seed round",
            "pre-seed", "valuation", "ipo", "вышел на биржу", "размещен", "акции", "долг",
            "грант", "grant", "m&a", "поглощ", "приобрел", "acquire", "acquisition", "buyout",
            "фонд", "vc ", "unicoin", "краудфанд", "kickstarter"
        ),
        "Стартапы" to listOf(
            "стартап", "startup", "основател", "founder", "фаундер", "акселератор", "accelerator",
            "инкубатор", "incubator", "y combinator", "product hunt", "mvp", "пилот"
        ),
        "Идеи" to listOf(
            "идея", "тренд", "trend", "прогноз", "forecast", "рынок", "market", "ниша", "niche",
            "возможност", "opportunit", "монетизац", "monetiz", "бизнес-модель", "business model",
            "как заработать", "стратег", "конкурент"
        ),
        "Продукты" to listOf(
            "запустил", "выпустил", "релиз", "launch", "release", "обновлен", "update", "версия",
            "приложение", "app ", "платформ", "сервис", "функци", "beta", "бета"
        ),
        "Железо" to listOf(
            "чип", "процессор", "gpu", "nvidia", "amd", "intel", "tsmc", "samsung", "полупровод",
            "semiconductor", "смартфон", "ноутбук", "дата-центр", "datacenter", "сервер", "железо",
            "hardware", "робот", "robot", "дрон", "drone", "arma", "arm ", "fpga"
        ),
        "Наука" to listOf(
            "исследован", "study", "учёные", "ученые", "science", "nature", "прорыв", "breakthrough",
            "эксперимент", "квант", "quantum", "физик", "биолог", "материал", "arxiv", "патент",
            "patent", "первый в мире"
        ),
        "Регулирование" to listOf(
            "закон", "регулятор", "штраф", "суд", "иск", "антимонопол", "запрет", "regulation",
            "regulator", "fine", "lawsuit", "ban ", "privacy", "gdpr", "цензур", "лицензи", "налог",
            "тариф", "политик", "government", "правительств", "госдум", "минц"
        ),
        "Кибербезопасность" to listOf(
            "взлом", "утечк", "хакер", "hack", "breach", "ransomware", "шифровальщик", "уязвим",
            "vulnerability", "cve", "malware", "вирус", "фишинг", "phishing", "0day", "кибербез"
        ),
        "Космос" to listOf(
            "космос", "space", "spacex", "ракет", "rocket", "спутник", "satellite", "nasa", "mars",
            "луна", "орбит", "orbit", "запуск ракеты"
        ),
        "Биотех" to listOf(
            "биотех", "biotech", "геном", "gene", "днк", "dna", "клетк", "cell", "медицин",
            "pharma", "лекарств", "вакцин", "crispr", "протеин", "protein", "клиническ"
        ),
        "Энергия" to listOf(
            "энерг", "energy", "батаре", "battery", "солнечн", "solar", "водород", "hydrogen",
            "термояд", "fusion", "сеть", "grid", "нефт", "газ", "климат", "carbon", "выброс"
        )
    )

    private val moneySignals: List<Pair<String, Int>> = listOf(
        "раунд" to 5, "привлек" to 5, "привлёк" to 5, "инвестиц" to 4, "funding" to 5,
        "raised" to 5, "raises" to 5, "series a" to 5, "series b" to 5, "series c" to 5,
        "seed" to 4, "pre-seed" to 4, "valuation" to 5, "оценк" to 4, "миллиард" to 4,
        "billion" to 4, "миллион" to 3, "million" to 3, "ipo" to 5, "вышел на биржу" to 5,
        "acquisition" to 5, "acquire" to 4, "поглощ" to 4, "приобрел" to 4, "приобрёл" to 4,
        "выручк" to 3, "revenue" to 3, "прибыл" to 3, "profit" to 3, "маржа" to 3,
        "грант" to 4, "grant" to 4, "тендер" to 4, "tender" to 3, "субсиди" to 3,
        "патент" to 3, "patent" to 3, "лицензи" to 2, "контракт" to 3, "contract" to 2,
        "monetiz" to 3, "монетизац" to 3, "рынок" to 2, "market" to 2, "спрос" to 2,
        "demand" to 2, "рост" to 1, "growth" to 1, "запуск" to 2, "launch" to 2,
        "партнерств" to 2, "partnership" to 2, "экспанси" to 3, "expansion" to 2,
        "прорыв" to 3, "breakthrough" to 3, "первый в мире" to 3, "новый рынок" to 4,
        "дефицит" to 2, "shortage" to 2, "регулир" to 1, "тариф" to 2
    )

    fun detectCategory(title: String, summary: String, fallback: String): String {
        val text = "$title $summary".lowercase(Locale.ROOT)
        var best = fallback
        var bestScore = 0
        for ((category, keywords) in categoryRules) {
            var score = 0
            for (k in keywords) {
                if (text.contains(k)) score += if (k.length > 5) 2 else 1
            }
            if (score > bestScore) {
                bestScore = score
                best = category
            }
        }
        return if (bestScore >= 2) best else fallback
    }

    fun moneyScore(title: String, summary: String, sourceWeight: Float): Pair<Int, List<String>> {
        val text = "$title $summary".lowercase(Locale.ROOT)
        var score = 0
        val tags = mutableListOf<String>()
        for ((keyword, weight) in moneySignals) {
            if (text.contains(keyword)) {
                score += weight
                val tag = tagFor(keyword)
                if (!tags.contains(tag)) tags.add(tag)
            }
        }
        score = (score * sourceWeight).toInt().coerceIn(0, 100)
        return score to tags.take(4)
    }

    private fun tagFor(keyword: String): String = when (keyword) {
        "раунд", "привлек", "привлёк", "инвестиц", "funding", "raised", "raises",
        "series a", "series b", "series c", "seed", "pre-seed", "венчур" -> "Раунд"
        "valuation", "оценк", "миллиард", "billion" -> "Оценка"
        "ipo", "вышел на биржу" -> "IPO"
        "acquisition", "acquire", "поглощ", "приобрел", "приобрёл" -> "M&A"
        "выручк", "revenue", "прибыл", "profit", "маржа" -> "Выручка"
        "грант", "grant", "субсиди", "тендер", "tender" -> "Грант/тендер"
        "патент", "patent" -> "Патент"
        "монетизац", "monetiz" -> "Монетизация"
        "рынок", "market", "спрос", "demand" -> "Рынок"
        "прорыв", "breakthrough", "первый в мире" -> "Прорыв"
        "дефицит", "shortage" -> "Дефицит"
        "экспанси", "expansion", "партнерств", "partnership" -> "Партнёрство"
        "контракт", "contract" -> "Контракт"
        "лицензи", "регулир", "тариф" -> "Регулирование"
        else -> "Запуск"
    }

    fun scoreFor(item: FeedParser.Item, source: Source): Pair<Int, List<String>> {
        val (base, tags) = moneyScore(item.title, item.summary, source.weight)
        // Свежесть немного повышает приоритет
        val hoursOld = ((System.currentTimeMillis() - item.publishedAt) / 3_600_000L).coerceAtLeast(0)
        val freshness = when {
            hoursOld <= 6 -> 6
            hoursOld <= 24 -> 3
            hoursOld <= 72 -> 1
            else -> 0
        }
        return (base + freshness).coerceIn(0, 100) to tags
    }
}
