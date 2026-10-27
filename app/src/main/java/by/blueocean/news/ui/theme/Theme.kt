package by.blueocean.news.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BluePrimary = Color(0xFF0B63F6)
private val CyanAccent = Color(0xFF22C7E5)
private val DeepNavy = Color(0xFF0A101C)

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF00174A),
    secondary = Color(0xFF00687A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF4FF),
    tertiary = Color(0xFF7B3FF2),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF11151C),
    surface = Color.White,
    onSurface = Color(0xFF11151C),
    surfaceVariant = Color(0xFFE7ECF5),
    onSurfaceVariant = Color(0xFF414B5C),
    outline = Color(0xFF9AA6BA),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EA8FF),
    onPrimary = Color(0xFF00264F),
    primaryContainer = Color(0xFF143A6B),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = CyanAccent,
    onSecondary = Color(0xFF00313B),
    secondaryContainer = Color(0xFF123C46),
    tertiary = Color(0xFFC7A6FF),
    background = DeepNavy,
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF121A29),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1C2739),
    onSurfaceVariant = Color(0xFFB4C0D4),
    outline = Color(0xFF5A677C),
    error = Color(0xFFFFB4AB)
)

@Composable
fun BlueOceanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            val activity = (context as? Activity) ?: return@SideEffect
            val window = activity.window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
