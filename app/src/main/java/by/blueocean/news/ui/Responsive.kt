package by.blueocean.news.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowClass { Compact, Medium, Expanded }

/**
 * Параметры окна: используются для адаптации отступов, числа колонок и ширины контента
 * на телефонах, складных устройствах, планшетах и в альбомной ориентации.
 */
data class WindowInfo(
    val widthClass: WindowClass,
    val widthDp: Int,
    val heightDp: Int
) {
    val isExpanded: Boolean get() = widthClass == WindowClass.Expanded
    val isCompact: Boolean get() = widthClass == WindowClass.Compact
    val isLandscape: Boolean get() = widthDp > heightDp
    val isShort: Boolean get() = heightDp < 480
    val edgePadding: Dp = when (widthClass) {
        WindowClass.Compact -> 16.dp
        WindowClass.Medium -> 24.dp
        WindowClass.Expanded -> 32.dp
    }

    /** Колонки карточек: на планшете лента читается лучше в две колонки. */
    val columns: Int = if (widthClass == WindowClass.Expanded) 2 else 1

    /** Максимальная ширина контента, чтобы строки не растягивались на весь планшет. */
    val contentMaxWidth: Dp = when (widthClass) {
        WindowClass.Compact -> 640.dp
        WindowClass.Medium -> 820.dp
        WindowClass.Expanded -> 1180.dp
    }
}

@Composable
fun rememberWindowInfo(): WindowInfo {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    return remember(width, height) {
        val windowClass = when {
            width >= 840 -> WindowClass.Expanded
            width >= 600 -> WindowClass.Medium
            else -> WindowClass.Compact
        }
        WindowInfo(windowClass, width, height)
    }
}

/** Стандартные отступы списка: одинаковые на всех экранах. */
fun WindowInfo.listPadding(bottom: Dp = 24.dp): PaddingValues = PaddingValues(
    start = edgePadding,
    end = edgePadding,
    top = 4.dp,
    bottom = bottom
)

/**
 * Контейнер экрана: центрирует содержимое и ограничивает ширину на больших экранах.
 */
@Composable
fun AppScreen(
    window: WindowInfo,
    modifier: Modifier = Modifier,
    maxWidth: Dp = window.contentMaxWidth,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            content()
        }
    }
}
