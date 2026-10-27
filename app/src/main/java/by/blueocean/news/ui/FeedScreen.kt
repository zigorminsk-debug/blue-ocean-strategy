package by.blueocean.news.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import by.blueocean.news.data.ArticleEntity
import by.blueocean.news.data.Sources

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: UiState,
    update: UpdateState,
    onRefresh: () -> Unit,
    onQuery: (String) -> Unit,
    onCategory: (String) -> Unit,
    onToggleSavedOnly: () -> Unit,
    onOpen: (ArticleEntity) -> Unit,
    onToggleSave: (ArticleEntity) -> Unit,
    onInstallUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onSkipVersion: (String) -> Unit,
    onTranslateMore: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Blue Ocean IT",
            subtitle = buildString {
                append("${state.total} материалов · перевод на русский")
                if (state.sync.status.isNotBlank()) {
                    append(" · ")
                    append(state.sync.status)
                }
            }
        ) {
            IconButton(onClick = onToggleSavedOnly) {
                Icon(
                    imageVector = if (state.filters.savedOnly) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = "Избранное",
                    tint = if (state.filters.savedOnly) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh, enabled = !state.sync.syncing) {
                if (state.sync.syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                }
            }
        }

        OutlinedTextField(
            value = state.filters.query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Поиск по новостям и идеям") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Sources.CATEGORIES) { category ->
                FilterChip(
                    selected = state.filters.category == category,
                    onClick = { onCategory(category) },
                    label = { Text(category) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        if (state.sync.syncing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        PullToRefreshBox(
            isRefreshing = state.sync.syncing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (update.info != null) {
                    item("update_banner") {
                        UpdateBanner(
                            version = update.info.version,
                            notes = update.info.notes,
                            downloading = update.downloading,
                            progress = update.progress,
                            onInstall = onInstallUpdate,
                            onDismiss = onDismissUpdate,
                            onSkip = { onSkipVersion(update.info.version) }
                        )
                    }
                }

                if (update.error != null) {
                    item("update_error") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = update.error,
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (state.sync.translationQueue > 0) {
                    item("translate_queue") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Перевод в очереди: ${state.sync.translationQueue}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onTranslateMore) {
                                    Text("Перевести")
                                }
                            }
                        }
                    }
                }

                if (state.articles.isEmpty()) {
                    item("empty") {
                        EmptyState(
                            title = "Пока пусто",
                            text = if (state.total == 0)
                                "Потяните вниз, чтобы загрузить первые новости из мировых источников. Первый запуск может занять до минуты."
                            else
                                "Ничего не найдено по фильтру. Сбросьте поиск или выберите другую категорию."
                        )
                    }
                }

                items(items = state.articles, key = { it.id }) { article ->
                    ArticleCard(
                        article = article,
                        onClick = { onOpen(article) },
                        onToggleSave = { onToggleSave(article) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(
    version: String,
    notes: String,
    downloading: Boolean,
    progress: Int,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Доступно обновление $version",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Скачиваю APK: $progress%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onInstall) {
                        Text("Установить", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Позже", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSkip) {
                        Text("Пропустить", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}
