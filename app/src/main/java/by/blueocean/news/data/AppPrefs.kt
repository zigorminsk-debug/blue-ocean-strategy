package by.blueocean.news.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Настройки приложения (SharedPreferences — без лишних зависимостей).
 */
class AppPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("blue_ocean_prefs", Context.MODE_PRIVATE)

    var autoUpdate: Boolean
        get() = sp.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_UPDATE, value).apply()

    var autoInstall: Boolean
        get() = sp.getBoolean(KEY_AUTO_INSTALL, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_INSTALL, value).apply()

    var notifications: Boolean
        get() = sp.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = sp.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    var wifiOnly: Boolean
        get() = sp.getBoolean(KEY_WIFI_ONLY, false)
        set(value) = sp.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var syncIntervalHours: Int
        get() = sp.getInt(KEY_SYNC_INTERVAL, 3)
        set(value) = sp.edit().putInt(KEY_SYNC_INTERVAL, value).apply()

    var maxItemsPerSource: Int
        get() = sp.getInt(KEY_MAX_ITEMS, 25)
        set(value) = sp.edit().putInt(KEY_MAX_ITEMS, value).apply()

    var lastSyncAt: Long
        get() = sp.getLong(KEY_LAST_SYNC, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_SYNC, value).apply()

    var lastSyncInfo: String
        get() = sp.getString(KEY_LAST_SYNC_INFO, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_SYNC_INFO, value).apply()

    var skippedVersion: String
        get() = sp.getString(KEY_SKIPPED_VERSION, "") ?: ""
        set(value) = sp.edit().putString(KEY_SKIPPED_VERSION, value).apply()

    fun isSourceEnabled(id: String): Boolean {
        val disabled = sp.getStringSet(KEY_DISABLED_SOURCES, emptySet()) ?: emptySet()
        return !disabled.contains(id)
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        val current = (sp.getStringSet(KEY_DISABLED_SOURCES, emptySet()) ?: emptySet()).toMutableSet()
        if (enabled) current.remove(id) else current.add(id)
        sp.edit().putStringSet(KEY_DISABLED_SOURCES, current).apply()
    }

    fun disabledSources(): Set<String> =
        sp.getStringSet(KEY_DISABLED_SOURCES, emptySet()) ?: emptySet()

    fun resetSources() {
        sp.edit().remove(KEY_DISABLED_SOURCES).apply()
    }

    companion object {
        private const val KEY_AUTO_UPDATE = "auto_update"
        private const val KEY_AUTO_INSTALL = "auto_install"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_SYNC_INTERVAL = "sync_interval_hours"
        private const val KEY_MAX_ITEMS = "max_items"
        private const val KEY_LAST_SYNC = "last_sync_at"
        private const val KEY_LAST_SYNC_INFO = "last_sync_info"
        private const val KEY_DISABLED_SOURCES = "disabled_sources"
        private const val KEY_SKIPPED_VERSION = "skipped_version"
    }
}
