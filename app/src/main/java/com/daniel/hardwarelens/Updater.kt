package com.daniel.hardwarelens

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a new APK from GitHub and installs it with Android's PackageInstaller, all inside the app.
 * Android asks once for "install unknown apps" permission, and shows its own confirmation for the first
 * update. From then on (Android 12+) the app is the installer of record and updates can go through silently.
 */
object Updater {
    sealed interface State {
        data object Idle : State
        data object NeedsPermission : State
        data class Downloading(val progress: Float) : State
        data object Installing : State
        data class Failed(val message: String) : State
    }

    val state = mutableStateOf<State>(State.Idle)

    val busy: Boolean get() = state.value is State.Downloading || state.value is State.Installing

    /** App-wide scope, so closing the update dialog doesn't cancel a download. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start(ctx: Context, info: UpdateInfo) {
        val app = ctx.applicationContext
        scope.launch { downloadAndInstall(app, info) }
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun onUnmeteredNetwork(ctx: Context): Boolean =
        ctx.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == false

    /** Opens the "Install unknown apps" switch for this app. */
    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        )
    }

    suspend fun downloadAndInstall(ctx: Context, info: UpdateInfo) {
        if (busy) return
        val app = ctx.applicationContext
        if (!canInstall(app)) {
            state.value = State.NeedsPermission
            return
        }
        state.value = State.Downloading(0f)
        try {
            val file = withContext(Dispatchers.IO) {
                download(info.apkUrl, File(app.cacheDir, "update/HardwareLens.apk")) { p ->
                    state.value = State.Downloading(p)
                }
            }
            val archive = app.packageManager.getPackageArchiveInfo(file.path, 0)
                ?: error("The downloaded file isn't a valid app")
            if (archive.packageName != app.packageName) error("The download is a different app (${archive.packageName})")
            if (PackageInfoCompat.getLongVersionCode(archive) <= BuildConfig.VERSION_CODE) error("The download isn't newer than this version")
            state.value = State.Installing
            withContext(Dispatchers.IO) { install(app, file) }
        } catch (e: Exception) {
            state.value = State.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun download(url: String, dest: File, onProgress: (Float) -> Unit): File {
        dest.parentFile?.mkdirs()
        dest.delete()
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode != 200) error("GitHub answered ${conn.responseCode}")
            val total = conn.contentLengthLong
            var done = 0L
            var lastReport = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastReport > 256 * 1024) {
                            lastReport = done
                            onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
            onProgress(1f)
            return dest
        } finally {
            conn.disconnect()
        }
    }

    private fun install(ctx: Context, file: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(ctx, UpdateInstallReceiver::class.java).setAction("com.daniel.hardwarelens.INSTALL_RESULT")
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    // ---------- "Updated" notification ----------

    private const val CHANNEL = "updates"

    fun notifyUpdated(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 && !ctx.hasPerm(Manifest.permission.POST_NOTIFICATIONS)) return
        val nm = NotificationManagerCompat.from(ctx)
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Hardware Lens updated")
            .setContentText("Now on version ${BuildConfig.VERSION_NAME}. Tap to open.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(1, n) }
    }
}

/** Gets the outcome of an install session from Android. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android wants the user to confirm (first update, or older Android): show its dialog.
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(confirm) }
                        .onFailure { Updater.state.value = Updater.State.Failed("Couldn't open the install screen") }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.state.value = Updater.State.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.state.value = Updater.State.Failed("Update cancelled")
            else -> Updater.state.value = Updater.State.Failed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Android couldn't install the update"
            )
        }
    }
}

/** Runs right after this app has been replaced by a newer version. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Updater.notifyUpdated(ctx)
    }
}
