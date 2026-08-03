package com.djand.hst.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Statistics (DESIGN.md §10.7): one metric at a time, picked from a "Squat ▾"
 * style dropdown — per-exercise working weight, bodyweight, or weekly tonnage.
 * The chart is a single primary-colored line with gridlines, a soft gradient
 * fill, dotted markers and sparse axis labels (§10.6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Box {
                OutlinedButton(onClick = { menuExpanded = true }, shape = CircleShape) {
                    val label = state.options.firstOrNull { it.key == state.selectedKey }?.label.orEmpty()
                    Text(label)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose metric")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    state.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                viewModel.select(option.key)
                                menuExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (state.points.size < 2) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Not enough data yet — complete a few workouts.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                MetricChart(
                    points = state.points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                )
            }
        }
    }
}

/** Hand-rolled single-series line chart (no chart library, per the offline/minimal rules). */
@Composable
private fun MetricChart(
    points: List<StatsViewModel.ChartPoint>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val leftPad = 48.dp.toPx()
        val rightPad = 12.dp.toPx()
        val topPad = 12.dp.toPx()
        val bottomPad = 28.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad
        if (chartW <= 0f || chartH <= 0f || points.size < 2) return@Canvas

        var minY = points.minOf { it.value }
        var maxY = points.maxOf { it.value }
        if (minY == maxY) {
            minY -= 1f
            maxY += 1f
        }
        val pad = (maxY - minY) * 0.08f
        minY = (minY - pad).coerceAtLeast(0f)
        maxY += pad

        fun xAt(index: Int) = leftPad + chartW * index / (points.size - 1)
        fun yAt(value: Float) = topPad + chartH * (1f - (value - minY) / (maxY - minY))

        // Gridlines + y-axis labels.
        for (step in 0..3) {
            val fraction = step / 3f
            val y = topPad + chartH * fraction
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width - rightPad, y),
                strokeWidth = 1.dp.toPx(),
            )
            val label = formatAxisValue(maxY - (maxY - minY) * fraction)
            val layout = textMeasurer.measure(label, labelStyle)
            drawText(
                layout,
                color = labelColor,
                topLeft = Offset(
                    leftPad - 6.dp.toPx() - layout.size.width,
                    y - layout.size.height / 2f,
                ),
            )
        }

        // Gradient fill under the line, then the line itself.
        val linePath = Path().apply {
            points.forEachIndexed { index, point ->
                val x = xAt(index)
                val y = yAt(point.value)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(xAt(points.size - 1), topPad + chartH)
            lineTo(xAt(0), topPad + chartH)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.25f), lineColor.copy(alpha = 0.02f)),
                startY = topPad,
                endY = topPad + chartH,
            ),
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Dotted markers per data point.
        points.forEachIndexed { index, point ->
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(xAt(index), yAt(point.value)),
            )
        }

        // Sparse x-axis labels.
        val step = ceil(points.size / 6f).toInt().coerceAtLeast(1)
        points.forEachIndexed { index, point ->
            if (index % step == 0 || index == points.size - 1) {
                val layout = textMeasurer.measure(point.label, labelStyle)
                val x = (xAt(index) - layout.size.width / 2f)
                    .coerceIn(0f, size.width - layout.size.width)
                drawText(
                    layout,
                    color = labelColor,
                    topLeft = Offset(x, topPad + chartH + 8.dp.toPx()),
                )
            }
        }
    }
}

private fun formatAxisValue(value: Float): String = when {
    value >= 10_000f -> "${(value / 1000f).roundToInt()}k"
    value >= 1_000f -> String.format(Locale.US, "%,d", value.roundToLong())
    abs(value - value.roundToLong()) < 0.05f -> value.roundToLong().toString()
    else -> String.format(Locale.US, "%.1f", value)
}
