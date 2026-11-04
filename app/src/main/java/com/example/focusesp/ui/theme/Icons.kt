package com.example.focusesp.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn, single-weight line icons on a 24-unit grid. Keeping them in-repo
 * (rather than pulling in the extended Material icon set) keeps the app small
 * and gives every glyph a consistent, minimal stroke language.
 */

private const val GRID = 24f

@Composable
private fun LineIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    draw: DrawScope.(u: Float, tint: Color) -> Unit
) {
    Canvas(modifier = modifier.size(24.dp)) {
        draw(size.minDimension / GRID, tint)
    }
}

private fun DrawScope.stroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
fun IconHome(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.9f
    val roof = Path().apply {
        moveTo(4 * u, 11 * u); lineTo(12 * u, 4 * u); lineTo(20 * u, 11 * u)
    }
    drawPath(roof, c, style = stroke(w))
    drawLine(c, Offset(6.5f * u, 10 * u), Offset(6.5f * u, 19.5f * u), w, StrokeCap.Round)
    drawLine(c, Offset(17.5f * u, 10 * u), Offset(17.5f * u, 19.5f * u), w, StrokeCap.Round)
    drawLine(c, Offset(6.5f * u, 19.5f * u), Offset(17.5f * u, 19.5f * u), w, StrokeCap.Round)
}

@Composable
fun IconChart(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 2.4f
    drawLine(c, Offset(6 * u, 20 * u), Offset(6 * u, 13 * u), w, StrokeCap.Round)
    drawLine(c, Offset(12 * u, 20 * u), Offset(12 * u, 8 * u), w, StrokeCap.Round)
    drawLine(c, Offset(18 * u, 20 * u), Offset(18 * u, 11 * u), w, StrokeCap.Round)
    drawLine(c, Offset(3.5f * u, 20 * u), Offset(20.5f * u, 20 * u), w * 0.5f, StrokeCap.Round)
}

@Composable
fun IconTimer(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.9f
    drawCircle(c, radius = 7.2f * u, center = Offset(12 * u, 13.5f * u), style = stroke(w))
    drawLine(c, Offset(12 * u, 13.5f * u), Offset(12 * u, 8.8f * u), w, StrokeCap.Round)
    drawLine(c, Offset(12 * u, 13.5f * u), Offset(15.4f * u, 15 * u), w, StrokeCap.Round)
    drawLine(c, Offset(9.5f * u, 3.2f * u), Offset(14.5f * u, 3.2f * u), w, StrokeCap.Round)
}

@Composable
fun IconMore(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val r = 2f * u
    listOf(7.5f, 16.5f).forEach { x ->
        listOf(7.5f, 16.5f).forEach { y ->
            drawCircle(c, radius = r, center = Offset(x * u, y * u))
        }
    }
}

@Composable
fun IconPlay(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val p = Path().apply {
        moveTo(8 * u, 5 * u); lineTo(19 * u, 12 * u); lineTo(8 * u, 19 * u); close()
    }
    drawPath(p, c)
}

@Composable
fun IconPause(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 3f
    drawLine(c, Offset(9 * u, 6 * u), Offset(9 * u, 18 * u), w, StrokeCap.Round)
    drawLine(c, Offset(15 * u, 6 * u), Offset(15 * u, 18 * u), w, StrokeCap.Round)
}

@Composable
fun IconStop(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    drawRoundRect(c, topLeft = Offset(7 * u, 7 * u), size = Size(10 * u, 10 * u), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * u))
}

@Composable
fun IconCheck(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 2.1f
    val p = Path().apply {
        moveTo(5 * u, 13 * u); lineTo(10 * u, 18 * u); lineTo(19 * u, 6.5f * u)
    }
    drawPath(p, c, style = stroke(w))
}

@Composable
fun IconShield(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val p = Path().apply {
        moveTo(12 * u, 3 * u); lineTo(19.5f * u, 6 * u); lineTo(19.5f * u, 12 * u)
        lineTo(12 * u, 21 * u); lineTo(4.5f * u, 12 * u); lineTo(4.5f * u, 6 * u); close()
    }
    drawPath(p, c, style = stroke(u * 1.9f))
}

