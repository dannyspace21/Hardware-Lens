@file:OptIn(ExperimentalMaterial3Api::class)

package com.daniel.hardwarelens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
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
    val address: String,
    val name: String?,
    val rssi: Int,
    val lastSeen: Long,
    val maker: String?,
    val kind: BleKind?,
    val txPower: Int?,
    val connectable: Boolean,
    val services: List<String>,
    val rawData: List<String>,
    val rssiHistory: List<Int>,
    val packets: Int,
) {
    val title: String get() = name ?: kind?.label ?: maker?.let { "$it device" } ?: "Unknown device"
    /** Rough distance from signal strength; uses the advertised TX power when the device gives one. */
    val meters: Double get() = 10.0.pow(((txPower?.let { it - 41 } ?: -59) - rssi) / 20.0)
}

private fun ScanResult.toDevice(prev: BleDevice?): BleDevice {
    val rec = scanRecord
    val mfgSparse = rec?.manufacturerSpecificData
    val mfg = buildMap<Int, ByteArray> {
        if (mfgSparse != null) for (i in 0 until mfgSparse.size()) put(mfgSparse.keyAt(i), mfgSparse.valueAt(i))
    }
    val uuids = rec?.serviceUuids.orEmpty().map { it.uuid }
    val serviceData = rec?.serviceData.orEmpty()
    val advert = BleAdvert(
        name = rec?.deviceName,
        manufacturerData = mfg,
        serviceUuids16 = uuids.mapNotNull { BleDecode.short16(it) },
        serviceData16 = serviceData.entries.mapNotNull { (k, v) -> BleDecode.short16(k.uuid)?.let { it to v } }.toMap(),
    )
    val raw = buildList {
        mfg.forEach { (id, bytes) -> add("${BleDecode.companyName(id)} data: ${bytes.hex(" ")}") }
        serviceData.forEach { (k, v) -> add("Service ${BleDecode.short16(k.uuid)?.let { "0x%04X".format(it) } ?: k.uuid} data: ${v.hex(" ")}") }
    }
    val tx = if (txPower != ScanResult.TX_POWER_NOT_PRESENT) txPower else rec?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
    return BleDevice(
        address = device.address,
        name = rec?.deviceName ?: prev?.name,
        rssi = rssi,
        lastSeen = System.currentTimeMillis(),
        maker = mfg.keys.firstOrNull()?.let { BleDecode.companyName(it) },
        kind = BleDecode.classify(advert) ?: prev?.kind,
        txPower = tx,
        connectable = isConnectable,
        services = uuids.map { u -> BleDecode.short16(u)?.let { "0x%04X".format(it) } ?: u.toString() },
        rawData = raw,
        rssiHistory = ((prev?.rssiHistory ?: emptyList()) + rssi).takeLast(60),
        packets = (prev?.packets ?: 0) + 1,
    )
}

@Composable
fun BluetoothScreen() {
    PermissionGate(
        Perms.bluetooth,
        "Scanning for nearby Bluetooth devices needs the \"Nearby devices\" permission (and Location on older Android).",
        need = Perms.bluetoothNeeded
    ) { BluetoothContent() }
}

