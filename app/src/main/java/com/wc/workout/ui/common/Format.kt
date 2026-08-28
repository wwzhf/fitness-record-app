package com.wc.workout.ui.common

import java.time.Instant
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
