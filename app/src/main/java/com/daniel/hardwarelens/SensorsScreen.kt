@file:OptIn(ExperimentalMaterial3Api::class)

package com.daniel.hardwarelens

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

enum class SensorGroup(val title: String, val intro: String) {
    Motion("Motion", "How the phone moves. Shake it, tilt it, spin it on the table."),
    Position("Position", "Which way the phone is pointing, and what's in front of the screen."),
    Environment("Environment", "What the phone can sense about the room around it."),
}

class SensorSpec(
    val type: Int,
    val label: String,
    val unit: String,
    val axes: List<String>,
    val group: SensorGroup,
    val blurb: String,
    val transform: ((FloatArray) -> FloatArray)? = null,
)

private fun deg(rad: Float) = Math.toDegrees(rad.toDouble()).toFloat()

/** Rotation vector -> azimuth / pitch / roll in degrees. */
fun orientationDegrees(v: FloatArray): FloatArray {
    val r = FloatArray(9)
    SensorManager.getRotationMatrixFromVector(r, v.copyOf(minOf(v.size, 4)))
    val o = FloatArray(3)
    SensorManager.getOrientation(r, o)
    return floatArrayOf((deg(o[0]) + 360f) % 360f, deg(o[1]), deg(o[2]))
}

val sensorSpecs = listOf(
    SensorSpec(Sensor.TYPE_ACCELEROMETER, "Accelerometer", "m/s²", listOf("X", "Y", "Z"), SensorGroup.Motion,
        "Total push on the phone, including gravity. Lying flat, Z reads about 9.8."),
    SensorSpec(Sensor.TYPE_LINEAR_ACCELERATION, "Linear acceleration", "m/s²", listOf("X", "Y", "Z"), SensorGroup.Motion,
        "Acceleration with gravity removed — only your movement. Still phone = all zeros."),
    SensorSpec(Sensor.TYPE_GRAVITY, "Gravity", "m/s²", listOf("X", "Y", "Z"), SensorGroup.Motion,
        "Which way is down, in the phone's own X/Y/Z directions."),
    SensorSpec(Sensor.TYPE_GYROSCOPE, "Gyroscope", "rad/s", listOf("X", "Y", "Z"), SensorGroup.Motion,
        "How fast the phone is spinning around each axis."),
    SensorSpec(Sensor.TYPE_ROTATION_VECTOR, "Orientation", "°", listOf("Heading", "Pitch", "Roll"), SensorGroup.Position,
        "Compass heading plus how far the phone is tipped forward/back (pitch) and sideways (roll).",
        transform = ::orientationDegrees),
    SensorSpec(Sensor.TYPE_MAGNETIC_FIELD, "Magnetometer", "µT", listOf("X", "Y", "Z"), SensorGroup.Position,
        "Magnetic field strength. Earth is about 25–65 µT — wave a magnet or speaker nearby to see a spike."),
    SensorSpec(Sensor.TYPE_PROXIMITY, "Proximity", "cm", listOf(""), SensorGroup.Position,
        "Distance to whatever is in front of the screen. Many phones only report near / far."),
    SensorSpec(Sensor.TYPE_LIGHT, "Light", "lux", listOf(""), SensorGroup.Environment,
        "Dark room < 10 lux · living room ~100 · office ~400 · daylight 10,000+."),
    SensorSpec(Sensor.TYPE_PRESSURE, "Barometer", "hPa", listOf(""), SensorGroup.Environment,
        "Air pressure. Sea level is about 1013 hPa and it drops ~0.12 hPa per meter you climb."),
    SensorSpec(Sensor.TYPE_AMBIENT_TEMPERATURE, "Thermometer", "°C", listOf(""), SensorGroup.Environment,
        "Air temperature around the phone (rare — most phones don't have one)."),
    SensorSpec(Sensor.TYPE_RELATIVE_HUMIDITY, "Humidity", "%", listOf(""), SensorGroup.Environment,
        "Relative humidity of the air (rare)."),
)

@Composable
fun SensorsScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SubTabs(listOf("Motion", "Position", "Environment", "Tools", "All sensors"), tab) { tab = it }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> SensorGroupPage(SensorGroup.Motion)
                1 -> SensorGroupPage(SensorGroup.Position)
                2 -> SensorGroupPage(SensorGroup.Environment)
                3 -> SensorToolsPage()
                else -> SensorInventoryPage()
            }
        }
    }
}

