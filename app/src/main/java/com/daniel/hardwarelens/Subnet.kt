package com.daniel.hardwarelens

import java.nio.ByteBuffer

/** IPv4 helpers. Addresses are kept as Ints, the way they come out of the network stack. */
object Subnet {
    fun toInt(b: ByteArray): Int = ByteBuffer.wrap(b).int
    fun toBytes(i: Int): ByteArray = ByteBuffer.allocate(4).putInt(i).array()
    fun toStr(i: Int) = "${(i ushr 24) and 255}.${(i ushr 16) and 255}.${(i ushr 8) and 255}.${i and 255}"
    fun parse(s: String): Int? {
        val p = s.split('.')
        if (p.size != 4) return null
        val n = p.map { it.toIntOrNull() ?: return null }
        if (n.any { it !in 0..255 }) return null
        return (n[0] shl 24) or (n[1] shl 16) or (n[2] shl 8) or n[3]
    }

    fun mask(prefix: Int): Int = if (prefix <= 0) 0 else -1 shl (32 - prefix)
    fun broadcast(ip: Int, prefix: Int): Int = ip or mask(prefix).inv()

    /**
     * Addresses worth knocking on. Big networks (bigger than /22, i.e. over 1022 hosts) are
     * narrowed to the /24 around our own address so a sweep stays under a minute.
     */
    fun sweepHosts(ip: Int, prefix: Int): List<Int> {
        val p = if (prefix < 22) 24 else prefix
        if (p >= 31) return emptyList()
        val m = mask(p)
        val net = ip and m
        val bc = net or m.inv()
        return ((net + 1) until bc).filter { it != ip }
    }
}

object Ports {
    val common: List<Pair<Int, String>> = listOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 53 to "DNS", 80 to "Web (HTTP)", 139 to "Windows sharing (NetBIOS)",
        443 to "Web (HTTPS)", 445 to "File sharing (SMB)", 548 to "Apple file sharing (AFP)", 554 to "Video stream (RTSP)",
        631 to "Printer (IPP)", 1883 to "Smart home (MQTT)", 3389 to "Remote Desktop", 5000 to "UPnP / NAS",
        5357 to "Windows device discovery", 7000 to "AirPlay", 8008 to "Chromecast", 8009 to "Chromecast",
        8080 to "Web (alt)", 8443 to "Web (HTTPS alt)", 9100 to "Printer (raw)", 9295 to "PS Remote Play",
        32400 to "Plex", 62078 to "iPhone / iPad sync",
    )

    /** Ports tried during the network sweep, most telling first. */
    val sweep = listOf(80, 443, 62078, 8009, 445, 22, 7000, 9295)

    fun name(port: Int): String = common.firstOrNull { it.first == port }?.second ?: "port $port"

    fun webUrl(ip: String, open: Collection<Int>): String? = when {
        443 in open -> "https://$ip"
        80 in open -> "http://$ip"
        8080 in open -> "http://$ip:8080"
        8443 in open -> "https://$ip:8443"
        else -> null
    }
}
