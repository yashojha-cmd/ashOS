package app.ash.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Animates from 0 to [target] with a soft spring the first time, and between values after that. */
@Composable
private fun animatedFraction(target: Float): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target.coerceIn(0f, 1f), spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) }
    return anim.value
}

/**
 * Circular gauge: a full track with an arc for [fraction] (0–1). The [content] sits in the centre.
 * Always pair it with text; colour alone never carries the meaning.
 */
@Composable
fun RingGauge(
    fraction: Float,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    stroke: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val value = animatedFraction(fraction)
    Box(modifier.size(size).semantics(mergeDescendants = true) { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (value > 0f) drawArc(color, -90f, 360f * value, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** A small line chart with a soft area fill, for a balance or spend trend. Needs at least 2 points. */
@Composable
fun Sparkline(points: List<Float>, color: Color, description: String, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val reveal = animatedFraction(1f)
    Canvas(modifier.semantics { contentDescription = description }) {
        val min = points.min()
        val max = points.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (points.size - 1)
        fun y(v: Float) = size.height - ((v - min) / range) * size.height * 0.9f - size.height * 0.05f
        val visible = (points.size * reveal).toInt().coerceIn(2, points.size)
        val line = Path().apply {
            moveTo(0f, y(points[0]))
            for (i in 1 until visible) lineTo(i * stepX, y(points[i]))
        }
        val area = Path().apply {
            addPath(line)
            lineTo((visible - 1) * stepX, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 3.5.dp.toPx(), Offset((visible - 1) * stepX, y(points[visible - 1])))
    }
}

/** Donut of shares; slices are drawn in order with small gaps. */
@Composable
fun Donut(values: List<Pair<Float, Color>>, description: String, modifier: Modifier = Modifier, size: Dp = 120.dp, stroke: Dp = 18.dp) {
    val total = values.sumOf { it.first.toDouble() }.toFloat().takeIf { it > 0f } ?: return
    val reveal = animatedFraction(1f)
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val w = stroke.toPx()
        val inset = w / 2
        val arcSize = Size(this.size.width - w, this.size.height - w)
        var start = -90f
        val gap = if (values.size > 1) 2.5f else 0f
        for ((value, color) in values) {
            val sweep = (value / total) * 360f * reveal
            if (sweep > gap) drawArc(color, start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

/** Vertical bars (e.g. spend per day). [highlightIndex] marks today. */
@Composable
fun BarChart(values: List<Float>, color: Color, description: String, modifier: Modifier = Modifier, highlightIndex: Int? = null) {
    if (values.isEmpty()) return
    val reveal = animatedFraction(1f)
    val muted = color.copy(alpha = 0.35f)
    Canvas(modifier.semantics { contentDescription = description }) {
        val max = values.max().takeIf { it > 0f } ?: 1f
        val slot = size.width / values.size
        val barWidth = (slot * 0.62f).coerceAtLeast(2f)
        values.forEachIndexed { i, v ->
            val h = (v / max) * size.height * reveal
            val x = i * slot + (slot - barWidth) / 2
            drawRoundRect(
                if (highlightIndex == null || i == highlightIndex) color else muted,
                topLeft = Offset(x, size.height - h),
                size = Size(barWidth, h.coerceAtLeast(if (v > 0) 2f else 0f)),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

/** A calm, distinguishable palette for category charts (paired with labels in a legend). */
@Composable
fun chartPalette(): List<Color> {
    val c = MaterialTheme.colorScheme
    val s = AshTheme.status
    return listOf(c.primary, c.tertiary, s.soon, s.ok, s.info, c.secondary, s.urgent)
}
