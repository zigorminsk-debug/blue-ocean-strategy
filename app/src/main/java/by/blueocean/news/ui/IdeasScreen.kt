package by.blueocean.news.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    articles: List<ArticleEntity>,
    onOpen: (ArticleEntity) -> Unit,
    onToggleSave: (ArticleEntity) -> Unit
) {
    val stats = remember(articles) { Trends.directionStats(articles, days = 21) }
    val opportunities = remember(articles) { Trends.opportunities(articles, minScore = 8, limit = 25) }
    var expanded by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item("header") {
            ScreenHeader(
                title = "Идеи и деньги",
                subtitle = "Направления, где рождается прибыль, и новости с денежными сигналами"
            )
        }

        item("how") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
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

        item("opp_title") {
            SectionTitle(
                "Денежные сигналы",
                if (opportunities.isEmpty()) "Пока нет материалов с высоким потенциалом"
                else "${opportunities.size} материалов с высоким потенциалом"
            )
        }

        if (opportunities.isEmpty()) {
            item("opp_empty") {
                EmptyState(
                    title = "Копим сигналы",
                    text = "Обновите ленту — потенциал считается автоматически по ключевым словам о деньгах."
                )
            }
        }

        items(items = opportunities, key = { "opp_${it.id}" }) { article ->
            ArticleCard(
                article = article,
                onClick = { onOpen(article) },
                onToggleSave = { onToggleSave(article) }
            )
        }

        item("dir_title") {
            Column {
                Spacer(Modifier.height(8.dp))
                SectionTitle(
                    "Направления развития",
                    "Сопоставление с новостями за 21 день · ${Directions.ALL.size} ниш"
                )
            }
        }

        if (stats.isEmpty()) {
            item("dir_empty") {
                Text(
                    text = "После загрузки новостей здесь появятся направления, которые чаще всего " +
                        "встречаются в мировой повестке.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(items = stats, key = { "dir_${it.direction.id}" }) { stat ->
            DirectionCard(
                stat = stat,
                expanded = expanded == stat.direction.id,
                onExpand = {
                    expanded = if (expanded == stat.direction.id) null else stat.direction.id
                },
                onOpenLatest = onOpen
            )
        }

        item("footer") {
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Раздел обновляется автоматически вместе с лентой.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}
