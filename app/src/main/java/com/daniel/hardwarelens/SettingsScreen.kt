@file:OptIn(ExperimentalMaterial3Api::class)

package com.daniel.hardwarelens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle("Appearance")
            HintText("Tip: the sun / moon button at the top of the main screen switches light and dark in one tap.")
            ThemeMode.entries.forEach { m ->
                RadioRow(m.label, Prefs.theme.value == m) { Prefs.setTheme(m) }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                SwitchRow("Colors from your wallpaper", "Material You dynamic colors", Prefs.dynamicColor.value) { Prefs.setDynamicColor(it) }
            }
            SwitchRow("Keep screen on", "Handy while watching sensors or scanning", Prefs.keepScreenOn.value) { Prefs.setKeepScreenOn(it) }

            HorizontalDivider()
            SectionTitle("Units")
            Units.entries.forEach { u -> RadioRow(u.label, Prefs.units.value == u) { Prefs.setUnits(u) } }

            HorizontalDivider()
            SectionTitle("Wi-Fi scan interval")
            HintText("Android lets each app scan about 4 times every 2 minutes, so faster isn't always possible.")
            listOf(30, 60, 120).forEach { s ->
                RadioRow("Every $s seconds", Prefs.wifiScanSeconds.intValue == s) { Prefs.setWifiScanSeconds(s) }
            }

            if (BuildConfig.UPDATE_CHECK) {
                HorizontalDivider()
                SectionTitle("Updates")
                SwitchRow("Check for updates when the app opens", "Looks at the GitHub release page (nothing about you is sent)", Prefs.checkUpdates.value) {
                    Prefs.setCheckUpdates(it)
                }
            }

            HorizontalDivider()
            SectionTitle("Data")
            HintText("NFC history is the only thing Hardware Lens saves. It stays on this phone.")
            OutlinedButton(onClick = { confirmClear = true }, enabled = NfcHistory.items.isNotEmpty()) {
                Text("Clear NFC history (${NfcHistory.items.size})")
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear NFC history?") },
            text = { Text("This deletes all ${NfcHistory.items.size} saved scans.") },
            confirmButton = { TextButton(onClick = { NfcHistory.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}

@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            HintText(hint)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
