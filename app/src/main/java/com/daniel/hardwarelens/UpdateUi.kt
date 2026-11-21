package com.daniel.hardwarelens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Starts an in-app update, asking for notification permission first so we can say "updated". */
@Composable
fun rememberUpdateStarter(): (UpdateInfo) -> Unit {
    val ctx = LocalContext.current
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(ctx) {
        { info ->
            if (Build.VERSION.SDK_INT >= 33 && !ctx.hasPerm(Manifest.permission.POST_NOTIFICATIONS)) {
                notif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Updater.start(ctx, info)
        }
    }
}

@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val start = rememberUpdateStarter()
    val st = Updater.state.value

    // Came back from the "Install unknown apps" screen with the switch on → carry on automatically.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, info) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && Updater.state.value is Updater.State.NeedsPermission && Updater.canInstall(ctx)) {
                Updater.state.value = Updater.State.Idle
                start(info)
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (st) {
                    is Updater.State.Downloading -> "Downloading update…"
                    Updater.State.Installing -> "Installing update…"
                    Updater.State.NeedsPermission -> "One-time permission"
                    is Updater.State.Failed -> "Update didn't finish"
                    Updater.State.Idle -> "Update available"
                }
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (st) {
                    Updater.State.Idle -> {
                        Text("Version ${info.versionName} is on GitHub (you have ${BuildConfig.VERSION_NAME}).")
                        if (info.notes.isNotBlank()) {
                            Text("What's new", style = MaterialTheme.typography.titleSmall)
                            Text(info.notes, style = MaterialTheme.typography.bodySmall)
                        }
                        HintText("Downloads and installs right here. Your settings and NFC history are kept.")
                    }
                    Updater.State.NeedsPermission -> {
                        Text(
                            "Android needs you to let Hardware Lens install updates. Tap Allow, switch on " +
                                "\"Allow from this source\", then come back — the update continues by itself."
                        )
                        HintText("You only do this once.")
                    }
                    is Updater.State.Downloading -> {
                        LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(st.progress * 100).toInt()}% of version ${info.versionName}")
                        HintText("You can close this — it keeps going.")
                    }
                    Updater.State.Installing -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Android may ask you to confirm with \"Update\". The app will close and update.")
                    }
                    is Updater.State.Failed -> {
                        Text(st.message)
                        HintText("You can try again, or download it in your browser instead.")
                    }
                }
            }
        },
        confirmButton = {
            when (st) {
                Updater.State.Idle -> TextButton(onClick = { start(info) }) { Text("Update now") }
                Updater.State.NeedsPermission -> TextButton(onClick = { Updater.openInstallPermission(ctx) }) { Text("Allow") }
                is Updater.State.Failed -> TextButton(onClick = { Updater.state.value = Updater.State.Idle; start(info) }) { Text("Try again") }
                else -> TextButton(onClick = onDismiss) { Text("Hide") }
            }
        },
        dismissButton = {
            when (st) {
                is Updater.State.Failed -> TextButton(onClick = { Updates.download(ctx, info); onDismiss() }) { Text("Use browser") }
                Updater.State.Idle, Updater.State.NeedsPermission -> TextButton(onClick = onDismiss) { Text("Later") }
                else -> {}
            }
        },
    )
}
