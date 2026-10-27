package by.blueocean.news.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.blueocean.news.data.ArticleEntity
import by.blueocean.news.data.ArticleExtractor
import by.blueocean.news.data.Directions
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Экран чтения: полный текст статьи внутри приложения + перевод на русский.
 * Колонка текста ограничена по ширине и центрируется на планшетах.
 */
@Composable
fun ReaderScreen(
    window: WindowInfo,
    article: ArticleEntity,
    reader: ReaderState,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleOriginal: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    var fontSize by remember(article.id) {
        mutableFloatStateOf(if (window.isExpanded) 18f else 16f)
    }
    val directions = remember(article.id) {
        Directions.match(article.displayTitle + " " + article.displaySummary)
    }

    val bodyText = when {
        reader.showOriginal -> reader.originalText.ifBlank { article.summary }
        reader.translatedText.isNotBlank() -> reader.translatedText
        else -> ""
    }

    AppScreen(window = window, maxWidth = if (window.isExpanded) 860.dp else window.contentMaxWidth) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = window.edgePadding - 8.dp, end = window.edgePadding - 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                onClick = onBack
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.sourceName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Text(
                    text = "${article.region} · ${Format.ago(article.publishedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            AppTextButton(
                label = "А−",
                contentDescription = "Уменьшить шрифт",
                onClick = { fontSize = (fontSize - 1f).coerceAtLeast(13f) },
                enabled = fontSize > 13f
            )
            AppTextButton(
                label = "А+",
                contentDescription = "Увеличить шрифт",
                onClick = { fontSize = (fontSize + 1f).coerceAtMost(24f) },
                enabled = fontSize < 24f
            )
            AppIconButton(
                icon = if (article.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = "Сохранить",
                onClick = onToggleSave,
                active = article.isSaved
            )
            AppIconButton(
                icon = Icons.Filled.Share,
                contentDescription = "Поделиться",
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, article.displayTitle)
                        putExtra(Intent.EXTRA_TEXT, "${article.displayTitle}\n\n${article.link}")
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "Поделиться")) }
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = window.listPadding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("head") {
                Column {
                    Text(
                        text = article.displayTitle,
                        style = if (window.isExpanded) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    AppButtonRow {
                        AppTag(
                            text = article.category,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        AppTag(
                            text = article.region,
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            content = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (article.score >= 8) {
                            AppTag(
                                text = "потенциал ${article.score}",
                                container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                content = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${Format.dateTime(article.publishedAt)} · ${article.sourceName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (article.imageUrl.isNotBlank()) {
                item("image") {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(article.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (window.isExpanded) 280.dp else 190.dp)
                            .clip(RoundedCornerShape(AppShapes.card))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }

            item("switch") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (article.lang != "ru") {
                        AppChip(
                            text = "Перевод",
                            onClick = { if (reader.showOriginal) onToggleOriginal() },
                            selected = !reader.showOriginal,
                            leadingEmoji = "🇷🇺",
                            compact = true
                        )
                        Spacer(Modifier.width(8.dp))
                        AppChip(
                            text = "Оригинал (${article.lang})",
                            onClick = { if (!reader.showOriginal) onToggleOriginal() },
                            selected = reader.showOriginal,
                            leadingEmoji = "🌐",
                            compact = true
                        )
                    } else {
                        AppChip(
                            text = "Русский источник",
                            onClick = { },
                            selected = true,
                            leadingEmoji = "🇷🇺",
                            compact = true
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (bodyText.isNotBlank()) {
                        Text(
                            text = "${ArticleExtractor.readingMinutes(bodyText)} мин",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (reader.loading) {
                item("loading") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppShapes.card),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = reader.stage.ifBlank { "Загружаю статью…" },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { reader.progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Полный текст загружается с сайта источника и переводится на русский",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (reader.error != null) {
                item("error") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppShapes.card),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = reader.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(10.dp))
                            AppButtonRow {
                                AppButton(
                                    text = "Повторить",
                                    onClick = onRetry,
                                    style = AppButtonStyle.Danger,
                                    size = AppButtonSize.Small
                                )
                                AppButton(
                                    text = "Открыть оригинал",
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(article.link))
                                            )
                                        }
                                    },
                                    style = AppButtonStyle.Outline,
                                    size = AppButtonSize.Small
                                )
                            }
                        }
                    }
                }
            }

            if (bodyText.isNotBlank()) {
                item("body") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppShapes.card),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = if (window.isExpanded) 28.dp else 16.dp,
                                vertical = 18.dp
                            )
                        ) {
                            Text(
                                text = bodyText,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.55f).sp,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (!reader.showOriginal && article.lang != "ru") {
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "Машинный перевод. Возможны неточности формулировок — " +
                                        "оригинал доступен по переключателю выше.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (!reader.loading && reader.error == null) {
                item("summary") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppShapes.card),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = article.displaySummary.ifBlank { "Текст недоступен — откройте оригинал." },
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (reader.showOriginal && reader.originalText.isNotBlank() && reader.translatedText.isNotBlank()) {
                item("translate_hint") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Показан текст на языке оригинала. Нажмите «Перевод», " +
                                "чтобы вернуться к русской версии.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (directions.isNotEmpty()) {
                item("directions") {
                    Column {
                        SectionTitle("Связанные направления")
                        directions.take(2).forEach { direction ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                shape = RoundedCornerShape(AppShapes.card),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "${direction.emoji} ${direction.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = direction.howToEarn,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item("actions") {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    AppButtonRow {
                        AppButton(
                            text = "Обновить перевод",
                            onClick = onRetry,
                            icon = Icons.Filled.Translate,
                            style = AppButtonStyle.Primary
                        )
                        AppButton(
                            text = "Источник",
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)))
                                }
                            },
                            icon = Icons.Filled.OpenInNew,
                            style = AppButtonStyle.Tonal
                        )
                        AppButton(
                            text = "Поделиться",
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, article.displayTitle)
                                    putExtra(Intent.EXTRA_TEXT, "${article.displayTitle}\n\n${article.link}")
                                }
                                runCatching { context.startActivity(Intent.createChooser(send, "Поделиться")) }
                            },
                            icon = Icons.Filled.Share,
                            style = AppButtonStyle.Ghost
                        )
                    }
                }
            }
        }
    }
}
