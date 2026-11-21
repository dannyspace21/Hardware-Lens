package com.daniel.hardwarelens

import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import androidx.compose.runtime.mutableStateOf

class NfcScan(
    val timestamp: Long,
    val uid: String,
    val uidNote: String?,
    val techs: List<String>,
    val title: String,
    val details: List<Pair<String, String>>,
    val tlv: List<TlvNode>,
    val raw: ByteArray?,
    val note: String?,
)

object NfcState {
    val lastScan = mutableStateOf<NfcScan?>(null)
}

/** Plain-text summary for sharing or copying. */
fun NfcScan.toReport(): String = buildString {
    appendLine("Hardware Lens NFC scan — ${dateTime(timestamp)}")
    appendLine(title)
    appendLine("Chip ID (UID): ${uid.ifBlank { "none" }}")
    uidNote?.let { appendLine("  $it") }
    appendLine("Technologies: ${techs.joinToString(", ")}")
    details.forEach { (k, v) -> appendLine("$k: $v") }
    raw?.let { appendLine("Raw response: ${it.hex(" ")}") }
    note?.let { appendLine(); appendLine(it) }
}

object NfcReader {

    // "SELECT 2PAY.SYS.DDF01" — asks a contactless card for its public list of payment apps.
    private val SELECT_PPSE = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E) +
        "2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00)

    fun read(tag: Tag): NfcScan {
        val details = mutableListOf<Pair<String, String>>()
        val techs = tag.techList.map { it.substringAfterLast('.') }
        var title = "NFC tag"
        var tlv = emptyList<TlvNode>()
        var raw: ByteArray? = null
        var note: String? = null

        NfcA.get(tag)?.let {
            details += "NFC-A (ISO 14443-3A)" to "ATQA ${it.atqa.hex(" ")} · SAK 0x%02X".format(it.sak.toInt())
        }
        NfcB.get(tag)?.let {
            details += "NFC-B (ISO 14443-3B)" to "Application data ${it.applicationData.hex(" ")}"
        }
        NfcF.get(tag)?.let {
            title = "FeliCa card"
            details += "NFC-F (FeliCa)" to "System code ${it.systemCode.hex()} · Manufacturer ${it.manufacturer.hex()}"
        }
        NfcV.get(tag)?.let {
            title = "ISO 15693 tag"
            details += "NFC-V (ISO 15693)" to "DSF ID 0x%02X".format(it.dsfId.toInt() and 0xFF)
        }
        runCatching { MifareClassic.get(tag) }.getOrNull()?.let { m ->
            title = "MIFARE Classic"
            val type = when (m.type) {
                MifareClassic.TYPE_CLASSIC -> "Classic"
                MifareClassic.TYPE_PLUS -> "Plus"
                MifareClassic.TYPE_PRO -> "Pro"
                else -> "Unknown"
            }
            details += "MIFARE Classic" to "$type · ${m.size} bytes · ${m.sectorCount} sectors · ${m.blockCount} blocks"
        }
        MifareUltralight.get(tag)?.let { u ->
            title = "MIFARE Ultralight / NTAG"
            details += "MIFARE Ultralight" to
                if (u.type == MifareUltralight.TYPE_ULTRALIGHT_C) "Ultralight C" else "Ultralight / NTAG family"
        }
        Ndef.get(tag)?.let { ndef ->
            if (title == "NFC tag") title = "NDEF tag"
            details += "NDEF" to "${ndef.type} · ${ndef.maxSize} bytes capacity · " +
                if (ndef.isWritable) "writable" else "read-only"
            ndef.cachedNdefMessage?.records?.forEachIndexed { i, r ->
                details += "NDEF record ${i + 1}" to describeRecord(r)
            }
        }
        IsoDep.get(tag)?.let { iso ->
            details += "ISO-DEP (ISO 14443-4)" to "Smart-card chip · max message ${iso.maxTransceiveLength} bytes"
            try {
                iso.connect()
                iso.timeout = 3000
                val resp = iso.transceive(SELECT_PPSE)
                val sw = if (resp.size >= 2) resp.copyOfRange(resp.size - 2, resp.size).hex() else ""
                if (sw == "9000") {
                    val body = resp.copyOfRange(0, resp.size - 2)
                    raw = body
                    tlv = Tlv.parse(body)
                    val aids = Tlv.findAll(tlv, "4F").map { it.value.hex() }
                    val labels = Tlv.findAll(tlv, "50").map { String(it.value, Charsets.US_ASCII) }
                    val brand = aids.firstNotNullOfOrNull { brandFor(it) } ?: "Payment"
                    title = "$brand contactless card"
                    aids.forEachIndexed { i, aid ->
                        details += "Payment app ${i + 1}" to
                            "${labels.getOrNull(i) ?: brandFor(aid) ?: "Unknown"} · AID $aid"
                    }
                    note = "This is the card's public payment directory (PPSE) — the first thing every " +
                        "tap-to-pay terminal reads to choose a payment network. Hardware Lens deliberately " +
                        "stops here: it never asks the card for its number or transaction data."
                } else {
                    if (title == "NFC tag") title = "Smart card / secure chip"
                    details += "Payment directory" to "Not a payment card (card answered $sw)"
                }
            } catch (e: Exception) {
                details += "ISO-DEP read" to "Interrupted (${e.javaClass.simpleName}) — hold the card still and try again"
            } finally {
                runCatching { iso.close() }
            }
        }

        val id = tag.id ?: ByteArray(0)
        val uidNote = if (id.size == 4 && id[0] == 0x08.toByte())
            "Random ID — this card/phone makes up a new one on every tap for privacy" else null

        return NfcScan(System.currentTimeMillis(), id.hex(":"), uidNote, techs, title, details, tlv, raw, note)
    }

    private fun brandFor(aid: String): String? = when {
        aid.startsWith("A000000003") -> "Visa"
        aid.startsWith("A000000004") -> "Mastercard"
        aid.startsWith("A000000025") -> "American Express"
        aid.startsWith("A000000152") -> "Discover"
        aid.startsWith("A000000065") -> "JCB"
        aid.startsWith("A000000333") -> "UnionPay"
        aid.startsWith("A000000277") -> "Interac"
        else -> null
    }

    private fun describeRecord(r: NdefRecord): String {
        r.toUri()?.let { return "Link: $it" }
        if (r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_TEXT)) {
            val p = r.payload
            if (p.isNotEmpty()) {
                val langLen = p[0].toInt() and 0x3F
                val charset = if ((p[0].toInt() and 0x80) != 0) Charsets.UTF_16 else Charsets.UTF_8
                val len = p.size - 1 - langLen
                if (len >= 0) return "Text: " + String(p, 1 + langLen, len, charset)
            }
        }
        if (r.tnf == NdefRecord.TNF_MIME_MEDIA) return "Data (${String(r.type)}) · ${r.payload.size} bytes"
        return "Record type ${String(r.type)} · ${r.payload.size} bytes"
    }
}
