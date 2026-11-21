package com.daniel.hardwarelens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModelTest {
    private val ip = Subnet.parse("192.168.1.23")!!

    @Test fun subnetMath() {
        assertEquals("192.168.1.23", Subnet.toStr(ip))
        assertEquals("192.168.1.255", Subnet.toStr(Subnet.broadcast(ip, 24)))
        assertNull(Subnet.parse("300.1.1.1"))
        assertNull(Subnet.parse("1.2.3"))
    }

    @Test fun sweepCoversTheSubnetExceptUs() {
        val hosts = Subnet.sweepHosts(ip, 24).map { Subnet.toStr(it) }
        assertEquals(253, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
        assertTrue("192.168.1.23" !in hosts)
    }

    @Test fun hugeNetworksAreNarrowedTo24() {
        assertEquals(253, Subnet.sweepHosts(Subnet.parse("10.20.30.40")!!, 16).size)
        assertEquals(1021, Subnet.sweepHosts(Subnet.parse("10.20.30.40")!!, 22).size)
        assertEquals(0, Subnet.sweepHosts(ip, 31).size)
    }

    @Test fun mergeKeepsTheBestInformation() {
        val sweep = FoundDevice("192.168.1.50", "192.168.1.50", "Network device", sources = setOf("Network sweep"), openPort = 80)
        val ssdp = FoundDevice("192.168.1.50", "Living Room TV", "Samsung TV / device", listOf("Samsung QN60"), setOf("SSDP / UPnP"))
        val m = Devices.merge(sweep, ssdp)
        assertEquals("Living Room TV", m.name)
        assertEquals("Samsung TV / device", m.kind)
        assertEquals(80, m.openPort)
        assertEquals(setOf("Network sweep", "SSDP / UPnP"), m.sources)
        // order of arrival doesn't matter
        assertEquals(m.name, Devices.merge(ssdp, sweep).name)
        assertEquals(m.kind, Devices.merge(ssdp, sweep).kind)
    }

    @Test fun consolesWinOverGenericKinds() {
        val a = FoundDevice("192.168.1.9", "PS5-123", "PlayStation 5", sources = setOf("PlayStation discovery"))
        val b = FoundDevice("192.168.1.9", "", "UPnP device", sources = setOf("SSDP / UPnP"))
        assertEquals("PlayStation 5", Devices.merge(b, a).kind)
        assertEquals("PS5-123", Devices.merge(b, a).name)
    }

    @Test fun guesses() {
        assertEquals("iPhone", Devices.guessFromHostname("Dannys-iPhone"))
        assertEquals("Windows PC", Devices.guessFromHostname("DESKTOP-4F2K9"))
        assertEquals("Android phone", Devices.guessFromHostname("Galaxy-S24"))
        assertNull(Devices.guessFromHostname("unknown-thing"))
        assertEquals("iPhone / iPad", Devices.guessFromPort(62078))
        assertEquals("Xbox", Devices.guessSsdpKind("Microsoft-Windows/10.0 UPnP/1.0 Xbox", ""))
        assertEquals("Router", Devices.guessSsdpKind("", "urn:schemas-upnp-org:device:InternetGatewayDevice:1"))
    }

    @Test fun headersAndXml() {
        val h = Devices.parseHeaders("HTTP/1.1 200 OK\r\nhost-name:PS5-ABC\r\nhost-type:PS5\r\n")
        assertEquals("PS5-ABC", h["host-name"])
        val xml = "<root><device><friendlyName>Den &amp; Office</friendlyName><modelName> X1 </modelName></device></root>"
        assertEquals("Den & Office", Devices.xmlTag(xml, "friendlyName"))
        assertEquals("X1", Devices.xmlTag(xml, "modelName"))
        assertNull(Devices.xmlTag(xml, "manufacturer"))
    }

    @Test fun ports() {
        assertEquals("AirPlay", Ports.name(7000))
        assertEquals("https://10.0.0.5", Ports.webUrl("10.0.0.5", listOf(80, 443)))
        assertNull(Ports.webUrl("10.0.0.5", listOf(22)))
    }
}
