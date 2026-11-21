@file:OptIn(ExperimentalMaterial3Api::class)

package com.daniel.hardwarelens

import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun NfcScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SubTabs(listOf("Latest scan", "History (${NfcHistory.items.size})"), tab) { tab = it }
        Box(Modifier.weight(1f)) {
            if (tab == 0) LatestScan() else ScanHistory(onOpen = { NfcState.lastScan.value = it; tab = 0 })
        }
    }
}

@Composable
private fun LatestScan() {
    val ctx = LocalContext.current
    val adapter = remember { NfcAdapter.getDefaultAdapter(ctx) }
    var enabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    LaunchedEffect(Unit) {
        while (true) { enabled = adapter?.isEnabled == true; delay(1500) }
    }
    val scan by NfcState.lastScan
    val s = scan

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (adapter == null) {
            item { Text("This phone has no NFC chip.", color = MaterialTheme.colorScheme.error) }
        } else if (!enabled) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("NFC is turned off.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                            Text("Open NFC settings")
                        }
                    }
                }
            }
        }

        item { WaitingForTag(hasScan = s != null) }

        if (s != null) scanDetails(s, onShare = { ctx.shareText("NFC scan: ${s.title}", s.toReport()) })
    }
}

private fun LazyListScope.scanDetails(s: NfcScan, onShare: () -> Unit) {
    item {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share this scan") }
                }
                Text("Read ${dateTime(s.timestamp)}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                InfoRow("Chip ID (UID)", s.uid.ifBlank { "none" })
                s.uidNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                InfoRow("Technologies the chip speaks", s.techs.joinToString(", "))
            }
        }
    }
    item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("What the phone sees")
                s.details.forEach { (k, v) -> InfoRow(k, v) }
            }
        }
    }
    if (s.tlv.isNotEmpty()) {
        item { SectionTitle("Decoded response (tag → value tree)") }
        items(Tlv.flatten(s.tlv)) { (depth, node) -> TlvRow(depth, node) }
    }
    s.raw?.let { bytes ->
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("Raw bytes received (${bytes.size})")
                    Text(
                        bytes.toList().chunked(8).joinToString("\n") { it.toByteArray().hex(" ") },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
    s.note?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun ScanHistory(onOpen: (NfcScan) -> Unit) {
    val ctx = LocalContext.current
    val list = NfcHistory.items
    var confirmClear by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            HintText("Your last 50 scans, saved only on this phone. Tap one to see it again.")
        }
        if (list.isEmpty()) {
            item { Text("Nothing scanned yet.", modifier = Modifier.padding(top = 16.dp)) }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        ctx.shareText("Hardware Lens NFC history", list.joinToString("\n----------\n\n") { it.toReport() })
                    }) {
                        Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Export all")
                    }
                    TextButton(onClick = { confirmClear = true }) { Text("Clear") }
                }
            }
            items(list.toList()) { s ->
                Card(onClick = { onOpen(s) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.title, style = MaterialTheme.typography.titleSmall)
                        HintText("${dateTime(s.timestamp)} · UID ${s.uid.ifBlank { "none" }}")
                        HintText(s.techs.joinToString(", "))
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear NFC history?") },
            text = { Text("This deletes all ${list.size} saved scans.") },
            confirmButton = { TextButton(onClick = { NfcHistory.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WaitingForTag(hasScan: Boolean) {
    val t = rememberInfiniteTransition(label = "pulse")
    val scale by t.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale"
    )
    Column(
        Modifier.fillMaxWidth().padding(vertical = if (hasScan) 4.dp else 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Nfc, contentDescription = null,
            modifier = Modifier.size(if (hasScan) 40.dp else 96.dp).scale(scale),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasScan) "Tap another card or tag to read it"
            else "Hold a card, key fob, transit pass, or NFC sticker flat against the back of your phone",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun TlvRow(depth: Int, node: TlvNode) {
    Row(Modifier.fillMaxWidth().padding(start = (depth * 16).dp)) {
        Column {
            Text(
                node.tag + if (node.name.isNotBlank()) "  ${node.name}" else "",
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp
            )
            if (node.children.isEmpty()) {
                val printable = node.value.isNotEmpty() && node.value.all { it in 0x20..0x7E }
                val shown = if (printable) "\"${String(node.value, Charsets.US_ASCII)}\"  (${node.value.hex(" ")})"
                else node.value.hex(" ")
                Text(shown, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}