@Composable
fun IconRefresh(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.9f
    drawArc(
        color = c,
        startAngle = 40f,
        sweepAngle = 290f,
        useCenter = false,
        topLeft = Offset(5 * u, 5 * u),
        size = Size(14 * u, 14 * u),
        style = stroke(w)
    )
    val head = Path().apply {
        moveTo(18.5f * u, 4 * u); lineTo(19.5f * u, 9.5f * u); lineTo(14 * u, 8 * u); close()
    }
    drawPath(head, c)
}

@Composable
fun IconChevronRight(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val p = Path().apply {
        moveTo(9.5f * u, 6 * u); lineTo(15.5f * u, 12 * u); lineTo(9.5f * u, 18 * u)
    }
    drawPath(p, c, style = stroke(u * 2f))
}

@Composable
fun IconLock(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.9f
    drawRoundRect(
        c,
        topLeft = Offset(6 * u, 11 * u),
        size = Size(12 * u, 9 * u),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * u),
        style = stroke(w)
    )
    drawArc(
        color = c,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(8 * u, 5.5f * u),
        size = Size(8 * u, 9 * u),
        style = stroke(w)
    )
}

@Composable
fun IconBolt(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val p = Path().apply {
        moveTo(13 * u, 2.5f * u); lineTo(5.5f * u, 13 * u); lineTo(11 * u, 13 * u)
        lineTo(9.5f * u, 21.5f * u); lineTo(18.5f * u, 10 * u); lineTo(12.5f * u, 10 * u); close()
    }
    drawPath(p, c)
}

@Composable
fun IconCalendar(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.9f
    drawRoundRect(
        c,
        topLeft = Offset(4 * u, 6 * u),
        size = Size(16 * u, 14 * u),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * u),
        style = stroke(w)
    )
    drawLine(c, Offset(4 * u, 10.5f * u), Offset(20 * u, 10.5f * u), w, StrokeCap.Round)
    drawLine(c, Offset(9 * u, 3.5f * u), Offset(9 * u, 7 * u), w, StrokeCap.Round)
    drawLine(c, Offset(15 * u, 3.5f * u), Offset(15 * u, 7 * u), w, StrokeCap.Round)
}

@Composable
fun IconTarget(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.9f
    drawCircle(c, radius = 8 * u, center = Offset(12 * u, 12 * u), style = stroke(w))
    drawCircle(c, radius = 3.6f * u, center = Offset(12 * u, 12 * u), style = stroke(w))
    drawCircle(c, radius = 1.3f * u, center = Offset(12 * u, 12 * u))
}

@Composable
fun IconBlock(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.9f
    drawCircle(c, radius = 8 * u, center = Offset(12 * u, 12 * u), style = stroke(w))
    drawLine(c, Offset(6.4f * u, 6.4f * u), Offset(17.6f * u, 17.6f * u), w, StrokeCap.Round)
}

@Composable
fun IconSearch(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 2f
    drawCircle(c, radius = 6 * u, center = Offset(11 * u, 11 * u), style = stroke(w))
    drawLine(c, Offset(15.5f * u, 15.5f * u), Offset(20 * u, 20 * u), w, StrokeCap.Round)
}

@Composable
fun IconSettings(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.9f
    drawCircle(c, radius = 3 * u, center = Offset(12 * u, 12 * u), style = stroke(w))
    drawCircle(c, radius = 8 * u, center = Offset(12 * u, 12 * u), style = stroke(w))
}

@Composable
fun IconPlus(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 2.1f
    drawLine(c, Offset(12 * u, 5 * u), Offset(12 * u, 19 * u), w, StrokeCap.Round)
    drawLine(c, Offset(5 * u, 12 * u), Offset(19 * u, 12 * u), w, StrokeCap.Round)
}

@Composable
fun IconMinus(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    drawLine(c, Offset(5 * u, 12 * u), Offset(19 * u, 12 * u), u * 2.1f, StrokeCap.Round)
}

