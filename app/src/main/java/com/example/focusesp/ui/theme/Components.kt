package com.example.focusesp.ui.theme

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

// ---------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------

/**
 * The primary grouped container across the app: a subtle vertical surface
 * gradient, a hairline border, and an optional accent hairline along the top
 * edge for status-bearing cards.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(scheme.surfaceContainerHigh, scheme.surface)))
            .border(BorderStroke(1.dp, scheme.outline.copy(alpha = 0.7f)), shape)
    ) {
        if (accent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent, accent.copy(alpha = 0.0f))
                        )
                    )
            )
        }
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Flatter rounded card used for list-style grouped content. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)), shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** Rounded-square accent tile used behind icons for visual hierarchy. */
@Composable
fun IconBadge(
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2.8f))
            .background(accent.copy(alpha = 0.14f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.22f)), RoundedCornerShape(size / 2.8f)),
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ---------------------------------------------------------------------------
// Labels & text
// ---------------------------------------------------------------------------

/** Small uppercase section label. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
    )
}

/** Section heading with an optional supporting line and trailing slot. */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Compact capsule used for short statuses. */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/** Selectable filter chip. */
@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (selected) scheme.primary.copy(alpha = 0.55f) else scheme.outline.copy(alpha = 0.6f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/** Centered empty-state text block. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Metric primitives
// ---------------------------------------------------------------------------

/** Large metric tile: label, prominent value, optional caption and accent icon. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    icon: (@Composable () -> Unit)? = null
) {
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                SectionHeader(label)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (caption != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (icon != null) {
                Spacer(Modifier.width(8.dp))
                IconBadge(accent = accent, size = 42.dp) { icon() }
            }
        }
    }
}

/**
 * Thin horizontal progress bar. Pass [fraction] in 0f..1f.
 */
@Composable
fun UsageBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 7.dp
) {
    val clamped = fraction.coerceIn(0f, 1f)
    val brush = barBrush(barColor)
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(brush)
            )
        }
    }
}

/** Gradient brush for bars/rings, tuned to the palette. */
private fun barBrush(color: Color): Brush =
    if (color == ElectricBlue) {
        Brush.horizontalGradient(listOf(ElectricBlue, CyanAccent))
    } else {
        Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.72f)))
    }

/**
 * Circular progress ring with an optional centered slot. Progress is drawn from
 * the top, clockwise, with a soft gradient and rounded caps.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    strokeWidth: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val clamped = progress.coerceIn(0f, 1f)
    val gradient = if (color == ElectricBlue) {
        listOf(ElectricBlue, CyanAccent)
    } else {
        listOf(color, color.copy(alpha = 0.75f))
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidthPx = strokeWidth.toPx()
            val inset = strokeWidthPx / 2f
            val arcSize = Size(this.size.width - strokeWidthPx, this.size.height - strokeWidthPx)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
            if (clamped > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = gradient + gradient.first(),
                        center = center
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/**
 * Compact 7-bar column chart. [values] are already normalised to 0f..1f.
 */
