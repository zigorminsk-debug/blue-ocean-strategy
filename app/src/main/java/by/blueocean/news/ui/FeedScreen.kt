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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
    window: WindowInfo,
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
    onTranslateAll: () -> Unit,
    onTranslateArticle: (ArticleEntity) -> Unit
) {
    AppScreen(window = window) {
        ScreenHeader(
            window = window,
            title = "Blue Ocean IT",
            subtitle = buildString {
                val translated = state.sync.translatedArticles.coerceAtMost(state.sync.totalArticles)
                append("${state.total} материалов · переведено $translated")
                if (state.sync.syncing) append(" · синк…")
                else if (state.sync.translating) append(" · перевод…")
            }
        ) {
            AppIconButton(
                icon = if (state.filters.savedOnly) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = "Избранное",
                onClick = onToggleSavedOnly,
                active = state.filters.savedOnly
            )
            AppIconButton(
                icon = Icons.Filled.Refresh,
                contentDescription = "Обновить",
                onClick = onRefresh,
                enabled = !state.sync.syncing
            )
        }

        OutlinedTextField(
            value = state.filters.query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = window.edgePadding),
            placeholder = { Text("Поиск по новостям и идеям") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(AppShapes.control)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = window.edgePadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Sources.CATEGORIES) { category ->
                AppChip(
                    text = category,
                    onClick = { onCategory(category) },
                    selected = state.filters.category == category,
                    compact = true
                )
            }
        }

        if (state.sync.syncing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        PullToRefreshBox(
            isRefreshing = state.sync.syncing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(window.columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = window.listPadding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (update.info != null) {
                    item(key = "update_banner", span = { GridItemSpan(maxLineSpan) }) {
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
                    item(key = "update_error", span = { GridItemSpan(maxLineSpan) }) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(AppShapes.card)
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

                if (state.sync.translating || state.sync.translationQueue > 0) {
                    item(key = "translate_queue", span = { GridItemSpan(maxLineSpan) }) {
                        TranslateQueueCard(
                            translating = state.sync.translating,
                            queue = state.sync.translationQueue,
                            onTranslateAll = onTranslateAll
                        )
                    }
                }

                if (state.articles.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            title = "Пока пусто",
                            text = if (state.total == 0) {
                                "Потяните вниз, чтобы загрузить первые новости из мировых источников. " +
                                    "Первый запуск может занять до минуты."
                            } else {
                                "Ничего не найдено по фильтру. Сбросьте поиск или выберите другую категорию."
                            }
                        )
                    }
                }

                gridItems(items = state.articles, key = { it.id }) { article ->
                    ArticleCard(
                        article = article,
                        onClick = { onOpen(article) },
                        onToggleSave = { onToggleSave(article) },
                        onTranslate = if (!article.hasTranslation && article.lang != "ru") {
                            { onTranslateArticle(article) }
                        } else {
                            null
                        },
                        imageSize = if (window.isExpanded) 96.dp else if (window.isCompact) 84.dp else 92.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslateQueueCard(
    translating: Boolean,
    queue: Int,
    onTranslateAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(AppShapes.card)
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (translating) {
                        "Перевожу на русский: осталось $queue"
                    } else {
                        "Без перевода: $queue"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                AppButton(
                    text = if (translating) "Идёт перевод" else "Перевести всё",
                    onClick = onTranslateAll,
                    style = AppButtonStyle.Tonal,
                    size = AppButtonSize.Small,
                    enabled = !translating,
                    loading = translating
                )
            }
            if (translating) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                )
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
        shape = RoundedCornerShape(AppShapes.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚀", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Доступно обновление $version",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Скачиваю APK: $progress%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                AppButtonRow {
                    AppButton(
                        text = "Установить",
                        onClick = onInstall,
                        size = AppButtonSize.Small,
                        icon = null
                    )
                    AppButton(
                        text = "Позже",
                        onClick = onDismiss,
                        style = AppButtonStyle.Tonal,
                        size = AppButtonSize.Small
                    )
                    AppButton(
                        text = "Пропустить",
                        onClick = onSkip,
                        style = AppButtonStyle.Ghost,
                        size = AppButtonSize.Small
                    )
                }
            }
        }
    }
}
