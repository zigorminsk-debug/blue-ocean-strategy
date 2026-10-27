package by.blueocean.news.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Единая дизайн-система: все кнопки, чипы и иконочные действия в приложении
 * собираются из этих компонентов, поэтому выглядят и ведут себя одинаково
 * на любом экране и в любом размере.
 */
object AppShapes {
    val card: Dp = 20.dp
    val control: Dp = 14.dp
    val chip: Dp = 11.dp
    val tag: Dp = 9.dp
}

enum class AppButtonStyle {
    /** Главное действие на экране: заливка основным цветом. */
    Primary,

    /** Второстепенное действие: тональная заливка. */
    Tonal,

    /** Альтернатива главному: обводка без заливки. */
    Outline,

    /** Текстовая ссылка (третьестепенное действие). */
    Ghost,

    /** Необратимое или отменяющее действие. */
    Danger
}

enum class AppButtonSize(
    val height: Dp,
    val iconSize: Dp,
    val horizontalPadding: Dp
) {
    Small(34.dp, 15.dp, 12.dp),
    Medium(44.dp, 18.dp, 16.dp),
    Large(52.dp, 20.dp, 20.dp)
}

private data class ButtonColors(
    val container: Color,
    val content: Color,
    val border: BorderStroke?
)

@Composable
private fun colorsFor(style: AppButtonStyle, enabled: Boolean): ButtonColors {
    val scheme = MaterialTheme.colorScheme
    val disabledContainer = scheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.6f)
    val disabledContent = scheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.55f)

    val base = when (style) {
        AppButtonStyle.Primary -> ButtonColors(
            container = scheme.primary,
            content = scheme.onPrimary,
            border = null
        )
        AppButtonStyle.Tonal -> ButtonColors(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            border = null
        )
        AppButtonStyle.Outline -> ButtonColors(
            container = Color.Transparent,
            content = scheme.primary,
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.6f))
        )
        AppButtonStyle.Ghost -> ButtonColors(
            container = Color.Transparent,
            content = scheme.primary,
            border = null
        )
        AppButtonStyle.Danger -> ButtonColors(
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
            border = null
        )
    }

    return if (enabled) {
        base
    } else {
        ButtonColors(
            container = if (style == AppButtonStyle.Ghost || style == AppButtonStyle.Outline) {
                Color.Transparent
            } else {
                disabledContainer
            },
            content = disabledContent,
            border = base.border?.copy(color = scheme.outline.copy(alpha = 0.3f))
        )
    }
}

/**
 * Универсальная кнопка приложения.
 * Одинаковая высота, радиус, отступы и типографика во всех размерах.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Primary,
    size: AppButtonSize = AppButtonSize.Medium,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillWidth: Boolean = false,
    compactText: Boolean = false
) {
    val colors = colorsFor(style, enabled && !loading)
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(size.height)
            .defaultMinSize(minWidth = size.height),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(AppShapes.control),
        color = colors.container,
        contentColor = colors.content,
        border = colors.border
    ) {
        Row(
            modifier = Modifier.padding(horizontal = size.horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(size.iconSize),
                    strokeWidth = 2.dp,
                    color = colors.content
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(size.iconSize),
                    tint = colors.content
                )
            }
            if (icon != null || loading) Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = if (size == AppButtonSize.Small) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelLarge
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = if (compactText) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Кнопки в ряд; на узком экране автоматически переносятся на новую строку. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppButtonRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/** Иконочная кнопка одного вида (закладка, обновление, размер шрифта и т.п.). */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (active) scheme.primaryContainer else Color.Transparent,
        contentColor = when {
            !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
            active -> scheme.onPrimaryContainer
            else -> scheme.onSurfaceVariant
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** То же, но с текстовой меткой вместо иконки (например, А− / А+). */
@Composable
fun AppTextButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (active) scheme.primaryContainer else Color.Transparent,
        contentColor = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Чип выбора/фильтра — единый для категорий, трендов и переключателей.
 */
@Composable
fun AppChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leadingEmoji: String? = null,
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(if (compact) 32.dp else 36.dp),
        shape = RoundedCornerShape(AppShapes.chip),
        color = if (selected) scheme.primary else scheme.surface,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, scheme.outline.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingEmoji != null) {
                Text(leadingEmoji, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = if (compact) MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Небольшой статичный ярлык (категория, источник, оценка). */
@Composable
fun AppTag(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(AppShapes.tag)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
