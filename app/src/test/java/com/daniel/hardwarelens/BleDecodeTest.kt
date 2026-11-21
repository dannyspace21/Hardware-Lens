package com.daniel.hardwarelens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class BleDecodeTest {
    @Test fun shortUuid() {
        assertEquals(0x180D, BleDecode.short16(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")))
        assertNull(BleDecode.short16(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")))
    }

    @Test fun appleTypes() {
        assertEquals("iBeacon", BleDecode.classify(BleAdvert(null, mapOf(0x004C to byteArrayOf(0x02, 0x15, 1, 2))))?.label)
        assertEquals("AirTag / Find My device", BleDecode.classify(BleAdvert(null, mapOf(0x004C to byteArrayOf(0x12, 0x19))))?.label)
        assertEquals("AirPods / Beats", BleDecode.classify(BleAdvert(null, mapOf(0x004C to byteArrayOf(0x07, 0x19))))?.label)
        assertEquals("Apple device", BleDecode.classify(BleAdvert(null, mapOf(0x004C to byteArrayOf(0x55))))?.label)
    }

    @Test fun services() {
        assertEquals("Tile tracker", BleDecode.classify(BleAdvert(null, serviceUuids16 = listOf(0xFEED)))?.label)
        assertEquals("Samsung SmartTag", BleDecode.classify(BleAdvert(null, serviceData16 = mapOf(0xFD5A to byteArrayOf(1))))?.label)
        assertEquals("Heart-rate monitor", BleDecode.classify(BleAdvert(null, serviceUuids16 = listOf(0x180D)))?.label)
    }

    @Test fun fallbacks() {
        assertEquals("Headphones / earbuds", BleDecode.classify(BleAdvert("Galaxy Buds2"))?.label)
        assertEquals("Garmin device", BleDecode.classify(BleAdvert(null, mapOf(0x0087 to byteArrayOf(1))))?.label)
        assertEquals("Maker ID 0xBEEF device", BleDecode.classify(BleAdvert(null, mapOf(0xBEEF to byteArrayOf(1))))?.label)
        assertNull(BleDecode.classify(BleAdvert(null)))
    }
}
