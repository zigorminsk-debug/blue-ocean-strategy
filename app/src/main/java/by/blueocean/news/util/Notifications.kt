package by.blueocean.news.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import by.blueocean.news.MainActivity
import by.blueocean.news.R

object Notifications {

    const val CHANNEL_NEWS = "news"
    const val CHANNEL_UPDATES = "updates"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val news = NotificationChannel(
            CHANNEL_NEWS,
            context.getString(R.string.channel_news),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_news_desc)
        }
        val updates = NotificationChannel(
            CHANNEL_UPDATES,
            context.getString(R.string.channel_updates),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_updates_desc)
        }
        manager.createNotificationChannel(news)
        manager.createNotificationChannel(updates)
    }

    fun notifyNews(context: Context, count: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_NEWS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Blue Ocean IT")
            .setContentText("$count новых материалов в ленте")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        safeNotify(context, 1001, notification)
    }

    fun notifyUpdate(context: Context, version: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Доступна версия $version")
            .setContentText("Нажмите, чтобы обновить приложение поверх текущей версии")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        safeNotify(context, 1002, notification)
    }

    private fun safeNotify(context: Context, id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // нет разрешения POST_NOTIFICATIONS — молча пропускаем
        }
    }
}
