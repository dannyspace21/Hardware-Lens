package com.daniel.hardwarelens

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private data class Featured(val type: Int, val label: String, val unit: String, val axes: Int)

private val featured = listOf(
    Featured(Sensor.TYPE_ACCELEROMETER, "Accelerometer", "m/s²", 3),
    Featured(Sensor.TYPE_GYROSCOPE, "Gyroscope (rotation)", "rad/s", 3),
    Featured(Sensor.TYPE_MAGNETIC_FIELD, "Magnetometer (compass)", "µT", 3),
    Featured(Sensor.TYPE_LIGHT, "Light", "lux", 1),
    Featured(Sensor.TYPE_PROXIMITY, "Proximity", "cm", 1),
    Featured(Sensor.TYPE_PRESSURE, "Barometer", "hPa", 1),
)

@Composable
fun SensorsScreen() {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(SensorManager::class.java) }
    val all = remember { sm.getSensorList(Sensor.TYPE_ALL) }
    val live = remember { featured.mapNotNull { f -> sm.getDefaultSensor(f.type)?.let { f to it } } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Shake, tilt, cover, or shine a light on your phone and watch the graphs react. " +
                    "Red/green/blue lines = X/Y/Z.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        items(live, key = { it.first.type }) { (f, s) -> LiveSensorCard(sm, s, f) }
        item { SectionTitle("All ${all.size} sensors on this phone") }
        items(all) { s ->
            Column {
                Text(s.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${s.vendor} · max ${s.maximumRange} · ${s.power} mA",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LiveSensorCard(sm: SensorManager, sensor: Sensor, f: Featured) {
    val history = remember { List(f.axes) { mutableStateListOf<Float>() } }
    var latest by remember { mutableStateOf(FloatArray(0)) }

    DisposableEffect(sensor) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                latest = e.values.copyOf()
                for (i in 0 until minOf(f.axes, e.values.size)) {
                    val h = history[i]
                    h.add(e.values[i])
                    if (h.size > MAX_POINTS) h.removeAt(0)
                }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(f.label, style = MaterialTheme.typography.titleMedium)
            val axisNames = listOf("X", "Y", "Z")
            val text = if (latest.isEmpty()) "waiting for data…" else
                (0 until minOf(f.axes, latest.size)).joinToString("   ") { i ->
                    (if (f.axes > 1) axisNames[i] + " " else "") + "%.2f".format(latest[i])
                } + "  ${f.unit}"
            Text(text, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            LineChart(history)
        }
    }
}
