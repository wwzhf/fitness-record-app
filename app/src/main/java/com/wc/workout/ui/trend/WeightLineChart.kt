package com.wc.workout.ui.trend

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

@Composable
fun WeightLineChart(points: List<Pair<Long, Double>>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val pad = 24f
        val paint = android.graphics.Paint().apply {
            textSize = 28f
            color = android.graphics.Color.GRAY
            isAntiAlias = true
        }
        if (points.size == 1) {
            drawCircle(lineColor, radius = 8f, center = Offset(size.width / 2, size.height / 2))
            return@Canvas
        }
        val minD = points.first().first.toFloat()
        val maxD = points.last().first.toFloat()
        val minV = points.minOf { it.second }
        val maxV = points.maxOf { it.second }
        val yLo = (minV - 1).toFloat()
        val yHi = (maxV + 1).toFloat()

        fun px(day: Long): Float =
            pad + (size.width - 2 * pad) * ((day.toFloat() - minD) / ((maxD - minD).coerceAtLeast(1f)))

        fun py(v: Double): Float =
            size.height - pad - (size.height - 2 * pad) * ((v.toFloat() - yLo) / (yHi - yLo))

        val path = Path()
        points.forEachIndexed { i, (day, kg) ->
            val x = px(day); val y = py(kg)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path, color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        if (points.size <= 90) {
            points.forEach { (day, kg) ->
                drawCircle(lineColor, radius = 4.dp.toPx() / 2, center = Offset(px(day), py(kg)))
            }
        }
        drawContext.canvas.nativeCanvas.drawText(
            "%.1f".format(maxV), pad, py(maxV) + 28f, paint
        )
        drawContext.canvas.nativeCanvas.drawText(
            "%.1f".format(minV), pad, py(minV), paint
        )
    }
}
