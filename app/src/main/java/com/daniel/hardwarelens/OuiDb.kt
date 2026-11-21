package com.daniel.hardwarelens

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Looks up who made a network card from the first half of its MAC address (bundled IEEE list). */
object OuiDb {
    @Volatile private var table: Map<Int, String> = emptyMap()

    /** Flips to true once the list is loaded, so screens recompose and show vendor names. */
    val ready = mutableStateOf(false)

    suspend fun load(ctx: Context) {
        if (ready.value) return
        val t = withContext(Dispatchers.IO) {
            runCatching {
                ctx.applicationContext.assets.open("oui.txt").bufferedReader().useLines { Mac.parseOuiLines(it) }
            }.getOrDefault(emptyMap())
        }
        table = t
        ready.value = true
    }

    fun vendor(mac: String?): String? {
        val m = Mac.normalize(mac) ?: return null
        if (Mac.isRandomized(m)) return null
        return Mac.oui(m)?.let { table[it] }
    }

    /** "Apple, Inc." / "private (randomized) address" / null when unknown. */
    fun describe(mac: String?): String? {
        val m = Mac.normalize(mac) ?: return null
        return if (Mac.isRandomized(m)) "private (randomized) address" else vendor(m)
    }
}
