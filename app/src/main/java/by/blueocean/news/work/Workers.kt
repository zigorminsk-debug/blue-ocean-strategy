package by.blueocean.news.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import by.blueocean.news.data.AppPrefs
import by.blueocean.news.data.NewsRepository
import by.blueocean.news.update.UpdateCheck
import by.blueocean.news.update.UpdateManager
import by.blueocean.news.util.Notifications
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val prefs = AppPrefs(applicationContext)
            val result = NewsRepository.get(applicationContext).sync()
            if (result.inserted > 0 && prefs.notifications) {
                Notifications.notifyNews(applicationContext, result.inserted)
            }
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}

/**
 * Догоняющий перевод: добирает всё, что осталось без русского перевода
 * (старые материалы, неудачные попытки, отключённая сеть).
 */
class TranslationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val repo = NewsRepository.get(applicationContext)
            repo.translatePending(limit = 40)
            Result.success()
        } catch (t: Throwable) {
            Result.success()
        }
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val prefs = AppPrefs(applicationContext)
            if (!prefs.autoUpdate) return Result.success()
            val result = UpdateManager(applicationContext).check()
            if (result is UpdateCheck.Available &&
                prefs.notifications &&
                prefs.skippedVersion != result.info.version
            ) {
                Notifications.notifyUpdate(applicationContext, result.info.version)
            }
            Result.success()
        } catch (t: Throwable) {
            Result.success()
        }
    }
}

object WorkScheduler {

    private const val SYNC_WORK = "blue_ocean_sync"
    private const val UPDATE_WORK = "blue_ocean_update_check"
    private const val TRANSLATION_WORK = "blue_ocean_translation"

    fun scheduleSync(context: Context, intervalHours: Int, wifiOnly: Boolean = false) {
        val hours = intervalHours.coerceAtLeast(1).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(hours, TimeUnit.HOURS) // первый синк делает приложение при запуске
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Пересоздаёт периодический синк (например, после смены интервала). */
    fun rescheduleSync(context: Context, intervalHours: Int, wifiOnly: Boolean = false) {
        val hours = intervalHours.coerceAtLeast(1).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(hours, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    fun scheduleTranslation(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<TranslationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TRANSLATION_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleUpdateCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UPDATE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
