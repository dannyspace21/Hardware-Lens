package com.daniel.hardwarelens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

data class WifiNet(
    val ssid: String, val bssid: String, val rssi: Int,
    val freq: Int, val channel: Int, val security: String
)

private fun ScanResult.toNet(): WifiNet {
    @Suppress("DEPRECATION") val name: String? = SSID
    return WifiNet(
        ssid = if (name.isNullOrBlank()) "(hidden network)" else name,
        bssid = BSSID ?: "",
        rssi = level,
        freq = frequency,
        channel = freqToChannel(frequency),
        security = securityOf(capabilities ?: "")
    )
}

private fun freqToChannel(f: Int) = when {
    f == 2484 -> 14
    f in 2412..2472 -> (f - 2407) / 5
    f in 5000..5900 -> (f - 5000) / 5
    f in 5925..7125 -> (f - 5950) / 5
    else -> 0
}

private fun band(f: Int) = when {
    f < 3000 -> "2.4 GHz"
    f < 5925 -> "5 GHz"
    else -> "6 GHz"
}

private fun securityOf(c: String) = when {
    "OWE" in c -> "Enhanced Open"
    "SAE" in c || "WPA3" in c -> "WPA3"
    "RSN" in c || "WPA2" in c -> "WPA2"
    "WPA" in c -> "WPA"
    "WEP" in c -> "WEP"
    else -> "Open"
}

@Composable
fun WifiScreen() {
    val ctx = LocalContext.current
    val wm = remember { ctx.applicationContext.getSystemService(WifiManager::class.java) }
    var nets by remember { mutableStateOf(emptyList<WifiNet>()) }
    var status by remember { mutableStateOf("Scanning…") }

    fun refresh() {
        nets = try {
            wm.scanResults.map { it.toNet() }.sortedByDescending { it.rssi }
        } catch (e: SecurityException) {
            status = "Needs Location permission (and Location turned on)"
            emptyList()
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                refresh()
                status = "Updated ${timeNow()}"
            }
        }
        ContextCompat.registerReceiver(
            ctx, receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            @Suppress("DEPRECATION") val started = wm.startScan()
            if (!started) status = "Android allows ~4 scans per 2 min — showing latest results"
            delay(30_000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("${nets.size} networks · $status", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("2.4 GHz channel map", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Taller hill = stronger signal. Overlapping hills = networks fighting for airtime.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    ChannelMap(nets.filter { it.freq in 2400..2500 })
                }
            }
        }
        items(nets) { n -> WifiRow(n) }
    }
}

@Composable
private fun ChannelMap(nets: List<WifiNet>) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val minCh = -1f
        val maxCh = 15f
        fun x(ch: Float) = (ch - minCh) / (maxCh - minCh) * size.width
        val chartH = size.height - 18.dp.toPx()
        fun y(rssi: Int) = chartH * (1f - ((rssi + 100).coerceIn(0, 70) / 70f))

        drawLine(labelColor, Offset(0f, chartH), Offset(size.width, chartH), 1f)
        for (ch in 1..14) {
            drawText(
                textMeasurer = measurer,
                text = "$ch",
                topLeft = Offset(x(ch.toFloat()) - 4.dp.toPx(), chartH + 2.dp.toPx()),
                style = TextStyle(color = labelColor, fontSize = 10.sp)
            )
        }
        nets.forEach { n ->
            val color = colorFor(n.bssid)
            val peak = y(n.rssi)
            val path = Path().apply {
                moveTo(x(n.channel - 2f), chartH)
                quadraticBezierTo(x(n.channel.toFloat()), 2 * peak - chartH, x(n.channel + 2f), chartH)
            }
            drawPath(path, color.copy(alpha = 0.2f))
            drawPath(path, color, style = Stroke(2.dp.toPx()))
            val lx = (x(n.channel.toFloat()) - 20.dp.toPx()).coerceIn(0f, size.width * 0.8f)
            val ly = (peak - 14.dp.toPx()).coerceAtLeast(0f)
            drawText(
                textMeasurer = measurer,
                text = n.ssid.take(14),
                topLeft = Offset(lx, ly),
                style = TextStyle(color = color, fontSize = 9.sp)
            )
        }
    }
}

@Composable
private fun WifiRow(n: WifiNet) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(colorFor(n.bssid), CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n.ssid, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${n.bssid} · ch ${n.channel} (${band(n.freq)}) · ${n.security}",
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = { ((n.rssi + 100) / 70f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("${n.rssi} dBm", fontFamily = FontFamily.Monospace)
        }
    }
}
