package com.daniel.hardwarelens

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class BleDevice(
    val address: String, val name: String?, val rssi: Int,
    val lastSeen: Long, val maker: String?
)

private val companies = mapOf(
    0x004C to "Apple", 0x0006 to "Microsoft", 0x0075 to "Samsung", 0x00E0 to "Google",
    0x012D to "Sony", 0x0087 to "Garmin", 0x0157 to "Amazfit", 0x038F to "Xiaomi",
    0x0059 to "Nordic Semi", 0x02E5 to "Espressif", 0x0171 to "Amazon", 0x009E to "Bose",
)

private fun companyName(id: Int) = companies[id] ?: "Maker ID 0x%04X".format(id)

@Composable
fun BluetoothScreen() {
    val ctx = LocalContext.current
    val adapter = remember { ctx.getSystemService(BluetoothManager::class.java)?.adapter }
    val devices = remember { mutableStateMapOf<String, BleDevice>() }
    var scanning by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val callback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val rec = result.scanRecord
                val mfg = rec?.manufacturerSpecificData
                val maker = if (mfg != null && mfg.size() > 0) companyName(mfg.keyAt(0)) else null
                devices[result.device.address] = BleDevice(
                    result.device.address, rec?.deviceName, result.rssi,
                    System.currentTimeMillis(), maker
                )
            }
            override fun onScanFailed(errorCode: Int) {
                error = "Scan failed (code $errorCode) — try again in a few seconds"
                scanning = false
            }
        }
    }

    DisposableEffect(scanning) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanning) {
            when {
                adapter == null -> { error = "This phone has no Bluetooth"; scanning = false }
                !adapter.isEnabled -> { error = "Turn Bluetooth on, then tap Start"; scanning = false }
                scanner == null -> { error = "Bluetooth scanner unavailable"; scanning = false }
                else -> try {
                    error = null
                    val settings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                    scanner.startScan(null, settings, callback)
                } catch (e: SecurityException) {
                    error = "Bluetooth permission needed (check app permissions)"
                    scanning = false
                }
            }
        }
        onDispose { try { scanner?.stopScan(callback) } catch (e: Exception) { } }
    }

    // Forget devices not heard from in 30 seconds.
    LaunchedEffect(scanning) {
        while (scanning) {
            delay(2000)
            val cutoff = System.currentTimeMillis() - 30_000
            devices.keys.filter { (devices[it]?.lastSeen ?: 0) < cutoff }.forEach { devices.remove(it) }
        }
    }

    val list = devices.values.sortedByDescending { it.rssi }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bluetooth radar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Button(onClick = { scanning = !scanning }) { Text(if (scanning) "Stop" else "Start scan") }
                    }
                    Text(
                        "Center = your phone. Closer dots = stronger signal (roughly closer).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(8.dp))
                    Radar(list)
                    Text("${list.size} devices nearby", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(list, key = { it.address }) { d -> BleRow(d) }
    }
}

@Composable
private fun Radar(devices: List<BleDevice>) {
    val ring = MaterialTheme.colorScheme.outline
    val me = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f * 0.95f
        for (i in 1..4) drawCircle(ring.copy(alpha = 0.4f), r * i / 4f, c, style = Stroke(1.dp.toPx()))
        drawCircle(me, 7.dp.toPx(), c)
        devices.forEach { d ->
            val dist = ((-d.rssi - 30).coerceIn(0, 70) / 70f) * r
            val angle = ((d.address.hashCode() and 0x7fffffff) % 360) * PI / 180.0
            val p = Offset(c.x + dist * cos(angle).toFloat(), c.y + dist * sin(angle).toFloat())
            drawCircle(colorFor(d.address), 6.dp.toPx(), p)
        }
    }
}

@Composable
private fun BleRow(d: BleDevice) {
    val meters = 10.0.pow((-59 - d.rssi) / 20.0)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(colorFor(d.address), CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name ?: d.maker?.let { "$it device" } ?: "Unknown device", style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(d.maker, d.address).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${d.rssi} dBm", fontFamily = FontFamily.Monospace)
                Text("~%.1f m".format(meters), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
