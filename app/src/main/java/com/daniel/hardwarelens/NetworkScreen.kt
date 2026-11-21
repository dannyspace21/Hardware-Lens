package com.daniel.hardwarelens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun NetworkScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val found = remember { mutableStateMapOf<String, FoundDevice>() }
    var running by remember { mutableStateOf(false) }
    var lastRun by remember { mutableStateOf<String?>(null) }
    val myIp = remember { NetworkDiscovery.myIpv4(ctx) }

    fun start() {
        if (running) return
        running = true
        found.clear()
        scope.launch {
            try {
                NetworkDiscovery.run(ctx) { d ->
                    // Keep the first answer per device, but always refresh PlayStation power state.
                    if (d.key !in found || d.via == "PlayStation discovery") found[d.key] = d
                }
            } finally {
                running = false
                lastRun = timeNow()
            }
        }
    }

    LaunchedEffect(Unit) { start() }

    val list = found.values.sortedWith(compareBy({ !isConsole(it.kind) }, { it.kind }, { it.name }))

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Devices on your Wi-Fi", style = MaterialTheme.typography.titleMedium)
                    Text("Your phone: ${myIp ?: "not connected"}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Finds game consoles (PS4/PS5, Xbox), TVs, Chromecasts, speakers, printers and routers " +
                            "that announce themselves. Your phone must be on the same Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { start() }, enabled = !running) {
                            Text(if (running) "Scanning…" else "Scan again")
                        }
                        Spacer(Modifier.width(12.dp))
                        if (running) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        } else {
                            lastRun?.let { Text("${found.size} found · $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
        items(list, key = { it.key }) { d -> DeviceRow(d) }
    }
}

private fun iconFor(kind: String): ImageVector {
    val k = kind.lowercase()
    return when {
        isConsole(kind) -> Icons.Filled.SportsEsports
        "router" in k -> Icons.Filled.Router
        "printer" in k -> Icons.Filled.Print
        "speaker" in k || "sonos" in k -> Icons.Filled.Speaker
        "chromecast" in k || "airplay" in k -> Icons.Filled.Cast
        "tv" in k || "roku" in k || "media" in k -> Icons.Filled.Tv
        "hue" in k || "homekit" in k -> Icons.Filled.Lightbulb
        "pc" in k || "windows" in k -> Icons.Filled.Computer
        else -> Icons.Filled.Devices
    }
}

@Composable
private fun DeviceRow(d: FoundDevice) {
    val console = isConsole(d.kind)
    val colors = if (console) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else CardDefaults.cardColors()
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(iconFor(d.kind), contentDescription = d.kind, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleSmall)
                Text(listOf(d.kind, d.ip).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                if (d.detail.isNotBlank()) Text(d.detail, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text("via ${d.via}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
