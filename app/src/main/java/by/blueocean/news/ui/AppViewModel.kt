package by.blueocean.news.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.blueocean.news.BuildConfig
import by.blueocean.news.data.AppPrefs
import by.blueocean.news.data.ArticleEntity
import by.blueocean.news.data.NewsRepository
import by.blueocean.news.data.NewsStore
import by.blueocean.news.data.Source
import by.blueocean.news.data.Sources
import by.blueocean.news.update.UpdateManager
import by.blueocean.news.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class Filters(
    val query: String = "",
    val category: String = "Все",
    val savedOnly: Boolean = false
)

data class SyncState(
    val syncing: Boolean = false,
    val translating: Boolean = false,
    val status: String = "",
    val lastSync: Long = 0L,
    val translationQueue: Int = 0,
    val totalArticles: Int = 0,
    val translatedArticles: Int = 0
)

data class UpdateState(
    val checking: Boolean = false,
    val info: UpdateManager.UpdateInfo? = null,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val message: String? = null,
    val error: String? = null
)

data class ReaderState(
    val articleId: Long = 0,
    val loading: Boolean = false,
    val stage: String = "",
    val progress: Int = 0,
    val translatedText: String = "",
    val originalText: String = "",
    val showOriginal: Boolean = false,
    val error: String? = null,
    val fromCache: Boolean = false
)

data class SettingsState(
    val autoUpdate: Boolean = true,
    val autoInstall: Boolean = true,
    val notifications: Boolean = true,
    val wifiOnly: Boolean = false,
    val syncIntervalHours: Int = 3,
    val maxItemsPerSource: Int = 25,
    val disabledSources: Set<String> = emptySet()
)

