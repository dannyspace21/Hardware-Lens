package com.daniel.hardwarelens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes sensor readings to a CSV file in the cache folder, on a background thread. */
class SensorRecorder(private val ctx: Context, private val sm: SensorManager) {
    private var thread: HandlerThread? = null
    private var writer: BufferedWriter? = null
    private var file: File? = null
    private val listeners = mutableListOf<SensorEventListener>()

    @Volatile var rows = 0L
        private set

    val isRecording get() = writer != null

    fun start(sensors: List<Pair<SensorSpec, Sensor>>) {
        if (isRecording) return
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // keep only the newest export
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val f = File(dir, "hardware-lens-sensors-$stamp.csv")
        val w = f.bufferedWriter()
        w.write("time_ms,sensor,value1,value2,value3,unit\n")
        val t = HandlerThread("sensor-recorder").apply { start() }
        val handler = Handler(t.looper)
        val t0 = SystemClock.elapsedRealtimeNanos()
        rows = 0
        for ((spec, sensor) in sensors) {
            val l = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    val v = spec.transform?.invoke(e.values) ?: e.values
                    val n = minOf(v.size, spec.axes.size)
                    val vals = (0 until 3).joinToString(",") { i -> if (i < n) v[i].toString() else "" }
                    val ms = (e.timestamp - t0) / 1_000_000.0
                    runCatching { w.write(String.format(Locale.US, "%.1f,%s,%s,%s\n", ms, spec.label, vals, spec.unit)) }
                    rows++
                }
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME, handler)
            listeners += l
        }
        writer = w
        file = f
        thread = t
    }

    /** Stops recording and returns the finished file (or null if nothing was recording). */
    fun stop(): File? {
        if (!isRecording) return null
        listeners.forEach { sm.unregisterListener(it) }
        listeners.clear()
        val t = thread
        val w = writer
        thread = null
        writer = null
        t?.quitSafely()
        runCatching { t?.join(500) }
        runCatching { w?.flush(); w?.close() }
        return file
    }
}