@Composable
private fun SensorGroupPage(group: SensorGroup) {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(SensorManager::class.java) }
    val specs = remember(group) { sensorSpecs.filter { it.group == group } }
    val present = remember(group) { specs.mapNotNull { s -> sm.getDefaultSensor(s.type)?.let { s to it } } }
    val missing = remember(group) { specs.filter { s -> present.none { it.first === s } } }

    val recorder = remember(group) { SensorRecorder(ctx.applicationContext, sm) }
    var recording by remember(group) { mutableStateOf(false) }
    var rows by remember(group) { mutableLongStateOf(0L) }
    LaunchedEffect(recording) { while (recording) { rows = recorder.rows; delay(500) } }
    DisposableEffect(recorder) { onDispose { recorder.stop() } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(group.intro, style = MaterialTheme.typography.bodyMedium) }
        if (present.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Record to CSV", style = MaterialTheme.typography.titleSmall)
                        HintText(
                            if (recording) "Recording… $rows readings so far"
                            else "Saves every reading from the sensors below to a spreadsheet file you can share."
                        )
                    }
                    if (recording) {
                        FilledTonalButton(onClick = {
                            val f = recorder.stop()
                            recording = false
                            if (f != null) ctx.shareFile(f, "text/csv")
                        }) {
                            Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(4.dp)); Text("Stop & share")
                        }
                    } else {
                        FilledTonalButton(onClick = { recorder.start(present); recording = true }) {
                            Icon(Icons.Filled.FiberManualRecord, null, tint = Color(0xFFE53935)); Spacer(Modifier.width(4.dp)); Text("Record")
                        }
                    }
                }
            }
        }
        items(present, key = { it.first.type }) { (spec, sensor) -> LiveSensorCard(sm, sensor, spec) }
        if (missing.isNotEmpty()) item {
            HintText("Not on this phone: " + missing.joinToString { it.label })
        }
    }
}

@Composable
private fun LiveSensorCard(sm: SensorManager, sensor: Sensor, spec: SensorSpec) {
    val axes = spec.axes.size
    val history = remember { List(axes) { mutableStateListOf<Float>() } }
    var latest by remember { mutableStateOf(FloatArray(0)) }

    DisposableEffect(sensor) {
        var lastUi = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val now = SystemClock.uptimeMillis()
                if (now - lastUi < 40) return   // ~25 updates a second is plenty for the screen
                lastUi = now
                val v = spec.transform?.invoke(e.values) ?: e.values.copyOf()
                latest = v
                for (i in 0 until minOf(axes, v.size)) history[i].push(v[i])
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(spec.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(spec.unit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HintText(spec.blurb)
            Spacer(Modifier.height(8.dp))
            if (latest.isEmpty()) {
                Text("waiting for data…")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until minOf(axes, latest.size)) AxisValue(spec.axes[i], latest[i], Palette[i])
                }
                if (spec.type == Sensor.TYPE_PRESSURE) {
                    val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, latest[0]).toDouble()
                    HintText("≈ ${Fmt.altitude(alt, Prefs.units.value)} above sea level (rough — weather moves this)")
                }
            }
            Spacer(Modifier.height(8.dp))
            LineChart(history)
            Text(
                "${sensor.name} · ${sensor.vendor}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AxisValue(name: String, v: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        val num = String.format(Locale.getDefault(), "%.2f", v)
        Text(if (name.isEmpty()) num else "$name $num", fontFamily = FontFamily.Monospace)
    }
}

// ---------- Inventory ----------

@Suppress("DEPRECATION")
private fun inventoryGroup(type: Int): String = when (type) {
    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, Sensor.TYPE_GYROSCOPE,
    Sensor.TYPE_GYROSCOPE_UNCALIBRATED, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GRAVITY,
    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_SIGNIFICANT_MOTION,
    Sensor.TYPE_MOTION_DETECT, Sensor.TYPE_STATIONARY_DETECT -> "Motion"
    Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
    Sensor.TYPE_PROXIMITY, Sensor.TYPE_ORIENTATION, Sensor.TYPE_POSE_6DOF -> "Position"
    Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE, Sensor.TYPE_AMBIENT_TEMPERATURE,
    Sensor.TYPE_RELATIVE_HUMIDITY, Sensor.TYPE_TEMPERATURE -> "Environment"
    Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR, Sensor.TYPE_HEART_RATE, Sensor.TYPE_HEART_BEAT,
    Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT -> "Activity & body"
    else -> "Other / maker-specific"
}

private val inventoryOrder = listOf("Motion", "Position", "Environment", "Activity & body", "Other / maker-specific")

@Composable
private fun SensorInventoryPage() {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(SensorManager::class.java) }
    val all = remember { sm.getSensorList(Sensor.TYPE_ALL) }
    val grouped = remember { all.groupBy { inventoryGroup(it.type) } }
    var expanded by rememberSaveable { mutableStateOf(listOf("Motion")) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("${all.size} sensors on this phone, grouped by what they measure. Tap a group to open it.",
                style = MaterialTheme.typography.bodyMedium)
        }
        inventoryOrder.forEach { g ->
            val list = grouped[g].orEmpty()
            if (list.isEmpty()) return@forEach
            val open = g in expanded
            item(key = "h-$g") {
                Card(onClick = { expanded = if (open) expanded - g else expanded + g }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(g, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${list.size}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                    }
                }
            }
            if (open) items(list) { s -> SensorInfoRow(s) }
        }
    }
}

@Composable
private fun SensorInfoRow(s: Sensor) {
    val friendlyType = s.stringType.substringAfterLast('.').replace('_', ' ')
    val maxHz = if (s.minDelay > 0) 1_000_000f / s.minDelay else null
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(s.name, style = MaterialTheme.typography.bodyLarge)
        HintText(
            buildString {
                append(friendlyType)
                if (s.isWakeUpSensor) append(" · wake-up")
                append(" · ${s.vendor} v${s.version}")
            }
        )
        HintText(
            buildString {
                append("Range ${s.maximumRange} · resolution ${s.resolution} · ${s.power} mA")
                if (maxHz != null) append(String.format(Locale.getDefault(), " · up to %.0f Hz", maxHz))
            }
        )
    }
}