@Composable
fun MiniBarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 96.dp,
    highlightIndex: Int = -1
) {
    Row(
        modifier = modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { index, raw ->
            val value = raw.coerceIn(0f, 1f)
            val highlighted = index == highlightIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(trackColor),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (value > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(value)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (highlighted) {
                                        Brush.verticalGradient(listOf(CyanAccent, ElectricBlue))
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(barColor.copy(alpha = 0.75f), barColor.copy(alpha = 0.6f))
                                        )
                                    }
                                )
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = labels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (highlighted) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------

/** Segmented pill selector. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(scheme.surfaceContainerHigh)
            .border(BorderStroke(1.dp, scheme.outline.copy(alpha = 0.55f)), RoundedCornerShape(15.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) {
                            Brush.horizontalGradient(listOf(ElectricBlue, ElectricBlueDeep))
                        } else {
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .clickable(enabled = enabled && !selected) { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        scheme.onPrimary
                    } else {
                        if (enabled) scheme.onSurfaceVariant else scheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Filled, high-emphasis action with a restrained blue gradient. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color? = null,
    containerColor: Color? = null,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val useGradient = containerColor == null
    val background = if (enabled) {
        Brush.horizontalGradient(listOf(ElectricBlue, ElectricBlueDeep))
    } else {
        Brush.horizontalGradient(listOf(scheme.surfaceContainerHighest, scheme.surfaceContainerHighest))
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(52.dp)
            .then(if (useGradient) Modifier.background(background, shape) else Modifier),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor ?: Color.Transparent,
            contentColor = contentColor ?: scheme.onPrimary,
            disabledContainerColor = if (useGradient) Color.Transparent else scheme.surfaceContainerHighest,
            disabledContentColor = scheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Outlined, low-emphasis action. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (enabled) scheme.outline else scheme.outline.copy(alpha = 0.4f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = scheme.secondary,
            disabledContentColor = scheme.onSurfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Label + / − numeric stepper, shared by durations, counts and goals. */
@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
    step: Int = 1,
    valueWidth: Dp = 48.dp,
    valueFormatter: (Int) -> String = { it.toString() }
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StepperIconButton(
            enabled = enabled && value - step >= min,
            onClick = { onValueChange((value - step).coerceIn(min, max)) }
        ) { IconMinus(tint = MaterialTheme.colorScheme.onSurface) }
        Box(modifier = Modifier.width(valueWidth), contentAlignment = Alignment.Center) {
            Text(
                text = valueFormatter(value),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        StepperIconButton(
            enabled = enabled && value + step <= max,
            onClick = { onValueChange((value + step).coerceIn(min, max)) }
        ) { IconPlus(tint = MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun StepperIconButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) { content() }
    }
}

// ---------------------------------------------------------------------------
// Banners & rows
// ---------------------------------------------------------------------------

enum class BannerTone { INFO, SUCCESS, WARNING, DANGER }

/** Inline informational banner with tone-dependent colors. */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.INFO,
    title: String? = null,
    icon: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        BannerTone.INFO -> scheme.surfaceContainerHigh to scheme.onSurface
        BannerTone.SUCCESS -> scheme.primaryContainer to scheme.onPrimaryContainer
        BannerTone.WARNING -> AmberContainer to OnAmberContainer
        BannerTone.DANGER -> scheme.errorContainer to scheme.onErrorContainer
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .border(BorderStroke(1.dp, content.copy(alpha = 0.14f)), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (icon != null) {
            Box(modifier = Modifier.padding(end = 10.dp)) { icon() }
        }
        Column(modifier = Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = content)
                Spacer(Modifier.height(3.dp))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = content)
        }
    }
}

/**
 * Tappable navigation / list row with optional leading icon and trailing slot.
 */
@Composable
fun NavRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Label + description + switch. */
@Composable
fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// ---------------------------------------------------------------------------
// Misc
// ---------------------------------------------------------------------------

/** Small colored dot for legends. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/** Rounded square badge for counts. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

/** App icon with a rounded fallback showing the app's initial. */
@Composable
fun AppAvatar(
    label: String,
    icon: Drawable?,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val shape = RoundedCornerShape(size / 3.2f)
    if (icon != null) {
        Image(
            bitmap = icon.toBitmap(96, 96).asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size).clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Terminal-style panel for diagnostic/log lines: a dark inset surface, a
 * hairline border and alternating row tinting for readability.
 */
@Composable
fun LogPanel(
    entries: List<String>,
    modifier: Modifier = Modifier,
    emphasizeFirst: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.background)
            .border(BorderStroke(1.dp, scheme.outline.copy(alpha = 0.55f)), shape)
    ) {
        entries.forEachIndexed { index, entry ->
            Text(
                text = entry,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (emphasizeFirst && index == 0) scheme.onSurface else scheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index % 2 == 1) scheme.surfaceContainerHigh.copy(alpha = 0.45f) else Color.Transparent
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

/** Muted explanatory note. */
@Composable
fun NoteCard(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

/** Label + value row used in compact info blocks. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
