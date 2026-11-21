package com.daniel.hardwarelens

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

data class FoundDevice(
    val key: String, val name: String, val ip: String,
    val kind: String, val detail: String, val via: String
)

fun isConsole(kind: String) = kind.startsWith("PS") || "PlayStation" in kind || "Xbox" in kind

/**
 * Finds devices on the same Wi-Fi network using three "hello, who's there?" methods
 * that devices answer on their own:
 *  1. PlayStation discovery (the same probe the PS Remote Play app uses)
 *  2. SSDP / UPnP (TVs, Xbox, routers, Roku, Sonos, Hue...)
 *  3. mDNS / Bonjour (Chromecast, AirPlay, printers, HomeKit, Spotify speakers...)
 */
object NetworkDiscovery {

    private val serviceTypes = listOf(
        "_googlecast._tcp" to "Chromecast / Google TV",
        "_airplay._tcp" to "AirPlay device",
        "_raop._tcp" to "AirPlay speaker",
        "_companion-link._tcp" to "Apple device",
        "_spotify-connect._tcp" to "Spotify Connect speaker",
        "_ipp._tcp" to "Printer",
        "_printer._tcp" to "Printer",
        "_hap._tcp" to "HomeKit accessory",
        "_sonos._tcp" to "Sonos speaker",
        "_smb._tcp" to "File share / PC",
        "_http._tcp" to "Web-enabled device",
    )

    suspend fun run(ctx: Context, onFound: (FoundDevice) -> Unit) {
        val app = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        val emit: (FoundDevice) -> Unit = { d -> main.post { onFound(d) } }
        val lock = app.getSystemService(WifiManager::class.java)
            .createMulticastLock("hardwarelens").apply { setReferenceCounted(false); acquire() }
        try {
            coroutineScope {
                launch(Dispatchers.IO) { runCatching { playStation(app, emit) } }
                launch(Dispatchers.IO) { runCatching { ssdp(emit) } }
                launch { mdns(app, emit) }
            }
        } finally {
            runCatching { lock.release() }
        }
    }

    fun myIpv4(ctx: Context): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
    }

    private fun broadcastAddress(ctx: Context): InetAddress? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        val la = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val ip = ByteBuffer.wrap(la.address.address).int
        val mask = if (la.prefixLength == 0) 0 else -1 shl (32 - la.prefixLength)
        val bcast = ip or mask.inv()
        return InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(bcast).array())
    }

    private fun parseHeaders(text: String): Map<String, String> =
        text.lineSequence().mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()

    private inline fun receiveFor(sock: DatagramSocket, ms: Long, handle: (String, String) -> Unit) {
        val end = System.currentTimeMillis() + ms
        val buf = ByteArray(4096)
        while (System.currentTimeMillis() < end) {
            val p = DatagramPacket(buf, buf.size)
            try {
                sock.receive(p)
            } catch (e: SocketTimeoutException) {
                continue
            }
            handle(String(p.data, 0, p.length, Charsets.UTF_8), p.address.hostAddress ?: "?")
        }
    }

    private fun playStation(ctx: Context, emit: (FoundDevice) -> Unit) {
        val target = broadcastAddress(ctx) ?: InetAddress.getByName("255.255.255.255")
        DatagramSocket().use { sock ->
            sock.broadcast = true
            sock.soTimeout = 500
            val probes = listOf(987 to "00020020", 9302 to "00030010") // PS4, PS5
            repeat(3) {
                for ((port, version) in probes) {
                    val msg = "SRCH * HTTP/1.1\ndevice-discovery-protocol-version:$version\n".toByteArray()
                    runCatching { sock.send(DatagramPacket(msg, msg.size, target, port)) }
                }
                receiveFor(sock, 1500) { text, addr ->
                    val h = parseHeaders(text)
                    val statusLine = text.lineSequence().firstOrNull().orEmpty()
                    val state = when {
                        " 200" in statusLine -> "Powered on"
                        " 620" in statusLine -> "In rest mode"
                        else -> statusLine
                    }
                    val type = h["host-type"] ?: "PlayStation"
                    val fw = h["system-version"]?.let { " · system $it" } ?: ""
                    emit(FoundDevice("ps-$addr", h["host-name"] ?: type, addr, type, state + fw, "PlayStation discovery"))
                }
            }
        }
    }

    private fun ssdp(emit: (FoundDevice) -> Unit) {
        val group = InetAddress.getByName("239.255.255.250")
        val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n").toByteArray()
        DatagramSocket().use { sock ->
            sock.soTimeout = 500
            repeat(2) { runCatching { sock.send(DatagramPacket(msg, msg.size, group, 1900)) } }
            receiveFor(sock, 4000) { text, addr ->
                val h = parseHeaders(text)
                val server = h["server"].orEmpty()
                val st = h["st"].orEmpty()
                val kind = guessKind(server, st)
                emit(FoundDevice("ssdp-$addr", kind, addr, kind, server.ifBlank { st }, "SSDP / UPnP"))
            }
        }
    }

    private fun guessKind(server: String, st: String): String {
        val s = "$server $st".lowercase()
        return when {
            "xbox" in s -> "Xbox"
            "playstation" in s || "ps4" in s || "ps5" in s -> "PlayStation"
            "roku" in s -> "Roku"
            "sonos" in s -> "Sonos speaker"
            "internetgatewaydevice" in s || "wanipconnection" in s -> "Router"
            "ipbridge" in s || "hue" in s -> "Philips Hue bridge"
            "webos" in s -> "LG TV"
            "samsung" in s || "tizen" in s -> "Samsung TV / device"
            "mediarenderer" in s || "dial" in s -> "TV / media player"
            "printer" in s -> "Printer"
            "windows" in s -> "Windows PC"
            else -> "UPnP device"
        }
    }

    private suspend fun mdns(ctx: Context, emit: (FoundDevice) -> Unit) {
        val nsd = ctx.getSystemService(NsdManager::class.java) ?: return
        val started = mutableListOf<NsdManager.DiscoveryListener>()
        for ((type, kind) in serviceTypes) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) {
                    emit(FoundDevice("mdns-$type-${info.serviceName}", info.serviceName, "", kind, type, "mDNS / Bonjour"))
                }
                override fun onServiceLost(info: NsdServiceInfo) {}
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            if (runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }.isSuccess) {
                started += listener
            }
        }
        try {
            delay(6000)
        } finally {
            started.forEach { l -> runCatching { nsd.stopServiceDiscovery(l) } }
        }
    }
}
