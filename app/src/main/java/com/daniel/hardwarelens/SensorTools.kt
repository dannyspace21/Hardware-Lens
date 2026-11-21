package com.daniel.hardwarelens

import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Latest values from one sensor type, or null until it reports (or if the phone lacks it). */
@Composable
fun rememberSensorValues(type: Int, rate: Int = SensorManager.SENSOR_DELAY_UI): State<FloatArray?> {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(SensorManager::class.java) }
    val sensor = remember(type) { sm.getDefaultSensor(type) }
    val state = remember(type) { mutableStateOf<FloatArray?>(null) }
    DisposableEffect(sensor) {
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { state.value = e.values.copyOf() }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, rate)
        onDispose { sm.unregisterListener(l) }
    }
    return state
}

@Composable
private fun hasSensor(type: Int): Boolean {
    val ctx = LocalContext.current
    return remember(type) { ctx.getSystemService(SensorManager::class.java).getDefaultSensor(type) != null }
}

@Composable
fun SensorToolsPage() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CompassCard() }
        item { LevelCard() }
        item { StepsCard() }
    }
}

@Composable
private fun ToolCard(title: String, hint: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            HintText(hint)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private fun azimuthFrom(acc: FloatArray?, mag: FloatArray?): Float? {
    if (acc == null || mag == null) return null
    val r = FloatArray(9)
    if (!SensorManager.getRotationMatrix(r, null, acc, mag)) return null
    val o = FloatArray(3)
    SensorManager.getOrientation(r, o)
    return ((Math.toDegrees(o[0].toDouble()).toFloat()) + 360f) % 360f
}

@Composable
private fun CompassCard() {
    val hasRot = hasSensor(Sensor.TYPE_ROTATION_VECTOR)
    val rot by rememberSensorValues(if (hasRot) Sensor.TYPE_ROTATION_VECTOR else -1)
    val acc by rememberSensorValues(if (hasRot) -1 else Sensor.TYPE_ACCELEROMETER)
    val mag by rememberSensorValues(if (hasRot) -1 else Sensor.TYPE_MAGNETIC_FIELD)
    val heading = rot?.let { orientationDegrees(it)[0] } ?: azimuthFrom(acc, mag)

    ToolCard("Compass", "Points to magnetic north. Hold the phone flat, away from metal, magnets and laptops.") {
        if (heading == null) {
            Text(if (hasRot || hasSensor(Sensor.TYPE_MAGNETIC_FIELD)) "Waiting for the compass…" else "This phone has no compass sensor.")
        } else {
            Text(String.format(Locale.getDefault(), "%.0f° %s", heading, cardinal(heading)), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            CompassRose(heading)
        }
    }
}

@Composable
private fun CompassRose(heading: Float) {
    val ring = MaterialTheme.colorScheme.outline
    val text = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.size(220.dp)) {
        val c = center
        val r = size.minDimension / 2f * 0.9f
        // fixed marker at the top = the way the phone is pointing
        drawLine(accent, Offset(c.x, c.y - r - 6.dp.toPx()), Offset(c.x, c.y - r + 14.dp.toPx()), 4.dp.toPx())
        rotate(-heading, c) {
            drawCircle(ring, r, c, style = Stroke(2.dp.toPx()))
            for (d in 0 until 360 step 15) {
                val a = Math.toRadians(d.toDouble())
                val inner = if (d % 90 == 0) r * 0.82f else r * 0.9f
                drawLine(
                    ring,
                    Offset(c.x + inner * sin(a).toFloat(), c.y - inner * cos(a).toFloat()),
                    Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat()),
                    if (d % 90 == 0) 3.dp.toPx() else 1.dp.toPx()
                )
            }
            listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (label, d) ->
                val a = Math.toRadians(d)
                val p = Offset(c.x + r * 0.65f * sin(a).toFloat(), c.y - r * 0.65f * cos(a).toFloat())
                val layout = measurer.measure(
                    label,
                    TextStyle(color = if (label == "N") Color(0xFFE53935) else text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
            }
            val needle = Path().apply {
                moveTo(c.x, c.y - r * 0.45f)
                lineTo(c.x - 10.dp.toPx(), c.y)
                lineTo(c.x + 10.dp.toPx(), c.y)
                close()
            }
            drawPath(needle, Color(0xFFE53935))
        }
        drawCircle(text, 5.dp.toPx(), c)
    }
}

@Composable
private fun LevelCard() {
    val hasGravity = hasSensor(Sensor.TYPE_GRAVITY)
    val g by rememberSensorValues(if (hasGravity) Sensor.TYPE_GRAVITY else Sensor.TYPE_ACCELEROMETER)
    ToolCard("Bubble level", "Lay the phone on a shelf or picture frame. The bubble floats to the high side.") {
        val v = g
        if (v == null || v.size < 3) {
            Text("Waiting for the motion sensor…")
            return@ToolCard
        }
        val norm = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(0.1f)
        val tiltX = Math.toDegrees(atan2(v[0].toDouble(), v[2].toDouble())).toFloat()
        val tiltY = Math.toDegrees(atan2(v[1].toDouble(), v[2].toDouble())).toFloat()
        val level = abs(tiltX) < 1f && abs(tiltY) < 1f
        Text(
            if (level) "Level ✓" else String.format(Locale.getDefault(), "Left/right %.1f°   Front/back %.1f°", tiltX, tiltY),
            style = MaterialTheme.typography.titleLarge,
            color = if (level) Color(0xFF43A047) else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        val ring = MaterialTheme.colorScheme.outline
        val bubble = if (level) Color(0xFF43A047) else MaterialTheme.colorScheme.primary
        Canvas(Modifier.size(200.dp)) {
            val c = center
            val r = size.minDimension / 2f * 0.95f
            drawCircle(ring, r, c, style = Stroke(2.dp.toPx()))
            drawCircle(ring, r * 0.15f, c, style = Stroke(1.dp.toPx()))
            drawLine(ring.copy(alpha = 0.4f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
            drawLine(ring.copy(alpha = 0.4f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
            // ~3x exaggeration so small tilts are visible; clamp to the ring
            var bx = v[0] / norm * r * 3f
            var by = -v[1] / norm * r * 3f
            val d = sqrt(bx * bx + by * by)
            val max = r * 0.85f
            if (d > max) { bx *= max / d; by *= max / d }
            drawCircle(bubble.copy(alpha = 0.8f), r * 0.13f, Offset(c.x + bx, c.y + by))
        }
    }
}

@Composable
private fun StepsCard() {
    val perms = if (Build.VERSION.SDK_INT >= 29) arrayOf(Manifest.permission.ACTIVITY_RECOGNITION) else emptyArray()
    val hasCounter = hasSensor(Sensor.TYPE_STEP_COUNTER)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Step counter", style = MaterialTheme.typography.titleMedium)
            HintText("A low-power chip counts steps even when the app is closed. Walk around with the phone to see it go up.")
            Spacer(Modifier.height(8.dp))
            if (!hasCounter) {
                Text("This phone has no step counter.")
            } else {
                Box(Modifier.heightIn(min = 60.dp)) {
                    PermissionGate(perms, "Counting steps needs the \"Physical activity\" permission.") {
                        val counter by rememberSensorValues(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL)
                        var base by remember { mutableStateOf<Float?>(null) }
                        val total = counter?.firstOrNull()
                        LaunchedEffect(total) { if (total != null && base == null) base = total }
                        if (total == null) {
                            Text("Waiting for the first step…")
                        } else {
                            Row(Modifier.fillMaxWidth()) {
                                StatTile("While this screen is open", "${(total - (base ?: total)).toInt()}", Modifier.weight(1f))
                                StatTile("Since the phone restarted", "${total.toInt()}", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
