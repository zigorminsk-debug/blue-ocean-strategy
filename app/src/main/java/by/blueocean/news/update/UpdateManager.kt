package by.blueocean.news.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import by.blueocean.news.BuildConfig
import by.blueocean.news.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Автообновление через GitHub Releases.
 *
 * Схема: CI собирает APK тем же постоянным ключом и публикует релиз.
 * Приложение раз в несколько часов спрашивает последний релиз, скачивает APK,
 * проверяет, что подпись совпадает с установленной (защита от подмены),
 * и запускает системный установщик — версия встаёт «поверх» без удаления данных.
 */
class UpdateManager(private val context: Context) {

    data class UpdateInfo(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
        val publishedAt: String
    )

    /** Результат проверки: важно отличать «обновлений нет» от «не смог проверить». */
    sealed interface UpdateCheck {
        data class Available(val info: UpdateInfo) : UpdateCheck
        data object UpToDate : UpdateCheck
        data class Failed(val reason: String) : UpdateCheck
    }

    /** Найденный релиз: версия, ссылка на APK и сам JSON релиза (для описания). */
    data class Candidate(
        val version: String,
        val apkUrl: String,
        val apkSize: Long,
        val release: JSONObject
    )

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val releases = try {
            releasesJson()
        } catch (t: Throwable) {
            null
        }
        if (releases == null) {
            Log.w(TAG, "update: список релизов недоступен")
            return@withContext UpdateCheck.Failed("GitHub не ответил (нет сети или исчерпан лимит запросов)")
        }
        val best = try {
            pickNewestApk(releases)
        } catch (t: Throwable) {
            null
        }
        if (best == null) {
            Log.w(TAG, "update: в релизах нет APK")
            return@withContext UpdateCheck.Failed("В релизах GitHub не найден APK")
        }
        Log.i(TAG, "update: installed=${BuildConfig.VERSION_NAME} latest=${best.version} url=${best.apkUrl}")
        if (!isNewer(best.version, BuildConfig.VERSION_NAME)) {
            return@withContext UpdateCheck.UpToDate
        }
        UpdateCheck.Available(
            UpdateInfo(
                version = best.version,
                notes = best.release.optString("body").take(1500),
                apkUrl = best.apkUrl,
                apkSize = best.apkSize,
                publishedAt = best.release.optString("published_at")
            )
        )
    }

    /**
     * Список релизов. Ветка публикует все сборки в один релиз с тегом `dev-build`,
     * поэтому версию берём не из тега, а из имён APK-файлов (см. [pickNewestApk]).
     */
    private fun releasesJson(): JSONArray? {
        val list = Http.getJson("https://api.github.com/repos/$OWNER/$REPO/releases?per_page=30")
        val array = list?.let { runCatching { JSONArray(it) }.getOrNull() }
        if (array != null && array.length() > 0) return array

        val single = Http.getJson("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
        val json = single?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (json != null && json.optString("tag_name").isNotBlank()) {
            return JSONArray().put(json)
        }
        return null
    }

    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit = {}): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val target = File(dir, "blue-ocean-${info.version}.apk")
                if (target.exists() && target.length() > 0 && target.length() != info.apkSize) {
                    target.delete()
                }
                if (target.exists() && target.length() > 0) {
                    onProgress(100)
                    return@withContext target
                }

                val request = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", Http.UA)
                    .header("Accept", "application/octet-stream")
                    .build()

                Http.downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val total = response.body?.contentLength() ?: info.apkSize
                    response.body?.byteStream()?.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            var done = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    onProgress(((done * 100) / total).toInt().coerceIn(0, 99))
                                }
                            }
                            output.flush()
                        }
                    }
                }
                if (target.exists() && target.length() > 0) {
                    onProgress(100)
                    target
                } else {
                    null
                }
            } catch (t: Throwable) {
                null
            }
        }

    /** Проверяет, что скачанный APK подписан тем же ключом, что и установленное приложение. */
    fun verifyApk(file: File): Boolean {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            @Suppress("DEPRECATION")
            val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
            if (archive.packageName != context.packageName) return false

            val apkSigs = signaturesOf(archive)
            @Suppress("DEPRECATION")
            val own = pm.getPackageInfo(context.packageName, flags)
            val ownSigs = signaturesOf(own)
            apkSigs.isNotEmpty() && ownSigs.isNotEmpty() && apkSigs == ownSigs
        } catch (t: Throwable) {
            false
        }
    }

    private fun signaturesOf(info: android.content.pm.PackageInfo): Set<String> {
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: return emptySet()
        }
        return certs?.mapNotNull { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }?.toSet() ?: emptySet()
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {
            }
        }
    }

    fun install(file: File): Boolean {
        if (!canInstallPackages()) {
            openInstallPermissionSettings()
            return false
        }
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }

    companion object {
        private const val TAG = "BlueOcean"

        const val OWNER = "zigorminsk-debug"
        const val REPO = "blue-ocean-strategy"

        private val VERSION_TAG = Regex("""^\d+(\.\d+)*$""")
        private val VERSION_IN_NAME = Regex("""(\d+(?:\.\d+)+)""")

        /** Страница всех релизов (у ветки нет «стабильного» релиза, /latest отдаёт 404). */
        fun releasesPage(): String = "https://github.com/$OWNER/$REPO/releases"

        fun downloadPage(): String = releasesPage()

        /** Версия из тега релиза: `v1.0.26` → `1.0.26`; `dev-build` → null. */
        fun versionOfTag(tag: String): String? {
            val clean = tag.trim().removePrefix("v").removePrefix("V")
            return if (VERSION_TAG.matches(clean)) clean else null
        }

        /** Версия из имени APK: `BlueOceanIT-1.0.25.apk` → `1.0.25`; иначе null. */
        fun versionOfAsset(name: String): String? {
            if (!name.endsWith(".apk", ignoreCase = true)) return null
            val match = VERSION_IN_NAME.find(name) ?: return null
            return versionOfTag(match.value)
        }

        /**
         * Самый свежий APK среди всех релизов.
         *
         * Версия берётся из имени файла (в рабочей ветке тег всегда `dev-build`,
         * а файлы называются `BlueOceanIT-1.0.N.apk`); если в имени версии нет —
         * из тега релиза. Черновики игнорируются, старые APK в общем релизе не мешают:
         * выбирается максимальная версия, а не первый файл в списке.
         */
        fun pickNewestApk(releases: JSONArray): Candidate? {
            var best: Candidate? = null
            for (i in 0 until releases.length()) {
                val release = releases.optJSONObject(i) ?: continue
                if (release.optBoolean("draft")) continue

                val tagVersion = versionOfTag(release.optString("tag_name"))
                val assets = release.optJSONArray("assets") ?: JSONArray()
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (url.isBlank()) continue
                    val version = versionOfAsset(name) ?: tagVersion ?: continue
                    val size = asset.optLong("size")
                    if (best == null || compareVersions(version, best.version) > 0) {
                        best = Candidate(version, url, size, release)
                    }
                }
            }
            return best
        }

        /** Сравнение версий вида 1.2.3: true, если remote новее local. */
        fun isNewer(remote: String, local: String): Boolean = compareVersions(remote, local) > 0

        /** 1.0.10 > 1.0.9; нечисловые версии считаются нулевыми. */
        fun compareVersions(a: String, b: String): Int {
            val left = parse(a)
            val right = parse(b)
            for (i in 0 until maxOf(left.size, right.size)) {
                val x = left.getOrElse(i) { 0 }
                val y = right.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }

        private fun parse(version: String): List<Int> =
            version.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .split('.')
                .mapNotNull { it.trim().toIntOrNull() }
    }
}
