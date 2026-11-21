package com.daniel.hardwarelens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String = "")

/**
 * Checks GitHub for a newer build. Every CI build publishes version.json next to the APK
 * on the "latest" release, so this is a single small download.
 */
object Updates {
    private val repo = BuildConfig.GITHUB_REPO
    val repoUrl = "https://github.com/$repo"
    val releasesUrl = "$repoUrl/releases"
    val apkUrl = "$repoUrl/releases/download/latest/HardwareLens.apk"
    private val versionUrl = "$repoUrl/releases/download/latest/version.json"

    /** Set when a newer version than the running one has been found. */
    val available = mutableStateOf<UpdateInfo?>(null)

    suspend fun check(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(versionUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode != 200) error("GitHub answered ${conn.responseCode}")
                val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                UpdateInfo(
                    versionCode = j.getInt("versionCode"),
                    versionName = j.optString("versionName", "?"),
                    apkUrl = j.optString("apkUrl", apkUrl),
                    notes = j.optString("notes", ""),
                )
            } finally {
                conn.disconnect()
            }
        }.onSuccess { if (it.versionCode > BuildConfig.VERSION_CODE) available.value = it }
    }

    fun download(ctx: Context, info: UpdateInfo?) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info?.apkUrl ?: apkUrl)))
    }
}