data class UiState(
    val articles: List<ArticleEntity> = emptyList(),
    val all: List<ArticleEntity> = emptyList(),
    val total: Int = 0,
    val filters: Filters = Filters(),
    val sync: SyncState = SyncState()
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)
    private val store = NewsStore.get(application)
    private val repo = NewsRepository.get(application)
    private val updateManager = UpdateManager(application)

    private val _articles = MutableStateFlow<List<ArticleEntity>>(emptyList())
    private val _filters = MutableStateFlow(Filters())
    private val _sync = MutableStateFlow(SyncState(lastSync = prefs.lastSyncAt, status = prefs.lastSyncInfo))
    private val _update = MutableStateFlow(UpdateState())
    private val _reader = MutableStateFlow(ReaderState())
    private val _settings = MutableStateFlow(loadSettings())

    private var readerJob: Job? = null
    private var queueJob: Job? = null

    val filters: StateFlow<Filters> = _filters.asStateFlow()
    val sync: StateFlow<SyncState> = _sync.asStateFlow()
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    val reader: StateFlow<ReaderState> = _reader.asStateFlow()
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    val sources: List<Source> = Sources.ALL
    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE

    val uiState: StateFlow<UiState> = combine(
        _articles,
        _filters,
        _sync
    ) { list, filters, sync ->
        val query = filters.query.trim().lowercase(Locale.ROOT)
        val filtered = list.filter { article ->
            val categoryOk = filters.category == "Все" || article.category == filters.category
            val savedOk = !filters.savedOnly || article.isSaved
            val queryOk = query.isEmpty() ||
                article.displayTitle.lowercase(Locale.ROOT).contains(query) ||
                article.displaySummary.lowercase(Locale.ROOT).contains(query) ||
                article.sourceName.lowercase(Locale.ROOT).contains(query)
            categoryOk && savedOk && queryOk
        }
        UiState(
            articles = filtered,
            all = list,
            total = list.size,
            filters = filters,
            sync = sync
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        WorkScheduler.scheduleSync(application, prefs.syncIntervalHours, prefs.wifiOnly)
        WorkScheduler.scheduleUpdateCheck(application)
        WorkScheduler.scheduleTranslation(application)
        viewModelScope.launch {
            reload()
            startTranslationQueue()
        }
        val stale = System.currentTimeMillis() - prefs.lastSyncAt > 20 * 60 * 1000L
        if (stale) refresh()
        if (prefs.autoUpdate) checkUpdate(manual = false)
    }

    private fun loadSettings(): SettingsState = SettingsState(
        autoUpdate = prefs.autoUpdate,
        autoInstall = prefs.autoInstall,
        notifications = prefs.notifications,
        wifiOnly = prefs.wifiOnly,
        syncIntervalHours = prefs.syncIntervalHours,
        maxItemsPerSource = prefs.maxItemsPerSource,
        disabledSources = prefs.disabledSources()
    )

    private suspend fun reload() {
        val list = withContext(Dispatchers.IO) { store.latest(1500) }
        val pending = withContext(Dispatchers.IO) {
            store.pendingTranslationCount() + store.pendingSummaryCount()
        }
        val total = withContext(Dispatchers.IO) { store.count() }
        val translated = withContext(Dispatchers.IO) { store.translatedCount() }
        _articles.value = list
        _sync.update {
            it.copy(
                translationQueue = pending,
                totalArticles = total,
                translatedArticles = translated
            )
        }
    }

    fun refresh() {
        if (_sync.value.syncing) return
        viewModelScope.launch {
            _sync.update { it.copy(syncing = true, status = "Обновляю ленту…") }
            val result = runCatching {
                repo.sync(
                    onProgress = { message -> _sync.update { it.copy(status = message) } },
                    onNewData = { launch { reload() } }
                )
            }
            if (result.isSuccess) {
                val syncResult = result.getOrThrow()
                _sync.update {
                    it.copy(
                        syncing = false,
                        status = syncResult.message,
                        lastSync = System.currentTimeMillis()
                    )
                }
            } else {
                _sync.update {
                    it.copy(
                        syncing = false,
                        status = "Ошибка обновления: ${result.exceptionOrNull()?.message ?: "нет сети"}"
                    )
                }
            }
            reload()
            startTranslationQueue()
        }
    }

    /**
     * Автоматический перевод всего, что ещё не переведено: идёт в фоне,
     * пока в очереди есть материалы. Новые материалы попадают сюда же.
     */
    fun startTranslationQueue() {
        if (queueJob?.isActive == true) return
        queueJob = viewModelScope.launch {
            _sync.update { it.copy(translating = true) }
            var failures = 0
            while (isActive) {
                val pending = withContext(Dispatchers.IO) {
                    store.pendingTranslationCount() + store.pendingSummaryCount()
                }
                if (pending == 0) break
                _sync.update {
                    it.copy(translating = true, status = "Перевод: осталось $pending…")
                }
                val done = runCatching { repo.translatePending(30) }.getOrDefault(0)
                if (done > 0) {
                    failures = 0
                } else {
                    failures++
                    if (failures >= 2) {
                        _sync.update { it.copy(status = "Перевод приостановлен: сервис перевода недоступен") }
                        break
                    }
                    delay(4_000)
                }
                reload()
                delay(250)
            }
            _sync.update { it.copy(translating = false) }
            reload()
        }
    }

    fun translateAll() {
        startTranslationQueue()
    }

    fun translateArticle(article: ArticleEntity) {
        viewModelScope.launch {
            _sync.update { it.copy(status = "Перевожу материал…") }
            val ok = runCatching { repo.translateOne(article.id) }.getOrDefault(false)
            _sync.update {
                it.copy(status = if (ok) "Переведено" else "Не удалось перевести, повторим позже")
            }
            reload()
        }
    }

    // ─────────────── Ридер ───────────────

    fun openArticle(article: ArticleEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.markRead(article.id) }
            reload()
        }

        readerJob?.cancel()
        _reader.value = ReaderState(
            articleId = article.id,
            loading = true,
            stage = "Открываю статью…",
            progress = 3
        )

        readerJob = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { store.byId(article.id) }
            if (cached != null && cached.fullTextRu.isNotBlank()) {
                _reader.value = ReaderState(
                    articleId = article.id,
                    loading = false,
                    progress = 100,
                    translatedText = cached.fullTextRu,
                    originalText = cached.fullText,
                    fromCache = true
                )
                return@launch
            }

            if (cached != null && cached.titleRu.isBlank() && cached.lang != "ru") {
                _reader.update { it.copy(stage = "Перевожу заголовок…", progress = 6) }
                runCatching { repo.translateOne(article.id) }
                reload()
            }

            val result = repo.readFullArticle(article.id) { stage, progress ->
                _reader.update { it.copy(stage = stage, progress = progress) }
            }

            _reader.update {
                it.copy(
                    loading = false,
                    progress = 100,
                    translatedText = result.translatedText,
                    originalText = result.originalText,
                    error = result.error
                )
            }
            reload()
        }
    }

    fun closeArticle() {
        readerJob?.cancel()
        readerJob = null
        _reader.value = ReaderState()
    }

    fun toggleReaderOriginal() {
        _reader.update { it.copy(showOriginal = !it.showOriginal) }
    }

    fun retryReader() {
        val id = _reader.value.articleId
        if (id == 0L) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clearFullText(id) }
            val article = withContext(Dispatchers.IO) { store.byId(id) } ?: return@launch
            openArticle(article)
        }
    }

    // ─────────────── Фильтры ───────────────

    fun setQuery(value: String) = _filters.update { it.copy(query = value) }

    fun setCategory(value: String) = _filters.update { it.copy(category = value) }

    fun toggleSavedOnly() = _filters.update { it.copy(savedOnly = !it.savedOnly) }

    fun markRead(article: ArticleEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.markRead(article.id) }
            reload()
        }
    }

    fun toggleSaved(article: ArticleEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setSaved(article.id, !article.isSaved) }
            reload()
        }
    }

    // ─────────────── Обновления ───────────────

    fun checkUpdate(manual: Boolean = true) {
        if (_update.value.checking || _update.value.downloading) return
        viewModelScope.launch {
            _update.update { it.copy(checking = true, message = null, error = null) }
            val info = updateManager.check()
            val skipped = info != null && prefs.skippedVersion == info.version
            _update.update {
                it.copy(
                    checking = false,
                    info = if (skipped) null else info,
                    message = when {
                        info != null && !skipped -> "Доступна версия ${info.version}"
                        manual && info == null -> "У вас последняя версия ($versionName)"
                        else -> null
                    }
                )
            }
        }
    }

    fun skipVersion(version: String) {
        prefs.skippedVersion = version
        _update.update { it.copy(info = null, message = null) }
    }

    fun downloadAndInstall() {
        val info = _update.value.info ?: return
        if (_update.value.downloading) return
        viewModelScope.launch {
            _update.update { it.copy(downloading = true, progress = 0, error = null) }
            val file = updateManager.download(info) { progress ->
                _update.update { it.copy(progress = progress) }
            }
            if (file == null) {
                _update.update { it.copy(downloading = false, error = "Не удалось скачать APK. Проверьте сеть.") }
                return@launch
            }
            if (!updateManager.verifyApk(file)) {
                _update.update {
                    it.copy(
                        downloading = false,
                        error = "Подпись APK не совпадает с установленной — установка отменена"
                    )
                }
                return@launch
            }
            _update.update { it.copy(downloading = false, progress = 100, message = "Устанавливаю…") }
            val started = updateManager.install(file)
            if (!started) {
                _update.update {
                    it.copy(
                        message = "Разрешите установку из этого источника и нажмите «Установить» ещё раз"
                    )
                }
            }
        }
    }

    fun dismissUpdateBanner() {
        _update.update { it.copy(info = null, message = null, error = null) }
    }

    // ─────────────── Настройки ───────────────

    fun setAutoUpdate(value: Boolean) {
        prefs.autoUpdate = value
        _settings.update { it.copy(autoUpdate = value) }
        if (value) checkUpdate(manual = false)
    }

    fun setAutoInstall(value: Boolean) {
        prefs.autoInstall = value
        _settings.update { it.copy(autoInstall = value) }
    }

    fun setNotifications(value: Boolean) {
        prefs.notifications = value
        _settings.update { it.copy(notifications = value) }
    }

    fun setWifiOnly(value: Boolean) {
        prefs.wifiOnly = value
        _settings.update { it.copy(wifiOnly = value) }
        WorkScheduler.rescheduleSync(getApplication(), prefs.syncIntervalHours, value)
    }

    fun setSyncInterval(hours: Int) {
        prefs.syncIntervalHours = hours
        _settings.update { it.copy(syncIntervalHours = hours) }
        WorkScheduler.rescheduleSync(getApplication(), hours, prefs.wifiOnly)
    }

    fun setMaxItems(value: Int) {
        prefs.maxItemsPerSource = value
        _settings.update { it.copy(maxItemsPerSource = value) }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        prefs.setSourceEnabled(id, enabled)
        _settings.update { it.copy(disabledSources = prefs.disabledSources()) }
    }

    fun resetSources() {
        prefs.resetSources()
        _settings.update { it.copy(disabledSources = emptySet()) }
    }

    fun openInstallPermission() {
        updateManager.openInstallPermissionSettings()
    }
}
