package by.blueocean.news.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import by.blueocean.news.data.AppPrefs
import by.blueocean.news.data.NewsRepository
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

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val prefs = AppPrefs(applicationContext)
            if (!prefs.autoUpdate) return Result.success()
            val info = UpdateManager(applicationContext).check()
            if (info != null && prefs.notifications && prefs.skippedVersion != info.version) {
                Notifications.notifyUpdate(applicationContext, info.version)
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

    fun scheduleSync(context: Context, intervalHours: Int, wifiOnly: Boolean = false) {
        val hours = intervalHours.coerceAtLeast(1).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleUpdateCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UPDATE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun syncNow(context: Context) {
        val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
