package by.blueocean.news.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.blueocean.news.data.ArticleEntity
import by.blueocean.news.data.Directions
import by.blueocean.news.data.Trends

@Composable
fun IdeasScreen(
    window: WindowInfo,
    articles: List<ArticleEntity>,
    onOpen: (ArticleEntity) -> Unit,
    onToggleSave: (ArticleEntity) -> Unit,
    onTranslate: (ArticleEntity) -> Unit
) {
    val stats = remember(articles) { Trends.directionStats(articles, days = 21) }
    val opportunities = remember(articles) { Trends.opportunities(articles, minScore = 8, limit = 25) }
    var expanded by remember { mutableStateOf<String?>(null) }

    AppScreen(window = window) {
        ScreenHeader(
            window = window,
            title = "Идеи и деньги",
            subtitle = "Направления, где рождается прибыль, и новости с денежными сигналами"
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
            item(key = "how", span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppShapes.card),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Как читать этот раздел",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "💰 — оценка коммерческого потенциала новости: раунды, оценка, IPO, M&A, " +
                                "выручка, гранты, патенты, дефицит и новые рынки. Чем выше число, тем ближе " +
                                "новость к деньгам.\n\nБлок «Денежные сигналы» — свежие события, из которых " +
                                "можно сделать бизнес. Блок «Направления» — 18 перспективных ниш с моделью " +
                                "заработка и точкой входа.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item(key = "opp_title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(
                    "Денежные сигналы",
                    if (opportunities.isEmpty()) "Пока нет материалов с высоким потенциалом"
                    else "${opportunities.size} материалов с высоким потенциалом"
                )
            }

            if (opportunities.isEmpty()) {
                item(key = "opp_empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "Копим сигналы",
                        text = "Обновите ленту — потенциал считается автоматически по ключевым словам о деньгах."
                    )
                }
            } else {
                items(items = opportunities, key = { "opp_${it.id}" }) { article ->
                    ArticleCard(
                        article = article,
                        onClick = { onOpen(article) },
                        onToggleSave = { onToggleSave(article) },
                        onTranslate = if (!article.hasTranslation && article.lang != "ru") {
                            { onTranslate(article) }
                        } else {
                            null
                        }
                    )
                }
            }

            item(key = "dir_title", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    SectionTitle(
                        "Направления развития",
                        "Сопоставление с новостями за 21 день · ${Directions.ALL.size} ниш"
                    )
                }
            }

            if (stats.isEmpty()) {
                item(key = "dir_empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "После загрузки новостей здесь появятся направления, которые чаще всего " +
                            "встречаются в мировой повестке.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(items = stats, key = { "dir_${it.direction.id}" }, span = { GridItemSpan(maxLineSpan) }) { stat ->
                DirectionCard(
                    stat = stat,
                    expanded = expanded == stat.direction.id,
                    onExpand = {
                        expanded = if (expanded == stat.direction.id) null else stat.direction.id
                    },
                    onOpenLatest = onOpen
                )
            }

            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Раздел обновляется автоматически вместе с лентой.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
