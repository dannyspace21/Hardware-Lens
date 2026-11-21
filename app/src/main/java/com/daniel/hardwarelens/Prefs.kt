package com.daniel.hardwarelens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** App settings, stored on the phone and exposed as Compose state so the UI updates instantly. */
object Prefs {
    private var sp: SharedPreferences? = null

    val theme = mutableStateOf(ThemeMode.System)
    val dynamicColor = mutableStateOf(true)
    val units = mutableStateOf(Units.Metric)
    val wifiScanSeconds = mutableIntStateOf(30)
    val checkUpdates = mutableStateOf(true)
    val keepScreenOn = mutableStateOf(false)

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        sp = p
        theme.value = ThemeMode.entries.firstOrNull { it.name == p.getString("theme", null) } ?: ThemeMode.System
        units.value = Units.entries.firstOrNull { it.name == p.getString("units", null) } ?: Units.Metric
        dynamicColor.value = p.getBoolean("dynamicColor", true)
        wifiScanSeconds.intValue = p.getInt("wifiScanSeconds", 30)
        checkUpdates.value = p.getBoolean("checkUpdates", true)
        keepScreenOn.value = p.getBoolean("keepScreenOn", false)
    }

    fun setTheme(v: ThemeMode) { theme.value = v; sp?.edit()?.putString("theme", v.name)?.apply() }
    fun setUnits(v: Units) { units.value = v; sp?.edit()?.putString("units", v.name)?.apply() }
    fun setDynamicColor(v: Boolean) { dynamicColor.value = v; sp?.edit()?.putBoolean("dynamicColor", v)?.apply() }
    fun setWifiScanSeconds(v: Int) { wifiScanSeconds.intValue = v; sp?.edit()?.putInt("wifiScanSeconds", v)?.apply() }
    fun setCheckUpdates(v: Boolean) { checkUpdates.value = v; sp?.edit()?.putBoolean("checkUpdates", v)?.apply() }
    fun setKeepScreenOn(v: Boolean) { keepScreenOn.value = v; sp?.edit()?.putBoolean("keepScreenOn", v)?.apply() }
}
