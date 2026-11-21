package com.daniel.hardwarelens

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiMathTest {
    @Test fun channels() {
        assertEquals(1, WifiMath.channel(2412))
        assertEquals(6, WifiMath.channel(2437))
        assertEquals(13, WifiMath.channel(2472))
        assertEquals(14, WifiMath.channel(2484))
        assertEquals(36, WifiMath.channel(5180))
        assertEquals(165, WifiMath.channel(5825))
        assertEquals(1, WifiMath.channel(5955))
        assertEquals(2, WifiMath.channel(5935))
        assertEquals(233, WifiMath.channel(7115))
    }

    @Test fun bands() {
        assertEquals(WifiBand.B24, WifiMath.band(2437))
        assertEquals(WifiBand.B5, WifiMath.band(5500))
        assertEquals(WifiBand.B6, WifiMath.band(6115))
    }

    @Test fun security() {
        assertEquals("WPA3", WifiMath.security("[RSN-SAE-CCMP][ESS]"))
        assertEquals("WPA2", WifiMath.security("[WPA2-PSK-CCMP][ESS]"))
        assertEquals("Enhanced Open", WifiMath.security("[RSN-OWE-CCMP][ESS]"))
        assertEquals("Open", WifiMath.security("[ESS]"))
    }

    @Test fun widths() {
        assertEquals(20, WifiMath.widthMhz(0))
        assertEquals(80, WifiMath.widthMhz(2))
        assertEquals(160, WifiMath.widthMhz(4))
    }
}
