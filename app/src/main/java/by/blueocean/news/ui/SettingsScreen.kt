package by.blueocean.news.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.blueocean.news.data.Source
import by.blueocean.news.data.Sources
import by.blueocean.news.update.UpdateManager

@Composable
fun SettingsScreen(
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item("header") {
            ScreenHeader(
                title = "Настройки",
                subtitle = "Версия $versionName (код $versionCode) · постоянная подпись"
            )
        }

        // ─── Обновления ───
        item("upd_title") {
            SectionTitle("Обновления", "Сборка и публикация — GitHub Actions, установка поверх старой версии")
        }
        item("upd_block") {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onCheckUpdate,
                        enabled = !update.checking && !update.downloading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (update.checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(16.dp).height(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (update.checking) "Проверяю…" else "Проверить обновления")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onOpenInstallPermission,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Разрешить установку")
                    }
                }
                if (update.info != null) {
                    Spacer(Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Доступна версия ${update.info.version}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (update.info.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = update.info.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            if (update.downloading) {
                                LinearProgressIndicator(progress = { update.progress / 100f })
                                Spacer(Modifier.height(4.dp))
                                Text("Скачивание: ${update.progress}%", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Button(
                                    onClick = onInstallUpdate,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Скачать и установить")
                                }
                            }
                        }
                    }
                }
                if (update.message != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = update.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (update.error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = update.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ─── Лента ───
        item("feed_title") { SectionTitle("Лента и уведомления") }
        item("feed_block") {
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
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 6, 12).forEach { hours ->
                        FilterChip(
                            selected = settings.syncIntervalHours == hours,
                            onClick = { onInterval(hours) },
                            label = { Text("$hours ч") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
                Text(
                    text = "Материалов с источника за один синк",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 25, 40).forEach { count ->
                        FilterChip(
                            selected = settings.maxItemsPerSource == count,
                            onClick = { onMaxItems(count) },
                            label = { Text("$count") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Перевод на русский",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Переведено ${sync.translatedArticles} из ${sync.totalArticles} материалов ленты. " +
                        "Очередь: ${sync.translationQueue}. Перевод идёт автоматически в фоне; " +
                        "полный текст открытой статьи переводится отдельно.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        if (sync.totalArticles == 0) 0f
                        else (sync.translatedArticles.toFloat() / sync.totalArticles).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (sync.translating) "Идёт перевод…" else "Перевод запускается автоматически",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onTranslateAll, enabled = !sync.translating) {
                        Text("Перевести всё")
                    }
                }
                if (sync.lastSync > 0) {
                    Text(
                        text = "Последний синк: ${Format.dateTime(sync.lastSync)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ─── Источники ───
        item("src_title") {
            Column {
                SectionTitle("Источники", "Всего ${sources.size} лент из разных регионов")
                TextButton(onClick = onResetSources) { Text("Включить все") }
            }
        }

        grouped.forEach { (region, regionSources) ->
            item("region_$region") {
                Text(
                    text = region,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
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
                    Switch(
                        checked = source.id !in settings.disabledSources,
                        onCheckedChange = { enabled -> onSourceToggle(source.id, enabled) }
                    )
                }
            }
        }

        // ─── О приложении ───
        item("about_title") { SectionTitle("О приложении") }
        item("about_block") {
            SettingsCard {
                Text(
                    text = "Blue Ocean IT собирает новости технологий, стартапов и перспективных " +
                        "направлений со всего мира, переводит их на русский и показывает в ленте. " +
                        "Денежные сигналы и направления помогают увидеть, где появляется прибыль.\n\n" +
                        "Обновления публикуются в GitHub Releases и подписаны постоянным ключом: " +
                        "новая версия ставится поверх старой с сохранением данных.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(UpdateManager.releasesPage()))
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Релизы") }
                    OutlinedButton(
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
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("GitHub") }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Уведомления") }
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
        shape = RoundedCornerShape(18.dp),
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
