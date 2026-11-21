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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

        if (s != null) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(s.title, style = MaterialTheme.typography.headlineSmall)
                        Text("Read at ${s.time}", style = MaterialTheme.typography.bodySmall)
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
