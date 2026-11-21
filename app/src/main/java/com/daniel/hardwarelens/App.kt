package com.daniel.hardwarelens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

enum class Tab(val label: String, val icon: ImageVector) {
    Sensors("Sensors", Icons.Filled.Sensors),
    Wifi("Wi-Fi", Icons.Filled.Wifi),
    Bluetooth("Bluetooth", Icons.Filled.Bluetooth),
    Network("Devices", Icons.Filled.Router),
    Nfc("NFC", Icons.Filled.Nfc),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareLensApp() {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(colorScheme = colors) {
        var tab by rememberSaveable { mutableStateOf(Tab.Sensors) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }
        LaunchedEffect(Unit) { permissionLauncher.launch(requiredPermissions()) }

        // Jump to the NFC tab whenever something is tapped against the phone.
        val scan by NfcState.lastScan
        LaunchedEffect(scan) { if (scan != null) tab = Tab.Nfc }

        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text("Hardware Lens · ${tab.label}") }) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = t == tab,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    Tab.Sensors -> SensorsScreen()
                    Tab.Wifi -> WifiScreen()
                    Tab.Bluetooth -> BluetoothScreen()
                    Tab.Network -> NetworkScreen()
                    Tab.Nfc -> NfcScreen()
                }
            }
        }
    }
}
