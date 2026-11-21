@file:OptIn(ExperimentalMaterial3Api::class)

package com.daniel.hardwarelens

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    val ssid: String, val bssid: String, val rssi: Int, val freq: Int,
    val channel: Int, val centerChannel: Int, val widthMhz: Int,
    val security: String, val band: WifiBand, val standard: String?,
) {
    val key: String get() = "$bssid|$freq|$ssid"
}

private fun ScanResult.toNet(): WifiNet {
    @Suppress("DEPRECATION") val name: String? = SSID
    val width = WifiMath.widthMhz(channelWidth)
    val ch = WifiMath.channel(frequency)
    val center = if (width > 20 && centerFreq0 > 0) WifiMath.channel(centerFreq0) else ch
    return WifiNet(
        ssid = if (name.isNullOrBlank()) "(hidden network)" else name,
        bssid = BSSID ?: "",
        rssi = level,
        freq = frequency,
        channel = ch,
        centerChannel = if (center > 0) center else ch,
        widthMhz = width,
        security = WifiMath.security(capabilities ?: ""),
        band = WifiMath.band(frequency),
        standard = if (Build.VERSION.SDK_INT >= 30) WifiMath.standardName(wifiStandard) else null,
    )
}

private class ConnectedWifi(val ssid: String, val bssid: String, val rssi: Int, val linkMbps: Int, val freq: Int)

@Suppress("DEPRECATION")
private fun readConnected(wm: WifiManager): ConnectedWifi? {
    val i = wm.connectionInfo ?: return null
    if (i.bssid == null || i.rssi <= -127 || i.networkId == -1 && i.linkSpeed <= 0) return null
    val ssid = i.ssid?.removeSurrounding("\"")?.takeUnless { it == "<unknown ssid>" || it.isBlank() } ?: "(name hidden)"
    return ConnectedWifi(ssid, i.bssid ?: "", i.rssi, i.linkSpeed, i.frequency)
}

@Composable
fun WifiScreen() {
    PermissionGate(
        Perms.location,
        "Android only shows nearby Wi-Fi networks to apps that have Location permission. Hardware Lens never stores or sends your location.",
        need = Perms.fineLocation
    ) { WifiContent() }
}

@SuppressLint("MissingPermission")
@Composable
private fun WifiContent() {
    val ctx = LocalContext.current
    val wm = remember { ctx.applicationContext.getSystemService(WifiManager::class.java) }
    var nets by remember { mutableStateOf(emptyList<WifiNet>()) }
    var status by remember { mutableStateOf("Scanning…") }
    var band by rememberSaveable { mutableStateOf<WifiBand?>(null) }
    var connected by remember { mutableStateOf<ConnectedWifi?>(null) }
    val signalHistory = remember { mutableStateListOf<Float>() }
    val wifiOn by produceState(initialValue = true) { while (true) { value = wm.isWifiEnabled; delay(2000) } }

    fun refresh() {
        nets = try {
            wm.scanResults.map { it.toNet() }.distinctBy { it.key }.sortedByDescending { it.rssi }
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

    val interval = Prefs.wifiScanSeconds.intValue
    LaunchedEffect(interval) {
        refresh()
        while (true) {
            @Suppress("DEPRECATION") val started = wm.startScan()
            if (!started) status = "Android allows ~4 scans per 2 min — showing latest results"
            delay(interval * 1000L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val c = readConnected(wm)
            connected = c
            if (c != null) signalHistory.push(c.rssi.toFloat())
            delay(2000)
        }
    }

    val counts = WifiBand.entries.associateWith { b -> nets.count { it.band == b } }
    val shown = if (band == null) nets else nets.filter { it.band == band }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { LocationOffHint() }
        if (!wifiOn) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Wi-Fi is off", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) { Text("Open Wi-Fi settings") }
                }
            }
        }
        item { ConnectedCard(connected, signalHistory) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = band == null, onClick = { band = null }, label = { Text("All (${nets.size})") })
                WifiBand.entries.forEach { b ->
                    FilterChip(selected = band == b, onClick = { band = b }, label = { Text("${b.label} (${counts[b] ?: 0})") })
                }
            }
        }
        item { Text("${shown.size} networks · $status", style = MaterialTheme.typography.bodyMedium) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val mapBand = band ?: WifiBand.B24
                    Text("${mapBand.label} channel map", style = MaterialTheme.typography.titleMedium)
                    HintText(
                        "Taller hill = stronger signal. Wider hill = wider channel. Overlapping hills share airtime" +
                            if (band == null) " · pick a band above to see 5 or 6 GHz." else "."
                    )
                    Spacer(Modifier.height(8.dp))
                    ChannelMap(mapBand, nets.filter { it.band == mapBand }, connected?.bssid)
                }
            }
        }
        items(shown, key = { it.key }) { n -> WifiRow(n, n.bssid.equals(connected?.bssid, ignoreCase = true)) }
    }
}

