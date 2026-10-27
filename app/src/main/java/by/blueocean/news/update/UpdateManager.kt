package by.blueocean.news.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val json = latestReleaseJson() ?: return@withContext null
            val tag = json.optString("tag_name").removePrefix("v").removePrefix("V")
            if (tag.isBlank()) return@withContext null
            if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@withContext null

            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl = ""
            var size = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    size = asset.optLong("size")
                    break
                }
            }
            if (apkUrl.isBlank()) return@withContext null

            UpdateInfo(
                version = tag,
                notes = json.optString("body").take(1500),
                apkUrl = apkUrl,
                apkSize = size,
                publishedAt = json.optString("published_at")
            )
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Берем последний стабильный релиз; если его ещё нет — самую свежую
     * prerelease-сборку (dev-build), чтобы автообновление работало с самого начала.
     */
    private fun latestReleaseJson(): JSONObject? {
        val stable = Http.getJson("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
        if (stable != null) {
            val json = runCatching { JSONObject(stable) }.getOrNull()
            if (json != null && json.optString("tag_name").isNotBlank()) return json
        }
        val list = Http.getJson("https://api.github.com/repos/$OWNER/$REPO/releases?per_page=10")
            ?: return null
        val array = runCatching { JSONArray(list) }.getOrNull() ?: return null
        var fallback: JSONObject? = null
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            if (json.optBoolean("draft")) continue
            if (!json.optBoolean("prerelease") && json.optString("tag_name").isNotBlank()) return json
            if (fallback == null) fallback = json
        }
        return fallback
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
        const val OWNER = "zigorminsk-debug"
        const val REPO = "blue-ocean-strategy"

        fun releasesPage(): String = "https://github.com/$OWNER/$REPO/releases/latest"

        fun downloadPage(): String = releasesPage()

        /** Сравнение версий вида 1.2.3: true, если remote новее local. */
        fun isNewer(remote: String, local: String): Boolean {
            val r = parse(remote)
            val l = parse(local)
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }
                val b = l.getOrElse(i) { 0 }
                if (a > b) return true
                if (a < b) return false
            }
            return false
        }

        private fun parse(version: String): List<Int> =
            version.trim()
                .substringBefore('-')
                .split('.')
                .mapNotNull { it.toIntOrNull() }
    }
}
