package com.daniel.hardwarelens

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The last 50 NFC scans, saved to a small file inside the app's private storage. */
object NfcHistory {
    private const val MAX = 50
    val items = mutableStateListOf<NfcScan>()
    private var file: File? = null

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "nfc_history.json")
        file = f
        runCatching {
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) runCatching { items += fromJson(arr.getJSONObject(i)) }
            }
        }
    }

    fun add(scan: NfcScan) {
        items.add(0, scan)
        while (items.size > MAX) items.removeAt(items.lastIndex)
        save()
    }

    fun clear() {
        items.clear()
        save()
    }

    private fun save() {
        val f = file ?: return
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        runCatching { f.writeText(arr.toString()) }
    }

    private fun toJson(s: NfcScan) = JSONObject().apply {
        put("timestamp", s.timestamp)
        put("uid", s.uid)
        put("uidNote", s.uidNote ?: JSONObject.NULL)
        put("techs", JSONArray(s.techs))
        put("title", s.title)
        put("details", JSONArray().apply { s.details.forEach { (k, v) -> put(JSONArray(listOf(k, v))) } })
        put("raw", s.raw?.hex() ?: JSONObject.NULL)
        put("note", s.note ?: JSONObject.NULL)
    }

    private fun fromJson(o: JSONObject): NfcScan {
        val techs = o.getJSONArray("techs").let { a -> List(a.length()) { a.getString(it) } }
        val details = o.getJSONArray("details").let { a ->
            List(a.length()) { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } }
        }
        val raw = o.optString("raw").takeIf { !o.isNull("raw") && it.isNotBlank() }?.hexToBytes()
        return NfcScan(
            timestamp = o.getLong("timestamp"),
            uid = o.getString("uid"),
            uidNote = if (o.isNull("uidNote")) null else o.getString("uidNote"),
            techs = techs,
            title = o.getString("title"),
            details = details,
            tlv = raw?.let { Tlv.parse(it) } ?: emptyList(),
            raw = raw,
            note = if (o.isNull("note")) null else o.getString("note"),
        )
    }
}
