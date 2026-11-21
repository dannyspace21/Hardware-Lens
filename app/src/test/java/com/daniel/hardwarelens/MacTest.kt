package com.daniel.hardwarelens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacTest {
    @Test fun normalizes() {
        assertEquals("AA:BB:CC:DD:EE:FF", Mac.normalize("aa-bb-cc-dd-ee-ff"))
        assertEquals("AA:BB:CC:DD:EE:FF", Mac.normalize("AABBCCDDEEFF"))
        assertNull(Mac.normalize("02:00:00:00:00:00"))   // Android's "hidden" placeholder
        assertNull(Mac.normalize("00:00:00:00:00:00"))
        assertNull(Mac.normalize("not a mac"))
        assertNull(Mac.normalize(null))
    }

    @Test fun randomizedBit() {
        assertTrue(Mac.isRandomized("DA:A1:19:00:00:01"))   // 0xDA has the 0x02 bit
        assertFalse(Mac.isRandomized("3C:22:FB:00:00:01"))  // a real maker prefix
    }

    @Test fun ouiPrefix() {
        assertEquals(0x3C22FB, Mac.oui("3c:22:fb:12:34:56"))
        val table = Mac.parseOuiLines(sequenceOf("3C22FB\tApple, Inc.", "garbage", "00000C\tCisco Systems, Inc"))
        assertEquals("Apple, Inc.", table[0x3C22FB])
        assertEquals(2, table.size)
    }

    @Test fun macsInAnnouncements() {
        assertEquals("58:55:CA:12:34:56", Mac.fromRaopName("5855CA123456@Kitchen"))
        assertNull(Mac.fromRaopName("Kitchen"))
        assertEquals("B8:27:EB:01:02:03", Mac.findIn("raspberrypi [b8:27:eb:01:02:03]"))
    }

    @Test fun netbiosQueryShape() {
        val q = NetBios.nodeStatusQuery(0x1234)
        assertEquals(50, q.size)
        assertEquals(0x12, q[0].toInt())
        assertEquals(0x20, q[12].toInt())
        assertEquals('C'.code, q[13].toInt())   // '*' = 0x2A -> "CK"
        assertEquals('K'.code, q[14].toInt())
        assertEquals(0x21, q[48].toInt())        // NBSTAT
    }

    @Test fun netbiosAnswerParses() {
        val header = byteArrayOf(0x12, 0x34, 0x84.toByte(), 0, 0, 0, 0, 1, 0, 0, 0, 0)
        val name = NetBios.nodeStatusQuery(0).copyOfRange(12, 46)          // echoed name, 34 bytes
        val rr = byteArrayOf(0, 0x21, 0, 1, 0, 0, 0, 0, 0, 65)             // type, class, ttl, rdlength
        fun entry(n: String, suffix: Int, group: Boolean) =
            n.padEnd(15).toByteArray(Charsets.US_ASCII) + byteArrayOf(suffix.toByte(), if (group) 0x84.toByte() else 0x04, 0)
        val names = entry("WORKGROUP", 0, true) + entry("DANNY-PC", 0, false) + entry("DANNY-PC", 0x20, false)
        val mac = byteArrayOf(0x3C, 0x22, 0xFB.toByte(), 0x12, 0x34, 0x56)
        val packet = header + name + rr + byteArrayOf(3) + names + mac + ByteArray(10)
        val (computer, m) = NetBios.parseNodeStatus(packet)!!
        assertEquals("DANNY-PC", computer)
        assertEquals("3C:22:FB:12:34:56", m)
    }

    @Test fun netbiosJunkIsSafe() {
        assertNull(NetBios.parseNodeStatus(ByteArray(5)))
        NetBios.parseNodeStatus(ByteArray(60) { 0x41 })
    }
}
