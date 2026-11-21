package com.daniel.hardwarelens

import java.util.Locale

enum class ThemeMode(val label: String) { System("Follow system"), Light("Light"), Dark("Dark") }

enum class Units(val label: String) { Metric("Metric (m, km/h, °C)"), Imperial("Imperial (ft, mph, °F)") }

object Fmt {
    private fun f(pattern: String, v: Double) = String.format(Locale.getDefault(), pattern, v)
    fun distance(m: Double, u: Units) = if (u == Units.Imperial) f("%.1f ft", m * 3.28084) else f("%.1f m", m)
    fun altitude(m: Double, u: Units) = if (u == Units.Imperial) f("%.0f ft", m * 3.28084) else f("%.0f m", m)
    fun speed(mps: Double, u: Units) = if (u == Units.Imperial) f("%.1f mph", mps * 2.23694) else f("%.1f km/h", mps * 3.6)
    fun temp(c: Double, u: Units) = if (u == Units.Imperial) f("%.1f °F", c * 9 / 5 + 32) else f("%.1f °C", c)
}
