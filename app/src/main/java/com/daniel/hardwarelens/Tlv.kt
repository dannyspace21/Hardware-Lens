package com.daniel.hardwarelens

class TlvNode(val tag: String, val name: String, val value: ByteArray, val children: List<TlvNode>)

/** Decoder for BER-TLV, the tag/length/value format smart cards speak. */
object Tlv {
    private val names = mapOf(
        "6F" to "File Control Information",
        "84" to "Directory name",
        "A5" to "Proprietary template",
        "BF0C" to "Issuer data",
        "61" to "Application entry",
        "4F" to "Application ID (AID)",
        "50" to "Application label",
        "87" to "Priority",
        "9F12" to "Preferred name",
        "5F2D" to "Language preference",
        "9F11" to "Character set",
        "9F2A" to "Kernel ID",
        "9F0A" to "Proprietary data",
        "73" to "Directory template",
    )

    fun parse(data: ByteArray): List<TlvNode> {
        val out = mutableListOf<TlvNode>()
        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            if (b == 0x00 || b == 0xFF) { i++; continue } // padding
            val tagStart = i
            i++
            if ((b and 0x1F) == 0x1F) {
                while (i < data.size && (data[i].toInt() and 0x80) != 0) i++
                i++
            }
            if (i >= data.size) break
            val tagHex = data.copyOfRange(tagStart, i).hex()
            var len = data[i].toInt() and 0xFF
            i++
            when {
                len == 0x81 -> { if (i >= data.size) break; len = data[i].toInt() and 0xFF; i++ }
                len == 0x82 -> {
                    if (i + 1 >= data.size) break
                    len = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF); i += 2
                }
                len > 0x82 -> break
            }
            if (i + len > data.size) break
            val value = data.copyOfRange(i, i + len)
            i += len
            val constructed = (b and 0x20) != 0
            out += TlvNode(tagHex, names[tagHex] ?: "", value, if (constructed) parse(value) else emptyList())
        }
        return out
    }

    fun findAll(nodes: List<TlvNode>, tag: String): List<TlvNode> =
        nodes.flatMap { (if (it.tag == tag) listOf(it) else emptyList()) + findAll(it.children, tag) }

    fun flatten(nodes: List<TlvNode>, depth: Int = 0): List<Pair<Int, TlvNode>> =
        nodes.flatMap { listOf(depth to it) + flatten(it.children, depth + 1) }
}