@SuppressLint("MissingPermission")
@Composable
private fun BluetoothContent() {
    val ctx = LocalContext.current
    val adapter = remember { ctx.getSystemService(BluetoothManager::class.java)?.adapter }
    val devices = remember { mutableStateMapOf<String, BleDevice>() }
    var scanning by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var namedOnly by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }

    val callback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val addr = result.device.address
                devices[addr] = result.toDevice(devices[addr])
            }
            override fun onScanFailed(errorCode: Int) {
                error = "Scan failed (code $errorCode) — wait a few seconds and tap Start"
                scanning = false
            }
        }
    }

    DisposableEffect(scanning) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanning) {
            when {
                adapter == null -> { error = "This phone has no Bluetooth"; scanning = false }
                !adapter.isEnabled -> { error = "Bluetooth is off"; scanning = false }
                scanner == null -> { error = "Bluetooth scanner unavailable"; scanning = false }
                else -> try {
                    error = null
                    val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
                    scanner.startScan(null, settings, callback)
                } catch (e: SecurityException) {
                    error = "Bluetooth permission needed (check app permissions)"
                    scanning = false
                }
            }
        }
        onDispose { runCatching { scanner?.stopScan(callback) } }
    }

    // Forget devices not heard from in 30 seconds.
    LaunchedEffect(scanning) {
        while (scanning) {
            delay(2000)
            val cutoff = System.currentTimeMillis() - 30_000
            devices.keys.filter { (devices[it]?.lastSeen ?: 0) < cutoff }.forEach { devices.remove(it) }
        }
    }

    val all = devices.values.sortedByDescending { it.rssi }
    val list = if (namedOnly) all.filter { it.name != null || it.kind != null } else all
    val categories = all.mapNotNull { it.kind?.category }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { LocationOffHint() }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bluetooth radar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Button(onClick = { scanning = !scanning }) { Text(if (scanning) "Stop" else "Start scan") }
                    }
                    HintText("Center = your phone. Closer dots = stronger signal (roughly closer). Tap a device below for details.")
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        if (adapter?.isEnabled == false) {
                            TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Open Bluetooth settings") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Radar(list, selected)
                    Text("${all.size} devices nearby", style = MaterialTheme.typography.bodySmall)
                    if (categories.isNotEmpty()) {
                        HintText(categories.joinToString(" · ") { "${it.value} ${it.key.lowercase()}" })
                    }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !namedOnly, onClick = { namedOnly = false }, label = { Text("Everything (${all.size})") })
                FilterChip(selected = namedOnly, onClick = { namedOnly = true }, label = { Text("Identified only") })
            }
        }
        items(list, key = { it.address }) { d -> BleRow(d) { selected = d.address } }
    }

    val detail = selected?.let { devices[it] }
    if (detail != null) BleDetailDialog(detail) { selected = null }
}

@Composable
private fun Radar(devices: List<BleDevice>, highlight: String?) {
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
            drawCircle(colorFor(d.address), (if (d.address == highlight) 10 else 6).dp.toPx(), p)
        }
    }
}

@Composable
private fun BleRow(d: BleDevice, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(colorFor(d.address), CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, style = MaterialTheme.typography.titleSmall)
                val sub = listOfNotNull(d.kind?.label?.takeIf { it != d.title }, d.maker?.takeIf { d.kind == null }, d.address)
                Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${d.rssi} dBm", fontFamily = FontFamily.Monospace)
                HintText("~${Fmt.distance(d.meters, Prefs.units.value)}")
            }
        }
    }
}

@Composable
private fun BleDetailDialog(d: BleDevice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(d.title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item {
                    Text("Signal (last ${d.rssiHistory.size} readings)", style = MaterialTheme.typography.labelMedium)
                    LineChart(listOf(d.rssiHistory.map { it.toFloat() }), height = 80.dp, minY = -100f, maxY = -30f, maxPoints = 60)
                }
                item { InfoRow("What it looks like", d.kind?.let { "${it.label} (${it.category})" } ?: "Not recognised") }
                item { InfoRow("Address", d.address) }
                item { InfoRow("Signal", "${d.rssi} dBm · ~${Fmt.distance(d.meters, Prefs.units.value)} away") }
                d.txPower?.let { item { InfoRow("Advertised transmit power", "$it dBm") } }
                item { InfoRow("Can connect", if (d.connectable) "Yes" else "No (broadcast only)") }
                item { InfoRow("Packets heard", "${d.packets}") }
                if (d.services.isNotEmpty()) item { InfoRow("Services", d.services.joinToString("\n")) }
                if (d.rawData.isNotEmpty()) item { InfoRow("Raw advertisement data", d.rawData.joinToString("\n")) }
            }
        }
    )
}
