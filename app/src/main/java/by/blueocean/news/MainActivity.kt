package by.blueocean.news

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import by.blueocean.news.ui.AppRoot
import by.blueocean.news.ui.AppViewModel
import by.blueocean.news.ui.theme.BlueOceanTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* отказ не мешает работе приложения */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openUpdates = intent?.getBooleanExtra(EXTRA_OPEN_UPDATES, false) ?: false

        setContent {
            BlueOceanTheme {
                val viewModel: AppViewModel = viewModel()
                AppRoot(viewModel = viewModel, openUpdatesInitially = openUpdates)
            }
        }

        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_UPDATES = "open_updates"
    }
}
