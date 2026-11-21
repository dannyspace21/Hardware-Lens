@file:OptIn(ExperimentalMaterial3Api::class)

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

private val statusOrder = listOf("network", "ps", "ssdp", "mdns", "sweep")

@Composable
fun NetworkScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val found = remember { mutableStateMapOf<String, FoundDevice>() }
    val statuses = remember { mutableStateMapOf<String, String>() }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var lastRun by remember { mutableStateOf<String?>(null) }
    var showHow by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }

    fun start() {
        if (running) return
        running = true
        progress = 0f
        found.clear()
        statuses.clear()
        scope.launch {
            try {
                NetworkDiscovery.run(
                    ctx,
                    onFound = { d ->
                        val k = d.key
                        found[k] = found[k]?.let { Devices.merge(it, d) } ?: d
                    },
                    onStatus = { k, v -> statuses[k] = v },
                    onProgress = { progress = it },
                )
            } finally {
                running = false
                lastRun = timeNow()
            }
        }
    }

    LaunchedEffect(Unit) { start() }

    val list = found.values.sortedWith(Devices.displayOrder)
    val others = list.count { !it.isMe && !it.isGateway }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Devices on your Wi-Fi", style = MaterialTheme.typography.titleMedium)
                    HintText(
                        "Finds game consoles, TVs, Chromecasts, speakers, printers, phones and computers on the same Wi-Fi as your phone."
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { start() }, enabled = !running) {
                            Text(if (running) "Scanning…" else "Scan again")
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (running) "${list.size} found so far" else lastRun?.let { "${list.size} found · $it" } ?: "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (running) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    TextButton(onClick = { showHow = !showHow }) {
                        Text(if (showHow) "Hide how it searched" else "How it searched")
                    }
                    if (showHow) {
                        statusOrder.forEach { k -> statuses[k]?.let { HintText("• $it") } }
                        if (statuses.isEmpty()) HintText("Starting…")
                    }
                }
            }
        }
        if (!running && lastRun != null && others == 0) item { IsolationHint(statuses["network"]) }
        items(list, key = { it.key }) { d -> DeviceRow(d) { selected = d.key } }
    }

    val detail = selected?.let { found[it] }
    if (detail != null) DeviceDialog(detail) { selected = null }
}

@Composable
private fun IsolationHint(network: String?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Nothing else answered", style = MaterialTheme.typography.titleSmall)
            if (network?.startsWith("Not connected") == true) {
                Text("Your phone isn't on Wi-Fi. Connect to the network you want to look at.")
            } else {
                Text(
                    "Guest, school, dorm, hotel and office Wi-Fi usually block devices from seeing each other " +
                        "(\"client isolation\"), so only the router shows up. On a home network you should see much more."
                )
                Text("Also check: VPN apps can hide your local network, and some routers have an \"AP isolation\" setting.")
            }
        }
    }
}

private fun iconFor(d: FoundDevice): ImageVector {
    val k = d.kind.lowercase()
    return when {
        d.isMe -> Icons.Filled.PhoneAndroid
        d.isGateway || "router" in k -> Icons.Filled.Router
        Devices.isConsole(d.kind) || "nintendo" in k -> Icons.Filled.SportsEsports
        "printer" in k -> Icons.Filled.Print
        "iphone" in k || "ipad" in k || "android" in k || "phone" in k -> Icons.Filled.Smartphone
        "speaker" in k || "sonos" in k -> Icons.Filled.Speaker
        "chromecast" in k || "airplay" in k -> Icons.Filled.Cast
        "tv" in k || "roku" in k || "media" in k -> Icons.Filled.Tv
        "hue" in k || "homekit" in k || "smart home" in k -> Icons.Filled.Lightbulb
        "pc" in k || "windows" in k || "mac" in k || "computer" in k -> Icons.Filled.Computer
        "camera" in k -> Icons.Filled.Videocam
        else -> Icons.Filled.Devices
    }
}

@Composable
private fun DeviceRow(d: FoundDevice, onClick: () -> Unit) {
    val highlight = Devices.isConsole(d.kind) || d.isMe
    val colors = if (highlight) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = colors) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(iconFor(d), contentDescription = d.kind, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name.ifBlank { d.kind }, style = MaterialTheme.typography.titleSmall)
                Text(listOf(d.kind, d.ip).filter { it.isNotBlank() }.distinct().joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                d.details.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                Text("via ${d.sources.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun DeviceDialog(d: FoundDevice, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ports by remember(d.ip) { mutableStateOf<List<Int>?>(null) }
    var checkingPorts by remember(d.ip) { mutableStateOf(false) }
    var pings by remember(d.ip) { mutableStateOf<List<Long?>?>(null) }
    var pinging by remember(d.ip) { mutableStateOf(false) }
    val web = ports?.let { Ports.webUrl(d.ip, it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(d.name.ifBlank { d.kind }) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { InfoRow("Looks like", d.kind) }
                if (d.ip.isNotBlank()) item { InfoRow("IP address", d.ip) }
                item { InfoRow("Found by", d.sources.joinToString(", ")) }
                if (d.details.isNotEmpty()) item { InfoRow("Details", d.details.joinToString("\n")) }
                if (d.ip.isNotBlank() && !d.isMe) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !checkingPorts, onClick = {
                                checkingPorts = true
                                scope.launch { ports = NetworkDiscovery.openPorts(ctx, d.ip); checkingPorts = false }
                            }) { Text(if (checkingPorts) "Checking…" else "Check ports") }
                            OutlinedButton(enabled = !pinging, onClick = {
                                pinging = true
                                scope.launch {
                                    pings = NetworkDiscovery.ping(ctx, d.ip, d.openPort ?: ports?.firstOrNull() ?: 80)
                                    pinging = false
                                }
                            }) { Text(if (pinging) "Pinging…" else "Ping") }
                        }
                    }
                    ports?.let { p ->
                        item {
                            InfoRow(
                                "Open ports (${Ports.common.size} common ones checked)",
                                if (p.isEmpty()) "None open — it may still be there with everything closed."
                                else p.joinToString("\n") { "$it  ${Ports.name(it)}" }
                            )
                        }
                    }
                    pings?.let { p ->
                        val ok = p.filterNotNull()
                        item {
                            InfoRow(
                                "Response time",
                                if (ok.isEmpty()) "No answer" else "${ok.size}/${p.size} answered · " +
                                    "min ${ok.min()} ms · avg ${ok.average().toInt()} ms · max ${ok.max()} ms"
                            )
                        }
                    }
                    if (web != null) item { TextButton(onClick = { ctx.openUrl(web) }) { Text("Open its web page") } }
                }
            }
        }
    )
}
