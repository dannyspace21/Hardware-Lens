package com.daniel.hardwarelens

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun ByteArray.hex(sep: String = ""): String =
    joinToString(sep) { "%02X".format(it.toInt() and 0xFF) }

fun String.hexToBytes(): ByteArray {
    val clean = filter { it.isLetterOrDigit() }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

fun timeNow(): String = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())

fun dateTime(ms: Long): String = SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault()).format(Date(ms))

/** 0° -> "N", 45° -> "NE", ... */
fun cardinal(deg: Float): String {
    val names = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[(((deg + 22.5f) % 360f + 360f) % 360f / 45f).toInt().coerceIn(0, 7)]
}
