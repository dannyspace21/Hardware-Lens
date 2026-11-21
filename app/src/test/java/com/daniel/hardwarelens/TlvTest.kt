package com.daniel.hardwarelens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TlvTest {
    // Typical PPSE answer: FCI template -> DF name + proprietary template -> issuer data -> one Visa app entry.
    private val ppse = ("6F 2C 84 0E 32 50 41 59 2E 53 59 53 2E 44 44 46 30 31 A5 1A BF 0C 17 61 15 " +
        "4F 07 A0 00 00 00 03 10 10 50 04 56 49 53 41 87 01 01 9F 0A 00").hexToBytes()

    @Test fun parsesNestedTemplates() {
        val nodes = Tlv.parse(ppse)
        assertEquals(1, nodes.size)
        assertEquals("6F", nodes[0].tag)
        val aid = Tlv.findAll(nodes, "4F").single()
        assertEquals("A0000000031010", aid.value.hex())
        assertEquals("VISA", String(Tlv.findAll(nodes, "50").single().value, Charsets.US_ASCII))
    }

    @Test fun twoByteTagsAreRecognised() {
        val tags = Tlv.flatten(Tlv.parse(ppse)).map { it.second.tag }
        assertTrue("BF0C" in tags)
        assertTrue("9F0A" in tags)
    }

    @Test fun truncatedInputDoesNotCrash() {
        Tlv.parse(ppse.copyOf(10))
        Tlv.parse(byteArrayOf(0x6F))
        Tlv.parse(ByteArray(0))
    }

    @Test fun hexRoundTrip() {
        val b = byteArrayOf(0, 1, 0x7F, -1)
        assertEquals("00 01 7F FF", b.hex(" "))
        assertEquals(b.toList(), b.hex().hexToBytes().toList())
    }
}