@Composable
fun IconClose(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 2.1f
    drawLine(c, Offset(6 * u, 6 * u), Offset(18 * u, 18 * u), w, StrokeCap.Round)
    drawLine(c, Offset(18 * u, 6 * u), Offset(6 * u, 18 * u), w, StrokeCap.Round)
}

@Composable
fun IconClock(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.9f
    drawCircle(c, radius = 8 * u, center = Offset(12 * u, 12 * u), style = stroke(w))
    drawLine(c, Offset(12 * u, 12 * u), Offset(12 * u, 7.5f * u), w, StrokeCap.Round)
    drawLine(c, Offset(12 * u, 12 * u), Offset(15.5f * u, 13.8f * u), w, StrokeCap.Round)
}

@Composable
fun IconEye(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.8f
    val p = Path().apply {
        moveTo(2.5f * u, 12 * u)
        cubicTo(5 * u, 6.5f * u, 19 * u, 6.5f * u, 21.5f * u, 12 * u)
        cubicTo(19 * u, 17.5f * u, 5 * u, 17.5f * u, 2.5f * u, 12 * u)
        close()
    }
    drawPath(p, c, style = stroke(w))
    drawCircle(c, radius = 3 * u, center = Offset(12 * u, 12 * u), style = stroke(w))
}

@Composable
fun IconWifi(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.9f
    drawArc(c, 215f, 110f, false, topLeft = Offset(6 * u, 8 * u), size = Size(12 * u, 12 * u), style = stroke(w))
    drawArc(c, 215f, 110f, false, topLeft = Offset(8.5f * u, 11.5f * u), size = Size(7 * u, 7 * u), style = stroke(w))
    drawCircle(c, radius = 1.3f * u, center = Offset(12 * u, 18.5f * u))
}

@Composable
fun IconBell(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.8f
    val p = Path().apply {
        moveTo(6 * u, 9.5f * u)
        cubicTo(6 * u, 5.5f * u, 8.5f * u, 3.5f * u, 12 * u, 3.5f * u)
        cubicTo(15.5f * u, 3.5f * u, 18 * u, 5.5f * u, 18 * u, 9.5f * u)
        lineTo(18 * u, 14 * u)
        lineTo(19.5f * u, 17 * u)
        lineTo(4.5f * u, 17 * u)
        lineTo(6 * u, 14 * u)
        close()
    }
    drawPath(p, c, style = stroke(w))
    drawArc(c, 0f, 180f, false, topLeft = Offset(9.5f * u, 16 * u), size = Size(5 * u, 5 * u), style = stroke(w))
}

@Composable
fun IconLayers(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.8f
    val top = Path().apply {
        moveTo(12 * u, 3.5f * u); lineTo(20.5f * u, 8 * u); lineTo(12 * u, 12.5f * u); lineTo(3.5f * u, 8 * u); close()
    }
    drawPath(top, c, style = stroke(w))
    drawLine(c, Offset(3.5f * u, 12.5f * u), Offset(12 * u, 17 * u), w, StrokeCap.Round)
    drawLine(c, Offset(12 * u, 17 * u), Offset(20.5f * u, 12.5f * u), w, StrokeCap.Round)
}

@Composable
fun IconInfo(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.9f
    drawCircle(c, radius = 8 * u, center = Offset(12 * u, 12 * u), style = stroke(w))
    drawLine(c, Offset(12 * u, 11 * u), Offset(12 * u, 16.5f * u), w, StrokeCap.Round)
    drawCircle(c, radius = 1.1f * u, center = Offset(12 * u, 7.8f * u))
}

@Composable
fun IconWarning(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) = LineIcon(modifier, tint) { u, c ->
    val w = u * 1.9f
    val p = Path().apply {
        moveTo(12 * u, 3.5f * u); lineTo(21 * u, 19.5f * u); lineTo(3 * u, 19.5f * u); close()
    }
    drawPath(p, c, style = stroke(w))
    drawLine(c, Offset(12 * u, 9.5f * u), Offset(12 * u, 14 * u), w, StrokeCap.Round)
    drawCircle(c, radius = 1.1f * u, center = Offset(12 * u, 16.6f * u))
}
