package by.blueocean.news.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Лента", Icons.Filled.RssFeed),
    Tab("Тренды", Icons.Filled.TrendingUp),
    Tab("Идеи", Icons.Filled.Lightbulb),
    Tab("Настройки", Icons.Filled.Settings)
)

@Composable
fun AppRoot(viewModel: AppViewModel, openUpdatesInitially: Boolean = false) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    val detailArticle = state.all.firstOrNull { it.id == detailId }

    LaunchedEffect(openUpdatesInitially) {
        if (openUpdatesInitially) {
            tab = 3
            viewModel.checkUpdate(manual = true)
        }
    }

    BackHandler(enabled = detailArticle != null) { detailId = null }

    Scaffold(
        bottomBar = {
            if (detailArticle == null) {
                NavigationBar {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val current = detailArticle
            when {
                current != null -> DetailScreen(
                    article = current,
                    onBack = { detailId = null },
                    onToggleSave = { viewModel.toggleSaved(current) }
                )
                tab == 0 -> FeedScreen(
                    state = state,
                    update = update,
                    onRefresh = viewModel::refresh,
                    onQuery = viewModel::setQuery,
                    onCategory = viewModel::setCategory,
                    onToggleSavedOnly = viewModel::toggleSavedOnly,
                    onOpen = { article ->
                        viewModel.markRead(article)
                        detailId = article.id
                    },
                    onToggleSave = viewModel::toggleSaved,
                    onInstallUpdate = viewModel::downloadAndInstall,
                    onDismissUpdate = viewModel::dismissUpdateBanner,
                    onSkipVersion = viewModel::skipVersion,
                    onTranslateMore = viewModel::translateMore
                )
                tab == 1 -> TrendsScreen(
                    articles = state.all,
                    onKeyword = { word ->
                        viewModel.setCategory("Все")
                        viewModel.setQuery(word)
                        tab = 0
                    },
                    onOpen = { article ->
                        viewModel.markRead(article)
                        detailId = article.id
                    }
                )
                tab == 2 -> IdeasScreen(
                    articles = state.all,
                    onOpen = { article ->
                        viewModel.markRead(article)
                        detailId = article.id
                    },
                    onToggleSave = viewModel::toggleSaved
                )
                else -> SettingsScreen(
                    settings = settings,
                    sync = state.sync,
                    update = update,
                    versionName = viewModel.versionName,
                    versionCode = viewModel.versionCode,
                    sources = viewModel.sources,
                    onAutoUpdate = viewModel::setAutoUpdate,
                    onAutoInstall = viewModel::setAutoInstall,
                    onNotifications = viewModel::setNotifications,
                    onWifiOnly = viewModel::setWifiOnly,
                    onInterval = viewModel::setSyncInterval,
                    onMaxItems = viewModel::setMaxItems,
                    onSourceToggle = viewModel::setSourceEnabled,
                    onResetSources = viewModel::resetSources,
                    onCheckUpdate = { viewModel.checkUpdate(manual = true) },
                    onInstallUpdate = viewModel::downloadAndInstall,
                    onTranslateMore = viewModel::translateMore,
                    onOpenInstallPermission = viewModel::openInstallPermission
                )
            }
        }
    }

    // Обновление скачивается сразу после проверки, если включена автоустановка
    val pendingUpdate = update.info
    val autoInstall = settings.autoInstall
    var autoTriedVersion by remember { mutableStateOf("") }
    LaunchedEffect(pendingUpdate?.version, autoInstall) {
        val version = pendingUpdate?.version ?: return@LaunchedEffect
        if (autoInstall && autoTriedVersion != version) {
            autoTriedVersion = version
            viewModel.downloadAndInstall()
        }
    }
}
