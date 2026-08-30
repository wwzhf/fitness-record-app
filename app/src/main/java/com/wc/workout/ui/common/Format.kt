package com.wc.workout.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hm = DateTimeFormatter.ofPattern("HH:mm")

fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(hm)

/** 59 → "0:59"；3661 → "1:01:01" */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** 展示用：固定 1 位小数，如 "72.5" */
fun Double.kgLabel(): String = String.format(Locale.US, "%.1f", this)

/** 输入框预填用：整数不带小数，如 "60" */
fun Double.displayKg(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

/** 组记录紧凑展示（"上次"参考、历史页、日历详情）：0kg 表示自重 → "自重×12"，负重 → "60kg×12" */
fun formatSetSummary(weightKg: Double, reps: Int): String =
    if (weightKg == 0.0) "自重×$reps" else "${weightKg.displayKg()}kg×$reps"

/** 组记录行展示（训练页组列表）：0kg 表示自重 → "自重 × 12 次"，负重 → "60kg × 12 次" */
fun formatSetRow(weightKg: Double, reps: Int): String =
    if (weightKg == 0.0) "自重 × $reps 次" else "${weightKg.displayKg()}kg × $reps 次"

/** 当天 00:00 的 epoch millis（本地时区） */
fun startOfDayMillis(d: LocalDate): Long =
    d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 次日 00:00 的 epoch millis（本地时区），用于 BETWEEN 的上界 */
fun endOfDayMillisExclusive(d: LocalDate): Long =
    d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
