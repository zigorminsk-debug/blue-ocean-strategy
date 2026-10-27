package by.blueocean.news

import android.app.Application
import by.blueocean.news.data.AppPrefs
import by.blueocean.news.util.Notifications
import by.blueocean.news.work.WorkScheduler

class BlueOceanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        val prefs = AppPrefs(this)
        WorkScheduler.scheduleSync(this, prefs.syncIntervalHours, prefs.wifiOnly)
        WorkScheduler.scheduleTranslation(this)
        WorkScheduler.scheduleUpdateCheck(this)
    }
}
