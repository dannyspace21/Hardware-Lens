@file:OptIn(ExperimentalMaterial3Api::class)

package com.daniel.hardwarelens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

enum class Section(val label: String, val icon: ImageVector) {
    Sensors("Sensors", Icons.Filled.Sensors),
    Wireless("Wireless", Icons.Filled.Wifi),
    Devices("Devices", Icons.Filled.Router),
    Nfc("NFC", Icons.Filled.Nfc),
    Phone("Phone", Icons.Filled.PhoneAndroid),
}

enum class Page { Settings, About }

@Composable
fun HardwareLensApp() {
    val ctx = LocalContext.current
    val dark = when (Prefs.theme.value) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = when {
        Prefs.dynamicColor.value && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    val view = LocalView.current
    val keepOn = Prefs.keepScreenOn.value
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    MaterialTheme(colorScheme = colors) {
        var section by rememberSaveable { mutableStateOf(Section.Sensors) }
        var page by rememberSaveable { mutableStateOf<Page?>(null) }
        var updateDismissed by rememberSaveable { mutableStateOf(false) }
        val holder = rememberSaveableStateHolder()

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }
        val startUpdate = rememberUpdateStarter()
        LaunchedEffect(Unit) {
            val missing = requiredPermissions().filterNot { ctx.hasPerm(it) }
            if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
            OuiDb.load(ctx)
            if (BuildConfig.UPDATE_CHECK && Prefs.checkUpdates.value) {
                val found = Updates.check().getOrNull()?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
                // On Wi-Fi with install permission already granted, just get on with it.
                if (found != null && Prefs.autoUpdate.value && Updater.onUnmeteredNetwork(ctx) && Updater.canInstall(ctx)) {
                    startUpdate(found)
                }
            }
        }

        // Jump to the NFC tab whenever something is tapped against the phone.
        val scan by NfcState.lastScan
        LaunchedEffect(scan) {
            if (scan != null) { section = Section.Nfc; page = null }
        }

        val update = Updates.available.value
        if (update != null && !updateDismissed && page == null) {
            UpdateDialog(update) { updateDismissed = true }
        }

        BackHandler(enabled = page != null) { page = null }

        when (page) {
            Page.Settings -> SettingsScreen(onBack = { page = null })
            Page.About -> AboutScreen(onBack = { page = null })
            null -> Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Hardware Lens") },
                        actions = {
                            if (update != null) {
                                IconButton(onClick = { page = Page.About }) {
                                    Icon(Icons.Filled.SystemUpdate, "Update available", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = { Prefs.setTheme(if (dark) ThemeMode.Light else ThemeMode.Dark) }) {
                                Icon(
                                    if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                    if (dark) "Switch to light mode" else "Switch to dark mode"
                                )
                            }
                            IconButton(onClick = { page = Page.About }) { Icon(Icons.Filled.Info, "About") }
                            IconButton(onClick = { page = Page.Settings }) { Icon(Icons.Filled.Settings, "Settings") }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        Section.entries.forEach { s ->
                            NavigationBarItem(
                                selected = s == section,
                                onClick = { section = s },
                                icon = { Icon(s.icon, contentDescription = s.label) },
                                label = { Text(s.label) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    holder.SaveableStateProvider(section.name) {
                        when (section) {
                            Section.Sensors -> SensorsScreen()
                            Section.Wireless -> WirelessScreen()
                            Section.Devices -> NetworkScreen()
                            Section.Nfc -> NfcScreen()
                            Section.Phone -> PhoneScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WirelessScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SubTabs(listOf("Wi-Fi", "Bluetooth", "Cellular"), tab) { tab = it }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> WifiScreen()
                1 -> BluetoothScreen()
                else -> CellularScreen()
            }
        }
    }
}

@Composable
fun PhoneScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SubTabs(listOf("Battery", "GPS", "Camera"), tab) { tab = it }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> BatteryScreen()
                1 -> GpsScreen()
                else -> CameraScreen()
            }
        }
    }
}
