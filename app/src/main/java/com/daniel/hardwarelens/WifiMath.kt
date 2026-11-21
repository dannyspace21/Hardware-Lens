package com.daniel.hardwarelens

/** Channel-map layout for each Wi-Fi band: the x-axis range and which channel numbers to label. */
enum class WifiBand(val label: String, val minCh: Float, val maxCh: Float, val labels: List<Int>) {
    B24("2.4 GHz", -1f, 16f, (1..14).toList()),
    B5("5 GHz", 28f, 181f, listOf(36, 52, 64, 100, 116, 132, 149, 165, 177)),
    B6("6 GHz", -6f, 239f, listOf(1, 37, 69, 101, 133, 165, 197, 229)),
}

object WifiMath {
    fun channel(freq: Int): Int = when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2407) / 5
        freq == 5935 -> 2
        freq in 5150..5895 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5950) / 5
        else -> 0
    }

    fun band(freq: Int): WifiBand = when {
        freq < 3000 -> WifiBand.B24
        freq < 5925 -> WifiBand.B5
        else -> WifiBand.B6
    }

    /** ScanResult.channelWidth code -> MHz. */
    fun widthMhz(code: Int): Int = when (code) {
        1 -> 40
        2 -> 80
        3, 4 -> 160
        5 -> 320
        else -> 20
    }

    fun security(caps: String): String = when {
        "OWE" in caps -> "Enhanced Open"
        "SAE" in caps || "WPA3" in caps -> "WPA3"
        "RSN" in caps || "WPA2" in caps -> "WPA2"
        "WPA" in caps -> "WPA"
        "WEP" in caps -> "WEP"
        else -> "Open"
    }

    /** ScanResult.getWifiStandard() -> marketing name. */
    fun standardName(code: Int): String? = when (code) {
        1 -> "Legacy (a/b/g)"
        4 -> "Wi-Fi 4 (n)"
        5 -> "Wi-Fi 5 (ac)"
        6 -> "Wi-Fi 6 (ax)"
        7 -> "WiGig (ad)"
        8 -> "Wi-Fi 7 (be)"
        else -> null
    }

    fun quality(rssi: Int): String = when {
        rssi >= -50 -> "Excellent"
        rssi >= -60 -> "Good"
        rssi >= -70 -> "Fair"
        else -> "Weak"
    }

    fun fraction(rssi: Int): Float = ((rssi + 100) / 70f).coerceIn(0f, 1f)
}
