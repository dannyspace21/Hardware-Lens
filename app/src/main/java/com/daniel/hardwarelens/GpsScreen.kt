package com.daniel.hardwarelens

import android.annotation.SuppressLint
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class Sat(
    val svid: Int, val constellation: Int, val cn0: Float, val elevation: Float,
    val azimuth: Float, val used: Boolean, val freqMhz: Float?,
)

private val constellationColors = mapOf(
    1 to Color(0xFF42A5F5), 2 to Color(0xFF8D6E63), 3 to Color(0xFFEF5350), 4 to Color(0xFFAB47BC),
    5 to Color(0xFFFFCA28), 6 to Color(0xFF66BB6A), 7 to Color(0xFFFF7043),
)

private fun satColor(type: Int) = constellationColors[type] ?: Color.Gray

@Composable
fun GpsScreen() {
    PermissionGate(
        Perms.location,
        "Seeing satellites needs precise Location permission. Your position is only shown on this screen — never stored or sent.",
        need = Perms.fineLocation
    ) { GpsContent() }
}

@SuppressLint("MissingPermission")
@Composable
private fun GpsContent() {
    val ctx = LocalContext.current
    val lm = remember { ctx.getSystemService(LocationManager::class.java) }
    var sats by remember { mutableStateOf(emptyList<Sat>()) }
    var fix by remember { mutableStateOf<Location?>(null) }
    var ttff by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val units = Prefs.units.value

    DisposableEffect(Unit) {
        val statusCb = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                sats = (0 until status.satelliteCount).map { i ->
                    Sat(
                        svid = status.getSvid(i),
                        constellation = status.getConstellationType(i),
                        cn0 = status.getCn0DbHz(i),
                        elevation = status.getElevationDegrees(i),
                        azimuth = status.getAzimuthDegrees(i),
                        used = status.usedInFix(i),
                        freqMhz = if (status.hasCarrierFrequencyHz(i)) status.getCarrierFrequencyHz(i) / 1e6f else null,
                    )
                }
            }
            override fun onFirstFix(ttffMillis: Int) { ttff = ttffMillis }
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { fix = location }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        try {
            if (lm == null || !lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                error = "This phone has no GPS."
            } else {
                lm.registerGnssStatusCallback(statusCb, Handler(Looper.getMainLooper()))
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            error = "Location permission is missing."
        }
        onDispose {
            runCatching { lm?.unregisterGnssStatusCallback(statusCb) }
            runCatching { lm?.removeUpdates(listener) }
        }
    }

    val used = sats.count { it.used }
    val present = sats.map { it.constellation }.distinct().sorted()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { LocationOffHint() }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile("Satellites used", "$used", Modifier.weight(1f))
                        StatTile("Satellites seen", "${sats.size}", Modifier.weight(1f))
                        StatTile("Accuracy", fix?.let { Fmt.distance(it.accuracy.toDouble(), units) } ?: "—", Modifier.weight(1f))
                    }
                    val f = fix
                    if (f == null) {
                        HintText("Looking for a fix… Go outside or near a window — GPS barely works indoors. The first fix can take 30 seconds or more.")
                    } else {
                        InfoRow("Position", String.format(Locale.US, "%.5f, %.5f", f.latitude, f.longitude))
                        Row(Modifier.fillMaxWidth()) {
                            StatTile("Altitude", if (f.hasAltitude()) Fmt.altitude(f.altitude, units) else "—", Modifier.weight(1f))
                            StatTile("Speed", if (f.hasSpeed()) Fmt.speed(f.speed.toDouble(), units) else "—", Modifier.weight(1f))
                            StatTile("First fix", ttff?.let { String.format(Locale.getDefault(), "%.1f s", it / 1000f) } ?: "—", Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sky map", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                    HintText("Center = straight overhead, edge = horizon, top = north. Filled dots are being used for your position.")
                    Spacer(Modifier.height(8.dp))
                    SkyPlot(sats)
                    Spacer(Modifier.height(8.dp))
                    FlowLegend(present)
                }
            }
        }
        if (sats.isNotEmpty()) item { SectionTitle("Signal strength by satellite") }
        items(sats.sortedByDescending { it.cn0 }) { s -> SatRow(s) }
    }
}

@Composable
private fun FlowLegend(types: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        types.chunked(3).forEach { row -> Legend(row.map { Gnss.shortName(it) to satColor(it) }) }
    }
}

@Composable
private fun SkyPlot(sats: List<Sat>) {
    val ring = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth(0.9f).aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f * 0.9f
        listOf(1f, 2f / 3f, 1f / 3f).forEach { drawCircle(ring.copy(alpha = 0.5f), r * it, c, style = Stroke(1.dp.toPx())) }
        drawLine(ring.copy(alpha = 0.3f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
        drawLine(ring.copy(alpha = 0.3f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
        listOf("N" to Offset(c.x, c.y - r), "E" to Offset(c.x + r, c.y), "S" to Offset(c.x, c.y + r), "W" to Offset(c.x - r, c.y)).forEach { (t, p) ->
            val layout = measurer.measure(t, TextStyle(color = labelColor, fontSize = 12.sp))
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
        sats.forEach { s ->
            if (s.elevation < 0) return@forEach
            val dist = (90f - s.elevation) / 90f * r
            val a = Math.toRadians(s.azimuth.toDouble())
            val p = Offset(c.x + dist * sin(a).toFloat(), c.y - dist * cos(a).toFloat())
            val color = satColor(s.constellation)
            if (s.used) drawCircle(color, 7.dp.toPx(), p) else drawCircle(color, 6.dp.toPx(), p, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun SatRow(s: Sat) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(satColor(s.constellation), CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.width(140.dp)) {
            Text("${Gnss.shortName(s.constellation)} #${s.svid}" + if (s.used) " ✓" else "", style = MaterialTheme.typography.bodyMedium)
            HintText(listOfNotNull(Gnss.bandName(s.freqMhz), "elev ${s.elevation.toInt()}°").joinToString(" · "))
        }
        LinearProgressIndicator(progress = { (s.cn0 / 50f).coerceIn(0f, 1f) }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        MonoText(String.format(Locale.getDefault(), "%.0f dB-Hz", s.cn0))
    }
}
