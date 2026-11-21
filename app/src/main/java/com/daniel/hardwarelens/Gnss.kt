package com.daniel.hardwarelens

/** Names for android.location.GnssStatus constellation types. */
object Gnss {
    fun constellation(type: Int): String = when (type) {
        1 -> "GPS (USA)"
        2 -> "SBAS"
        3 -> "GLONASS (Russia)"
        4 -> "QZSS (Japan)"
        5 -> "BeiDou (China)"
        6 -> "Galileo (EU)"
        7 -> "NavIC (India)"
        else -> "Unknown"
    }

    fun shortName(type: Int): String = when (type) {
        1 -> "GPS"; 2 -> "SBAS"; 3 -> "GLONASS"; 4 -> "QZSS"; 5 -> "BeiDou"; 6 -> "Galileo"; 7 -> "NavIC"
        else -> "?"
    }

    /** GPS L1 vs L5 etc, from carrier frequency in MHz. */
    fun bandName(mhz: Float?): String? = when {
        mhz == null -> null
        mhz > 1559 && mhz < 1610 -> "L1/E1/B1"
        mhz > 1160 && mhz < 1190 -> "L5/E5a/B2a"
        mhz > 1190 && mhz < 1215 -> "E5b/B2b"
        mhz > 1215 && mhz < 1240 -> "L2"
        mhz > 1260 && mhz < 1300 -> "E6/B3"
        else -> null
    }
}
