package com.wc.workout.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wc.workout.data.local.WeightRecord
import com.wc.workout.data.local.WorkoutSession
import com.wc.workout.ui.common.kgLabel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun MonthGrid(
    month: YearMonth,
    weights: Map<Long, WeightRecord>,
    sessions: List<WorkoutSession>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1 // 周一起始：周一偏移 0，周日偏移 6
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    val zone = ZoneId.systemDefault()
    val sessionsByDay = sessions.groupBy {
        Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate()
    }

    Column(modifier) {
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val index = r * 7 + c
                    val date = first.minusDays(offset.toLong()).plusDays(index.toLong())
                    if (date.month == month.month) {
                        DayCell(
                            date = date,
                            weight = weights[date.toEpochDay()],
                            daySessions = sessionsByDay[date].orEmpty(),
                            modifier = Modifier.weight(1f),
                            onDayClick = onDayClick
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    weight: WeightRecord?,
    daySessions: List<WorkoutSession>,
    modifier: Modifier,
    onDayClick: (LocalDate) -> Unit
) {
    val isToday = date == LocalDate.now()
    val hasWorkout = daySessions.isNotEmpty()
    val cellShape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .clip(cellShape)
            .then(
                if (hasWorkout) {
                    Modifier.background(
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                        cellShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onDayClick(date) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isToday) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            weight?.weightKg?.kgLabel() ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        val titles = daySessions.sortedBy { it.startTime }.map { it.title }
        titles.take(2).forEach { t ->
            Text(
                t,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (titles.size > 2) {
            Text(
                "+${titles.size - 2}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
