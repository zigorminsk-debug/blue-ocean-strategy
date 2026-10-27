package by.blueocean.news.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import by.blueocean.news.data.ArticleEntity
import by.blueocean.news.data.Direction
import by.blueocean.news.data.DirectionStat
import by.blueocean.news.data.TrendWord
import by.blueocean.news.data.Trends

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrendsScreen(
    window: WindowInfo,
    articles: List<ArticleEntity>,
    onKeyword: (String) -> Unit,
    onOpen: (ArticleEntity) -> Unit,
    onTranslate: (ArticleEntity) -> Unit
) {
    val words = remember(articles) { Trends.words(articles, days = 7, limit = 48) }
    val hot = remember(articles) {
        articles.filter { it.publishedAt > System.currentTimeMillis() - 3 * 86_400_000L }
    }
    val maxCount = words.maxOfOrNull { it.count } ?: 1

    AppScreen(window = window) {
        ScreenHeader(
            window = window,
            title = "Радар трендов",
            subtitle = "Частота терминов за 7 дней и рост относительно прошлой недели"
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(window.columns),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = window.listPadding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (words.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "Данных пока мало",
                        text = "Обновите ленту: радар считает тренды по загруженным материалам."
                    )
                }
            } else {
                item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle("Что обсуждают", "Нажмите на термин, чтобы отфильтровать ленту")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            words.take(28).forEach { word ->
                                AppChip(
                                    text = "${word.word}  ${word.count}",
                                    onClick = { onKeyword(word.word) },
                                    compact = true
                                )
                            }
                        }
                    }
                }

                item(key = "bars", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        SectionTitle("Топ по упоминаниям")
                        words.take(12).forEach { word -> TrendBar(word, maxCount) }
                    }
                }

                item(key = "hot_title", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle("Свежее за 3 дня", "${hot.size} материалов")
                        if (hot.isEmpty()) {
                            Text(
                                text = "Пока нет материалов за последние 3 дня — обновите ленту.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(items = hot.take(20), key = { "hot_${it.id}" }) { article ->
                    ArticleCard(
                        article = article,
                        onClick = { onOpen(article) },
                        onToggleSave = { },
                        onTranslate = if (!article.hasTranslation && article.lang != "ru") {
                            { onTranslate(article) }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendBar(word: TrendWord, maxCount: Int) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = word.word,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (word.delta > 0) {
                Text(
                    text = "+${word.delta}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = word.count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { (word.count.toFloat() / maxCount).coerceIn(0.02f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = if (word.isHot) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun DirectionCard(
    stat: DirectionStat,
    expanded: Boolean,
    onExpand: () -> Unit,
    onOpenLatest: (ArticleEntity) -> Unit
) {
    val direction: Direction = stat.direction
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppShapes.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(direction.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = direction.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${stat.count} новостей · потенциал ${stat.score} · ${direction.horizon}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                AppChip(
                    text = if (expanded) "Свернуть" else "Разбор",
                    onClick = onExpand,
                    selected = expanded,
                    compact = true
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Text("Почему это деньги", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(direction.why, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Как заработать", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(direction.howToEarn, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("С чего начать", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(direction.entry, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Риски", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(direction.risk, style = MaterialTheme.typography.bodySmall)
                stat.latest?.let { article ->
                    Spacer(Modifier.height(12.dp))
                    AppButton(
                        text = article.displayTitle,
                        onClick = { onOpenLatest(article) },
                        style = AppButtonStyle.Tonal,
                        size = AppButtonSize.Small,
                        fillWidth = true,
                        compactText = true
                    )
                }
            }
        }
    }
}
