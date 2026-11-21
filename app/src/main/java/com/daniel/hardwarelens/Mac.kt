package com.daniel.hardwarelens

/** Hardware (MAC) address helpers. Plain Kotlin, so they're unit-tested. */
object Mac {
    private val macRegex = Regex("""([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}""")

    /** "aa-bb-cc-dd-ee-ff" / "AABBCCDDEEFF" -> "AA:BB:CC:DD:EE:FF"; null for junk or placeholder values. */
    fun normalize(s: String?): String? {
        if (s == null) return null
        val hex = s.filter { it.isLetterOrDigit() }.uppercase()
        if (hex.length != 12 || hex.any { it !in "0123456789ABCDEF" }) return null
        if (hex == "000000000000" || hex == "FFFFFFFFFFFF" || hex == "020000000000") return null
        return hex.chunked(2).joinToString(":")
    }

    /** First three bytes as an Int, the part that identifies the manufacturer. */
    fun oui(mac: String): Int? = normalize(mac)?.substring(0, 8)?.replace(":", "")?.toInt(16)

    /**
     * True for "locally administered" addresses: the ones phones, laptops and smart devices
     * make up for privacy instead of using the maker's permanent one.
     */
    fun isRandomized(mac: String): Boolean =
        normalize(mac)?.let { (it.substring(0, 2).toInt(16) and 0x02) != 0 } ?: false

    /** Finds a MAC written anywhere in a piece of text, e.g. "laptop [aa:bb:cc:dd:ee:ff]". */
    fun findIn(text: String?): String? = text?.let { macRegex.find(it)?.value }?.let { normalize(it) }

    /** AirPlay speakers advertise as "AABBCCDDEEFF@Living Room". */
    fun fromRaopName(name: String): String? =
        name.substringBefore('@', "").takeIf { it.length == 12 }?.let { normalize(it) }

    /** Reads the bundled vendor list: one "AABBCC<TAB>Vendor name" per line. */
    fun parseOuiLines(lines: Sequence<String>): Map<Int, String> {
        val out = HashMap<Int, String>(40_000)
        for (l in lines) {
            if (l.length < 8 || l[6] != '\t') continue
            val key = l.substring(0, 6).toIntOrNull(16) ?: continue
            out[key] = l.substring(7).trim()
        }
        return out
    }
}

/** NetBIOS "node status" — Windows PCs, NAS boxes and Samba servers answer it with their name and MAC. */
object NetBios {
    fun nodeStatusQuery(txid: Int): ByteArray {
        val out = ArrayList<Byte>(50)
        fun b(v: Int) = out.add(v.toByte())
        b(txid shr 8); b(txid)          // transaction id
        b(0); b(0)                      // flags: query
        b(0); b(1)                      // 1 question
        repeat(6) { b(0) }              // no answer/authority/additional records
        b(0x20)                         // encoded name length
        val name = ByteArray(16).also { it[0] = '*'.code.toByte() }
        for (c in name) { b('A'.code + ((c.toInt() shr 4) and 0xF)); b('A'.code + (c.toInt() and 0xF)) }
        b(0)                            // end of name
        b(0); b(0x21)                   // type NBSTAT
        b(0); b(1)                      // class IN
        return out.toByteArray()
    }

    /** Returns (computer name, MAC) from a node-status answer, or null if it isn't one. */
    fun parseNodeStatus(p: ByteArray): Pair<String?, String?>? {
        if (p.size < 12) return null
        var i = 12
        // skip the echoed name (labels or a compression pointer)
        while (i < p.size) {
            val len = p[i].toInt() and 0xFF
            if (len == 0) { i++; break }
            if (len and 0xC0 == 0xC0) { i += 2; break }
            i += len + 1
        }
        i += 2 + 2 + 4 + 2             // type, class, TTL, data length
        if (i >= p.size) return null
        val count = p[i].toInt() and 0xFF
        i++
        var computer: String? = null
        repeat(count) {
            if (i + 18 > p.size) return null
            val raw = String(p, i, 15, Charsets.US_ASCII).trim()
            val suffix = p[i + 15].toInt() and 0xFF
            val group = (p[i + 16].toInt() and 0x80) != 0
            if (computer == null && suffix == 0x00 && !group && raw.isNotBlank()) computer = raw
            i += 18
        }
        val mac = if (i + 6 <= p.size) Mac.normalize(p.copyOfRange(i, i + 6).hex()) else null
        return computer to mac
    }
}
