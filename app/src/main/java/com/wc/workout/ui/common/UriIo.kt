package com.wc.workout.ui.common

import android.content.Context
import android.net.Uri

fun writeUriText(context: Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
    } ?: throw IllegalStateException("无法写入所选位置")
}

fun readUriText(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: throw IllegalStateException("无法读取所选文件")
