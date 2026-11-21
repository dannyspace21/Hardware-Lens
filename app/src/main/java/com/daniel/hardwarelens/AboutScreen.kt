@file:OptIn(ExperimentalMaterial3Api::class)

package com.daniel.hardwarelens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val update = Updates.available.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp).background(Color(0xFF0E3A44), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                        tint = Color.Unspecified, modifier = Modifier.size(72.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Hardware Lens", style = MaterialTheme.typography.headlineSmall)
                    Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                    HintText(if (BuildConfig.UPDATE_CHECK) "GitHub build" else "Play Store build")
                }
            }

            if (BuildConfig.UPDATE_CHECK) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Updates", style = MaterialTheme.typography.titleMedium)
                        if (update != null) {
                            Text("Version ${update.versionName} is available.")
                            Button(onClick = { Updates.download(ctx, update) }) { Text("Download update") }
                            HintText("Open the downloaded file and tap Update. No need to uninstall — your settings and NFC history are kept.")
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    enabled = !checking,
                                    onClick = {
                                        checking = true
                                        result = null
                                        scope.launch {
                                            val r = Updates.check()
                                            checking = false
                                            result = r.fold(
                                                onSuccess = { if (it.versionCode > BuildConfig.VERSION_CODE) null else "You have the latest version." },
                                                onFailure = { "Couldn't reach GitHub (${it.message ?: it.javaClass.simpleName})." }
                                            )
                                        }
                                    }
                                ) { Text(if (checking) "Checking…" else "Check for updates") }
                                if (checking) {
                                    Spacer(Modifier.width(12.dp))
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                            result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your privacy", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Everything Hardware Lens shows is read and displayed on this phone. It has no account, no ads, " +
                            "no analytics and no servers. The only internet request it makes on its own is the update check " +
                            "to GitHub, which you can turn off in Settings."
                    )
                    TextButton(onClick = { ctx.openUrl("${Updates.repoUrl}/blob/HEAD/PRIVACY.md") }) { Text("Read the privacy policy") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("What Android doesn't allow", style = MaterialTheme.typography.titleMedium)
                    Text("• Hardware (MAC) addresses of other devices on your Wi-Fi are hidden from apps since Android 10.")
                    Text("• Apps get about 4 Wi-Fi scans every 2 minutes.")
                    Text("• Most phones, watches and earbuds use random Bluetooth addresses that change over time.")
                    Text("• Guest, school and office Wi-Fi often stop devices from seeing each other.")
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Open source", style = MaterialTheme.typography.titleMedium)
                    Text("Hardware Lens is MIT licensed. Built with Kotlin, Jetpack Compose, Material 3 and AndroidX (Apache 2.0).")
                    Row {
                        TextButton(onClick = { ctx.openUrl(Updates.repoUrl) }) { Text("Source code") }
                        TextButton(onClick = { ctx.openUrl(Updates.releasesUrl) }) { Text("All releases") }
                    }
                }
            }
        }
    }
}