@Composable
private fun ConnectedCard(c: ConnectedWifi?, history: List<Float>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text("Your connection", style = MaterialTheme.typography.titleMedium)
            if (c == null) {
                Text("Not connected to Wi-Fi.")
                return@Column
            }
            Text(c.ssid, style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth()) {
                StatTile("Signal · ${WifiMath.quality(c.rssi)}", "${c.rssi} dBm", Modifier.weight(1f))
                StatTile("Link speed", if (c.linkMbps > 0) "${c.linkMbps} Mbps" else "—", Modifier.weight(1f))
                StatTile(WifiMath.band(c.freq).label, "ch ${WifiMath.channel(c.freq)}", Modifier.weight(1f))
            }
            Mac.normalize(c.bssid)?.let { m ->
                HintText("Access point MAC $m" + (OuiDb.describe(m)?.let { " · $it" } ?: ""))
            }
            HintText("Signal over the last few minutes — walk around to find dead spots.")
            LineChart(listOf(history.toList()), height = 80.dp, minY = -95f, maxY = -25f)
        }
    }
}

@Composable
private fun ChannelMap(band: WifiBand, nets: List<WifiNet>, connectedBssid: String?) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        fun x(ch: Float) = (ch - band.minCh) / (band.maxCh - band.minCh) * size.width
        val chartH = size.height - 18.dp.toPx()
        fun y(rssi: Int) = chartH * (1f - ((rssi + 100).coerceIn(0, 70) / 70f))

        drawLine(labelColor, Offset(0f, chartH), Offset(size.width, chartH), 1f)
        for (ch in band.labels) {
            drawText(
                textMeasurer = measurer,
                text = "$ch",
                topLeft = Offset(x(ch.toFloat()) - 6.dp.toPx(), chartH + 2.dp.toPx()),
                style = TextStyle(color = labelColor, fontSize = 10.sp)
            )
        }
        nets.forEach { n ->
            val color = colorFor(n.bssid)
            val peak = y(n.rssi)
            val half = n.widthMhz / 10f
            val center = n.centerChannel.toFloat()
            val path = Path().apply {
                moveTo(x(center - half), chartH)
                quadraticTo(x(center), 2 * peak - chartH, x(center + half), chartH)
            }
            val mine = n.bssid.equals(connectedBssid, ignoreCase = true)
            drawPath(path, color.copy(alpha = if (mine) 0.4f else 0.15f))
            drawPath(path, color, style = Stroke((if (mine) 3 else 2).dp.toPx()))
            val lx = (x(center) - 20.dp.toPx()).coerceIn(0f, size.width * 0.8f)
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
private fun WifiRow(n: WifiNet, connected: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(colorFor(n.bssid), CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.ssid, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                    if (connected) {
                        Spacer(Modifier.width(6.dp))
                        Text("CONNECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    "ch ${n.channel} · ${n.band.label} · ${n.widthMhz} MHz · ${n.security}" + (n.standard?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    n.bssid.uppercase() + (OuiDb.describe(n.bssid)?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { WifiMath.fraction(n.rssi) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${n.rssi} dBm", fontFamily = FontFamily.Monospace)
                HintText(WifiMath.quality(n.rssi))
            }
        }
    }
}
