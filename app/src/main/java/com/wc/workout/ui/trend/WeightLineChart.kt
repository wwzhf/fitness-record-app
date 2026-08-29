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
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WeightLineChart(points: List<Pair<Long, Double>>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val pad = 56.dp.toPx()
        val textHeight = 12.sp.toPx()
        val paint = android.graphics.Paint().apply {
            textSize = textHeight
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

        // 横向网格线：最小值、中间值、最大值
        val gridValues = listOf(minV, (minV + maxV) / 2, maxV)
        gridValues.forEach { v ->
            drawLine(
                color = gridColor,
                start = Offset(pad, py(v)),
                end = Offset(size.width - pad, py(v)),
                strokeWidth = 1.dp.toPx()
            )
        }

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

        // Y 轴 kg 刻度（画布左侧，与网格线同高）
        gridValues.forEach { v ->
            drawContext.canvas.nativeCanvas.drawText(
                "%.1f".format(v), 0f, py(v) + textHeight / 3, paint
            )
        }

        // X 轴日期刻度：4 个刻度线 + 底部标签
        val tickPaint = android.graphics.Paint().apply {
            textSize = 10.sp.toPx()
            color = android.graphics.Color.GRAY
            isAntiAlias = true
        }
        val minDay = points.first().first
        val maxDay = points.last().first
        val dateFmt = DateTimeFormatter.ofPattern(if (maxDay - minDay <= 400) "MM-dd" else "yyyy-MM")
        val zone = ZoneId.systemDefault()
        val tickLen = 6.dp.toPx()
        for (i in 0..3) {
            val day = (minDay + (maxDay - minDay) * i / 3.0).toLong()
            val x = px(day)
            drawLine(
                color = gridColor,
                start = Offset(x, size.height),
                end = Offset(x, size.height - tickLen),
                strokeWidth = 1.dp.toPx()
            )
            val label = Instant.ofEpochMilli(day * 86_400_000).atZone(zone).toLocalDate().format(dateFmt)
            val w = tickPaint.measureText(label)
            val tx = when (i) {
                0 -> x
                3 -> x - w
                else -> x - w / 2
            }
            drawContext.canvas.nativeCanvas.drawText(label, tx, size.height - 4f, tickPaint)
        }
    }
}
