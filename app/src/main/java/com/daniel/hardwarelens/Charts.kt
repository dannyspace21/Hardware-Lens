package com.daniel.hardwarelens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

const val MAX_POINTS = 120

/** Simple scrolling line chart. Each inner list is one line (X, Y, Z...). */
@Composable
fun LineChart(series: List<List<Float>>, modifier: Modifier = Modifier) {
    val grid = Color.Gray.copy(alpha = 0.3f)
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
        }
        val all = series.flatten()
        if (all.isEmpty()) return@Canvas
        var min = all.min()
        var max = all.max()
        if (max - min < 0.001f) { max += 1f; min -= 1f }
        val range = max - min
        val step = size.width / (MAX_POINTS - 1)

        series.forEachIndexed { idx, s ->
            if (s.size < 2) return@forEachIndexed
            val path = Path()
            val offset = MAX_POINTS - s.size
            s.forEachIndexed { j, v ->
                val x = (offset + j) * step
                val y = size.height * (1f - (v - min) / range)
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Palette[idx % Palette.size], style = Stroke(width = 2.dp.toPx()))
        }
    }
}
