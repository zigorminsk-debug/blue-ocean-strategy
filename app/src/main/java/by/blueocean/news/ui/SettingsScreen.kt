package by.blueocean.news.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.blueocean.news.data.Source
import by.blueocean.news.update.UpdateManager

@Composable
fun SettingsScreen(
    window: WindowInfo,
    settings: SettingsState,
    sync: SyncState,
    update: UpdateState,
    versionName: String,
    versionCode: Int,
    sources: List<Source>,
    onAutoUpdate: (Boolean) -> Unit,
    onAutoInstall: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onMaxItems: (Int) -> Unit,
    onSourceToggle: (String, Boolean) -> Unit,
    onResetSources: () -> Unit,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onTranslateAll: () -> Unit,
    onOpenInstallPermission: () -> Unit
) {
    val context = LocalContext.current
    val grouped = sources.groupBy { it.region }
    val columns = if (window.isExpanded) 2 else 1

    AppScreen(window = window) {
        ScreenHeader(
            window = window,
            title = "Настройки",
            subtitle = "Версия $versionName (код $versionCode) · постоянная подпись"
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = window.listPadding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "upd_title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("Обновления", "Сборка и публикация — GitHub Actions, установка поверх старой версии")
            }
            item(key = "upd_block", span = { GridItemSpan(maxLineSpan) }) {
                SettingsCard {
                    SwitchRow(
                        title = "Автообновление",
                        subtitle = "Проверять новые версии при запуске и каждые 6 часов",
                        checked = settings.autoUpdate,
                        onCheckedChange = onAutoUpdate
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = "Скачивать автоматически",
                        subtitle = "Загружать APK сразу после проверки и предлагать установку",
                        checked = settings.autoInstall,
                        onCheckedChange = onAutoInstall
                    )
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    AppButtonRow {
                        AppButton(
                            text = if (update.checking) "Проверяю…" else "Проверить обновления",
                            onClick = onCheckUpdate,
                            enabled = !update.checking && !update.downloading,
                            loading = update.checking,
                            style = AppButtonStyle.Primary,
                            size = AppButtonSize.Small
                        )
                        AppButton(
                            text = "Разрешить установку",
                            onClick = onOpenInstallPermission,
                            style = AppButtonStyle.Outline,
                            size = AppButtonSize.Small
                        )
                    }
                    val info = update.info
                    if (info != null) {
                        Spacer(Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AppShapes.control),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Доступна версия ${info.version}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (info.notes.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = info.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                if (update.downloading) {
                                    LinearProgressIndicator(progress = { update.progress / 100f })
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Скачивание: ${update.progress}%",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                } else {
                                    AppButton(
                                        text = "Скачать и установить",
                                        onClick = onInstallUpdate,
                                        style = AppButtonStyle.Primary,
                                        size = AppButtonSize.Small
                                    )
                                }
                            }
                        }
                    }
                    if (update.message != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = update.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (update.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = update.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item(key = "feed_title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("Лента и уведомления")
            }
            item(key = "feed_block", span = { GridItemSpan(maxLineSpan) }) {
                SettingsCard {
                    SwitchRow(
                        title = "Уведомления",
                        subtitle = "Сообщать о новых материалах и обновлениях",
                        checked = settings.notifications,
                        onCheckedChange = onNotifications
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = "Только Wi-Fi",
                        subtitle = "Фоновое обновление без мобильного трафика",
                        checked = settings.wifiOnly,
                        onCheckedChange = onWifiOnly
                    )
                    HorizontalDivider()
                    Text(
                        text = "Интервал фонового обновления",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                    )
                    AppButtonRow {
                        listOf(1, 3, 6, 12).forEach { hours ->
                            AppChip(
                                text = "$hours ч",
                                onClick = { onInterval(hours) },
                                selected = settings.syncIntervalHours == hours,
                                compact = true
                            )
                        }
                    }
                    Text(
                        text = "Материалов с источника за один синк",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    )
                    AppButtonRow {
                        listOf(10, 25, 40).forEach { count ->
                            AppChip(
                                text = "$count",
                                onClick = { onMaxItems(count) },
                                selected = settings.maxItemsPerSource == count,
                                compact = true
                            )
                        }
                    }
                }
            }

            item(key = "translate_title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("Перевод на русский")
            }
            item(key = "translate_block", span = { GridItemSpan(maxLineSpan) }) {
                SettingsCard {
                    Text(
                        text = "Переведено ${sync.translatedArticles} из ${sync.totalArticles} материалов ленты. " +
                            "Очередь: ${sync.translationQueue}. Перевод идёт автоматически в фоне; " +
                            "полный текст открытой статьи переводится отдельно.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (sync.totalArticles == 0) 0f
                            else (sync.translatedArticles.toFloat() / sync.totalArticles).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    AppButtonRow {
                        AppButton(
                            text = if (sync.translating) "Идёт перевод…" else "Перевести всё",
                            onClick = onTranslateAll,
                            enabled = !sync.translating,
                            loading = sync.translating,
                            style = AppButtonStyle.Tonal,
                            size = AppButtonSize.Small
                        )
                    }
                    if (sync.lastSync > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Последний синк: ${Format.dateTime(sync.lastSync)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "src_title", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionTitle("Источники", "Всего ${sources.size} лент из разных регионов")
                    AppButton(
                        text = "Включить все",
                        onClick = onResetSources,
                        style = AppButtonStyle.Ghost,
                        size = AppButtonSize.Small
                    )
                }
            }

            grouped.forEach { (region, regionSources) ->
                item(key = "region_$region", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = region,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }
                items(items = regionSources, key = { "src_${it.id}" }) { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSourceToggle(source.id, source.id in settings.disabledSources) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(source.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${source.category} · ${source.lang}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = source.id !in settings.disabledSources,
                            onCheckedChange = { enabled -> onSourceToggle(source.id, enabled) }
                        )
                    }
                }
            }

            item(key = "about_title", span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("О приложении")
            }
            item(key = "about_block", span = { GridItemSpan(maxLineSpan) }) {
                SettingsCard {
                    Text(
                        text = "Blue Ocean IT собирает новости технологий, стартапов и перспективных " +
                            "направлений со всего мира, переводит их на русский и показывает в ленте. " +
                            "Денежные сигналы и направления помогают увидеть, где появляется прибыль.\n\n" +
                            "Обновления публикуются в GitHub Releases и подписаны постоянным ключом: " +
                            "новая версия ставится поверх старой с сохранением данных.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    AppButtonRow {
                        AppButton(
                            text = "Релизы",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(UpdateManager.releasesPage()))
                                    )
                                }
                            },
                            style = AppButtonStyle.Outline,
                            size = AppButtonSize.Small
                        )
                        AppButton(
                            text = "GitHub",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/${UpdateManager.OWNER}/${UpdateManager.REPO}")
                                        )
                                    )
                                }
                            },
                            style = AppButtonStyle.Outline,
                            size = AppButtonSize.Small
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            AppButton(
                                text = "Уведомления",
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
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
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppShapes.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
