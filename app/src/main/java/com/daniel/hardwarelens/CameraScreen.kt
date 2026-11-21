package com.daniel.hardwarelens

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.sqrt

class CamInfo(val title: String, val subtitle: String, val details: List<Pair<String, String>>)

private fun f(pattern: String, vararg args: Any) = String.format(Locale.getDefault(), pattern, *args)

private fun describe(id: String, ch: CameraCharacteristics, physicalOf: String? = null): CamInfo {
    val d = mutableListOf<Pair<String, String>>()
    val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
        CameraCharacteristics.LENS_FACING_FRONT -> "Front"
        CameraCharacteristics.LENS_FACING_BACK -> "Back"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
        else -> "Unknown"
    }
    val pixels = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
    val mp = pixels?.let { it.width.toDouble() * it.height / 1e6 }
    val phys = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    val diag = phys?.let { sqrt(it.width * it.width + it.height * it.height) }
    val eq35 = if (focal != null && diag != null && diag > 0) focal * 43.27f / diag else null
    val lensType = when {
        facing != "Back" || eq35 == null -> null
        eq35 < 20 -> "Ultra-wide"
        eq35 < 35 -> "Wide (main)"
        eq35 > 50 -> "Telephoto"
        else -> "Standard"
    }

    if (mp != null && pixels != null) d += "Sensor resolution" to f("%.1f MP (%d × %d)", mp, pixels.width, pixels.height)
    if (phys != null) d += "Sensor size" to f("%.2f × %.2f mm", phys.width, phys.height)
    if (focal != null) d += "Focal length" to (f("%.2f mm", focal) + (eq35?.let { f(" (≈ %.0f mm on a full-frame camera)", it) } ?: ""))
    ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.let { a ->
        if (a.isNotEmpty()) d += "Aperture" to a.joinToString(" / ") { f("f/%.1f", it) }
    }
    ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)?.let { d += "Flash" to if (it) "Yes" else "No" }
    ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.let { d += "Optical stabilization" to if (it.contains(1)) "Yes" else "No" }
    if (Build.VERSION.SDK_INT >= 30) {
        ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { d += "Zoom range" to f("%.1f× – %.1f×", it.lower, it.upper) }
    } else {
        ch.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.let { d += "Max digital zoom" to f("%.1f×", it) }
    }
    ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { d += "ISO range" to "${it.lower} – ${it.upper}" }
    ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { r ->
        val fastest = r.lower.toDouble() / 1e9
        val slowest = r.upper.toDouble() / 1e9
        d += "Shutter speed" to (f("1/%.0f s", 1 / fastest) + " – " + if (slowest >= 1) f("%.1f s", slowest) else f("1/%.0f s", 1 / slowest))
    }
    val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }?.let {
        d += "Largest photo" to f("%d × %d (%.1f MP)", it.width, it.height, it.width.toLong() * it.height / 1e6)
    }
    runCatching { map?.highSpeedVideoFpsRanges?.maxOfOrNull { it.upper } }.getOrNull()?.let { d += "Slow-motion video" to "up to $it fps" }
    val level = when (ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy (basic)"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3 (best)"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
        else -> null
    }
    level?.let { d += "Camera API support" to it }
    val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet().orEmpty()
    val capNames = buildList {
        if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW in caps) add("RAW photos")
        if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps) add("Manual controls")
        if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE in caps) add("Burst")
        if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT in caps) add("Depth")
        if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in caps) add("Combines several lenses")
    }
    if (capNames.isNotEmpty()) d += "Features" to capNames.joinToString(", ")

    val title = listOfNotNull(
        if (physicalOf != null) "Lens $id (part of camera $physicalOf)" else "$facing camera",
        lensType,
    ).joinToString(" · ")
    val subtitle = listOfNotNull("ID $id", mp?.let { f("%.0f MP", it) }, focal?.let { f("%.1f mm", it) }).joinToString(" · ")
    return CamInfo(title, subtitle, d)
}

private fun readCameras(cm: CameraManager): List<CamInfo> {
    val out = mutableListOf<CamInfo>()
    val seen = mutableSetOf<String>()
    for (id in runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())) {
        val ch = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: continue
        out += describe(id, ch)
        seen += id
        if (Build.VERSION.SDK_INT >= 28) {
            for (pid in ch.physicalCameraIds) {
                if (pid in seen) continue
                val pch = runCatching { cm.getCameraCharacteristics(pid) }.getOrNull() ?: continue
                out += describe(pid, pch, physicalOf = id)
                seen += pid
            }
        }
    }
    return out
}

@Composable
fun CameraScreen() {
    val ctx = LocalContext.current
    val cams = remember { ctx.getSystemService(CameraManager::class.java)?.let { readCameras(it) }.orEmpty() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Every camera Android exposes, read straight from the hardware descriptions. " +
                    "No photos are taken and no camera permission is needed. Some phones hide extra lenses from apps.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (cams.isEmpty()) item { Text("No cameras reported.") }
        items(cams) { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(c.title, style = MaterialTheme.typography.titleMedium)
                    HintText(c.subtitle)
                    Spacer(Modifier.height(6.dp))
                    c.details.forEach { (k, v) -> InfoRow(k, v) }
                }
            }
        }
    }
}
