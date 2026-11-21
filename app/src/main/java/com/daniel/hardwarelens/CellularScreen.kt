package com.daniel.hardwarelens

import android.annotation.SuppressLint
import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class CellRow(
    val tech: String,
    val registered: Boolean,
    val dbm: Int?,
    val level: Int,
    val details: List<Pair<String, String>>,
)

private fun Int.ok(): Int? = takeIf { it != Int.MAX_VALUE && it != Int.MIN_VALUE }
private fun Long.ok(): Long? = takeIf { it != Long.MAX_VALUE && it != Long.MIN_VALUE }

private fun MutableList<Pair<String, String>>.add(label: String, v: Any?, unit: String = "") {
    if (v != null) add(label to "$v$unit")
}

private fun toRow(c: CellInfo): CellRow {
    val d = mutableListOf<Pair<String, String>>()
    return when {
        c is CellInfoLte -> {
            val id = c.cellIdentity
            val s = c.cellSignalStrength
            d.add("RSRP (signal)", s.rsrp.ok(), " dBm")
            d.add("RSRQ (quality)", s.rsrq.ok(), " dB")
            d.add("SNR", s.rssnr.ok(), " dB")
            d.add("Physical cell ID", id.pci.ok())
            d.add("Tracking area", id.tac.ok())
            d.add("Cell ID", id.ci.ok())
            d.add("EARFCN (frequency #)", id.earfcn.ok())
            if (Build.VERSION.SDK_INT >= 28) {
                d.add("Bandwidth", id.bandwidth.ok()?.let { it / 1000 }, " MHz")
                if (id.mccString != null) d.add("Network code", "${id.mccString}-${id.mncString}")
            }
            if (Build.VERSION.SDK_INT >= 30 && id.bands.isNotEmpty()) d.add("Band", id.bands.joinToString { "B$it" })
            CellRow("4G LTE", c.isRegistered, s.dbm.ok(), s.level, d)
        }
        Build.VERSION.SDK_INT >= 29 && c is CellInfoNr -> nrRow(c)
        c is CellInfoWcdma -> {
            val id = c.cellIdentity
            d.add("Area code (LAC)", id.lac.ok())
            d.add("Cell ID", id.cid.ok())
            d.add("Scrambling code", id.psc.ok())
            d.add("UARFCN (frequency #)", id.uarfcn.ok())
            CellRow("3G (WCDMA)", c.isRegistered, c.cellSignalStrength.dbm.ok(), c.cellSignalStrength.level, d)
        }
        c is CellInfoGsm -> {
            val id = c.cellIdentity
            d.add("Area code (LAC)", id.lac.ok())
            d.add("Cell ID", id.cid.ok())
            d.add("ARFCN (frequency #)", id.arfcn.ok())
            CellRow("2G (GSM)", c.isRegistered, c.cellSignalStrength.dbm.ok(), c.cellSignalStrength.level, d)
        }
        c is CellInfoCdma -> CellRow("CDMA", c.isRegistered, c.cellSignalStrength.dbm.ok(), c.cellSignalStrength.level, d)
        else -> CellRow("Other", c.isRegistered, null, 0, d)
    }
}

@RequiresApi(29)
private fun nrRow(c: CellInfoNr): CellRow {
    val d = mutableListOf<Pair<String, String>>()
    val id = c.cellIdentity as CellIdentityNr
    val s = c.cellSignalStrength as CellSignalStrengthNr
    d.add("SS-RSRP (signal)", s.ssRsrp.ok(), " dBm")
    d.add("SS-RSRQ (quality)", s.ssRsrq.ok(), " dB")
    d.add("SS-SINR", s.ssSinr.ok(), " dB")
    d.add("Physical cell ID", id.pci.ok())
    d.add("Tracking area", id.tac.ok())
    d.add("Cell ID (NCI)", id.nci.ok())
    d.add("NR-ARFCN (frequency #)", id.nrarfcn.ok())
    if (Build.VERSION.SDK_INT >= 30 && id.bands.isNotEmpty()) d.add("Band", id.bands.joinToString { "n$it" })
    return CellRow("5G NR", c.isRegistered, s.dbm.ok(), s.level, d)
}

@Composable
fun CellularScreen() {
    PermissionGate(
        Perms.location,
        "Android only shares cell tower details with apps that have Location permission. Nothing is stored or sent.",
        need = Perms.fineLocation
    ) { CellularContent() }
}

@SuppressLint("MissingPermission")
@Composable
private fun CellularContent() {
    val ctx = LocalContext.current
    val tm = remember { ctx.getSystemService(TelephonyManager::class.java) }
    var cells by remember { mutableStateOf(emptyList<CellRow>()) }
    var updated by remember { mutableStateOf<String?>(null) }
    val history = remember { mutableStateListOf<Float>() }

    fun accept(list: List<CellInfo>?) {
        cells = list.orEmpty().map { toRow(it) }.sortedByDescending { it.registered }
        updated = timeNow()
        cells.firstOrNull { it.registered }?.dbm?.let { history.push(it.toFloat()) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                if (Build.VERSION.SDK_INT >= 29) requestFresh(ctx, tm) { accept(it) } else accept(tm?.allCellInfo)
            } catch (e: SecurityException) {
                updated = "Permission missing"
            }
            delay(5000)
        }
    }

    val serving = cells.firstOrNull { it.registered }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { LocationOffHint() }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Your mobile network", style = MaterialTheme.typography.titleMedium)
                    val op = tm?.networkOperatorName?.takeIf { it.isNotBlank() }
                    val sim = tm?.simOperatorName?.takeIf { it.isNotBlank() }
                    Text(op ?: "No mobile network", style = MaterialTheme.typography.titleLarge)
                    if (sim != null && sim != op) HintText("SIM from $sim (roaming or MVNO)")
                    if (serving != null) {
                        Row(Modifier.fillMaxWidth()) {
                            StatTile("Technology", serving.tech, Modifier.weight(1f))
                            StatTile("Signal", serving.dbm?.let { "$it dBm" } ?: "—", Modifier.weight(1f))
                            StatTile("Bars", "${serving.level} / 4", Modifier.weight(1f))
                        }
                        LineChart(listOf(history.toList()), height = 80.dp, minY = -130f, maxY = -50f)
                        HintText("Signal history, updated every 5 seconds. 5G \"non-standalone\" often shows as 4G LTE here.")
                    } else {
                        HintText("No connected cell reported. Tablets without a SIM, airplane mode, or Location off all cause this.")
                    }
                    updated?.let { HintText("Updated $it") }
                }
            }
        }
        if (cells.isNotEmpty()) item { SectionTitle("Towers the phone can hear (${cells.size})") }
        items(cells) { c -> CellCard(c) }
    }
}

@RequiresApi(29)
@SuppressLint("MissingPermission")
private fun requestFresh(ctx: android.content.Context, tm: TelephonyManager?, onResult: (List<CellInfo>) -> Unit) {
    tm ?: return
    tm.requestCellInfoUpdate(ContextCompat.getMainExecutor(ctx), object : TelephonyManager.CellInfoCallback() {
        override fun onCellInfo(cellInfo: MutableList<CellInfo>) { onResult(cellInfo) }
    })
}

@Composable
private fun CellCard(c: CellRow) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.tech, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (c.registered) Text("CONNECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                MonoText(c.dbm?.let { "$it dBm" } ?: "—")
            }
            LinearProgressIndicator(progress = { c.level / 4f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            c.details.forEach { (k, v) ->
                Row {
                    Text(k, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(v, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
