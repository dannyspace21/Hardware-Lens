package com.daniel.hardwarelens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

private fun fmt(pattern: String, vararg args: Any) = String.format(Locale.getDefault(), pattern, *args)

@Composable
fun BatteryScreen() {
    val ctx = LocalContext.current
    val bm = remember { ctx.getSystemService(BatteryManager::class.java) }
    var info by remember { mutableStateOf<Intent?>(null) }
    var currentMa by remember { mutableStateOf<Float?>(null) }
    val currentHist = remember { mutableStateListOf<Float>() }
    val tempHist = remember { mutableStateListOf<Float>() }
    val units = Prefs.units.value

    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { info = i }
        }
        val sticky = ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (sticky != null) info = sticky
        onDispose { ctx.unregisterReceiver(r) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val raw = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
            if (raw != Int.MIN_VALUE && raw != 0) {
                // Most phones report microamps; a few report milliamps.
                val ma = if (abs(raw) >= 10_000) raw / 1000f else raw.toFloat()
                currentMa = ma
                currentHist.push(ma)
            }
            info?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { tempHist.push(it / 10f) }
            delay(2000)
        }
    }

    val i = info
    val level = i?.let {
        val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (l >= 0 && s > 0) l * 100 / s else null
    }
    val statusCode = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val status = when (statusCode) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "On battery"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Plugged in, not charging"
        else -> "Unknown"
    }
    val plugged = when (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Wall charger"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless charger"
        8 -> "Dock"
        else -> "Not plugged in"
    }
    val health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        else -> "Unknown"
    }
    val tempC = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10.0 }
    val voltage = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.takeIf { it > 0 }
    val tech = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
    val cycles = i?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it >= 0 }
    val chargeCounterUah = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 && it != Int.MIN_VALUE }
    val estCapacity = if (chargeCounterUah != null && level != null && level > 5) chargeCounterUah / 1000 * 100 / level else null
    val remainingMs = if (Build.VERSION.SDK_INT >= 28) bm?.computeChargeTimeRemaining()?.takeIf { it > 0 } else null
    val watts = if (currentMa != null && voltage != null) abs(currentMa!! * voltage / 1_000_000f) else null

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BatteryGauge(level ?: 0, statusCode == BatteryManager.BATTERY_STATUS_CHARGING)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(level?.let { "$it%" } ?: "—", style = MaterialTheme.typography.displaySmall)
                        Text(status, style = MaterialTheme.typography.titleMedium)
                        HintText(plugged)
                        remainingMs?.let { HintText(fmt("Full in about %d min", it / 60000)) }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Power flow", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            "Current",
                            currentMa?.let { fmt("%+.0f mA", it) } ?: "—",
                            Modifier.weight(1f)
                        )
                        StatTile("Power", watts?.let { fmt("%.2f W", it) } ?: "—", Modifier.weight(1f))
                        StatTile("Voltage", voltage?.let { fmt("%.2f V", it / 1000f) } ?: "—", Modifier.weight(1f))
                    }
                    HintText("+ means power going into the battery, − means the phone is using it. Phone makers differ, so treat this as a rough guide.")
                    LineChart(listOf(currentHist.toList()), height = 90.dp)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Temperature", style = MaterialTheme.typography.titleMedium)
                    Text(tempC?.let { Fmt.temp(it, units) } ?: "—", style = MaterialTheme.typography.titleLarge)
                    HintText("Batteries are happiest below about 35 °C (95 °F). Hot + charging is what wears them out fastest.")
                    LineChart(listOf(tempHist.toList()), colors = listOf(Color(0xFFFF7043)), height = 70.dp)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Battery details", style = MaterialTheme.typography.titleMedium)
                    InfoRow("Health", health)
                    cycles?.let { InfoRow("Charge cycles", "$it") }
                    estCapacity?.let { InfoRow("Estimated full capacity", "≈ $it mAh (from the charge counter)") }
                    chargeCounterUah?.let { InfoRow("Charge left", "${it / 1000} mAh") }
                    tech?.let { InfoRow("Chemistry", it) }
                }
            }
        }
    }
}

@Composable
private fun BatteryGauge(level: Int, charging: Boolean) {
    val outline = MaterialTheme.colorScheme.onPrimaryContainer
    val fill = when {
        charging -> Color(0xFF43A047)
        level <= 15 -> Color(0xFFE53935)
        level <= 30 -> Color(0xFFFFB300)
        else -> MaterialTheme.colorScheme.primary
    }
    Canvas(Modifier.size(width = 48.dp, height = 88.dp)) {
        val capH = 6.dp.toPx()
        val bodyTop = capH
        val bodyH = size.height - capH
        drawRoundRect(outline, Offset(size.width * 0.3f, 0f), Size(size.width * 0.4f, capH + 2), CornerRadius(3f))
        drawRoundRect(outline, Offset(0f, bodyTop), Size(size.width, bodyH), CornerRadius(12f), style = Stroke(3.dp.toPx()))
        val inset = 6.dp.toPx()
        val h = (bodyH - inset * 2) * (level.coerceIn(0, 100) / 100f)
        drawRoundRect(fill, Offset(inset, bodyTop + bodyH - inset - h), Size(size.width - inset * 2, h), CornerRadius(6f))
    }
}
