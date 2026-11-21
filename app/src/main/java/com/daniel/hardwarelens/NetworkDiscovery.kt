package com.daniel.hardwarelens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** The Wi-Fi (or Ethernet) network we scan, even if mobile data is the phone's "default" network. */
class LanLink(val network: Network?, val ip: Int, val prefix: Int, val gateway: String?, val transport: String) {
    val ipStr get() = Subnet.toStr(ip)
}

enum class Probe { Open, Refused, Silent }

/**
 * Finds devices on the same Wi-Fi network using four methods:
 *  1. PlayStation discovery (the same probe the PS Remote Play app uses)
 *  2. SSDP / UPnP (TVs, Xbox, routers, Roku, Sonos, Hue...) + their description files for real names
 *  3. mDNS / Bonjour (Chromecast, AirPlay, printers, HomeKit, Spotify speakers...)
 *  4. A sweep that knocks on common ports of every address, which finds quiet devices too
 * All sockets are tied to the Wi-Fi network, so it still works when mobile data is the default route.
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
        "_androidtvremote2._tcp" to "Android / Google TV",
        "_amzn-wplay._tcp" to "Fire TV",
        "_hue._tcp" to "Philips Hue bridge",
        "_nvstream._tcp" to "Gaming PC (NVIDIA)",
        "_workstation._tcp" to "Computer",
        "_http._tcp" to "Web-enabled device",
    )

    @Suppress("DEPRECATION")
    fun lanLink(ctx: Context): LanLink? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val nets = cm.allNetworks.toList()
        fun has(n: Network, t: Int) = cm.getNetworkCapabilities(n)?.hasTransport(t) == true
        val (net, transport) = nets.firstOrNull { has(it, NetworkCapabilities.TRANSPORT_WIFI) }?.let { it to "Wi-Fi" }
            ?: nets.firstOrNull { has(it, NetworkCapabilities.TRANSPORT_ETHERNET) }?.let { it to "Ethernet" }
            ?: return null
        val lp = cm.getLinkProperties(net) ?: return null
        val la = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val gw = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
        return LanLink(net, Subnet.toInt(la.address.address), la.prefixLength, gw, transport)
    }

    suspend fun run(
        ctx: Context,
        onFound: (FoundDevice) -> Unit,
        onStatus: (String, String) -> Unit,
        onProgress: (Float) -> Unit,
    ) {
        val app = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        val emit: (FoundDevice) -> Unit = { d -> main.post { onFound(d) } }
        val status: (String, String) -> Unit = { k, v -> main.post { onStatus(k, v) } }
        val progress: (Float) -> Unit = { p -> main.post { onProgress(p) } }

        val link = withContext(Dispatchers.IO) { lanLink(app) }
        if (link == null) {
            status("network", "Not connected to Wi-Fi. Join the network you want to look at, then scan again.")
            return
        }
        status("network", "${link.transport}: your phone is ${link.ipStr}/${link.prefix}, router ${link.gateway ?: "unknown"}")
        emit(FoundDevice(link.ipStr, "This phone", "This phone", sources = setOf("You"), isMe = true))
        link.gateway?.let { emit(FoundDevice(it, "Router", "Router", sources = setOf("Network settings"), isGateway = true)) }

        val lock = app.getSystemService(WifiManager::class.java)
            ?.createMulticastLock("hardwarelens")?.apply { setReferenceCounted(false); acquire() }
        try {
            coroutineScope {
                launch(Dispatchers.IO) {
                    runCatching { playStation(link, emit, status) }.onFailure { status("ps", "PlayStation probe failed: ${it.message}") }
                }
                launch(Dispatchers.IO) {
                    runCatching { ssdp(link, emit, status) }.onFailure { status("ssdp", "SSDP failed: ${it.message}") }
                }
                launch { runCatching { mdns(app, emit, status) }.onFailure { status("mdns", "mDNS failed: ${it.message}") } }
                launch { runCatching { sweep(link, emit, status, progress) }.onFailure { status("sweep", "Sweep failed: ${it.message}") } }
            }
        } finally {
            runCatching { lock?.release() }
        }
    }

    private fun DatagramSocket.bindTo(link: LanLink) {
        link.network?.let { runCatching { it.bindSocket(this) } }
    }

    private inline fun receiveFor(sock: DatagramSocket, ms: Long, handle: (String, String) -> Unit) {
        val end = System.currentTimeMillis() + ms
        val buf = ByteArray(8192)
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

    private fun playStation(link: LanLink, emit: (FoundDevice) -> Unit, status: (String, String) -> Unit) {
        val targets = listOf(
            InetAddress.getByAddress(Subnet.toBytes(Subnet.broadcast(link.ip, link.prefix))),
            InetAddress.getByName("255.255.255.255"),
        )
        val seen = mutableSetOf<String>()
        DatagramSocket().use { sock ->
            sock.bindTo(link)
            sock.broadcast = true
            sock.soTimeout = 400
            val probes = listOf(987 to "00020020", 9302 to "00030010") // PS4, PS5
            repeat(3) {
                for ((port, version) in probes) for (t in targets) {
                    val msg = "SRCH * HTTP/1.1\ndevice-discovery-protocol-version:$version\n".toByteArray()
                    runCatching { sock.send(DatagramPacket(msg, msg.size, t, port)) }
                }
                receiveFor(sock, 1200) { text, addr ->
                    val h = Devices.parseHeaders(text)
                    val statusLine = text.lineSequence().firstOrNull().orEmpty()
                    val state = when {
                        " 200" in statusLine -> "Powered on"
                        " 620" in statusLine -> "In rest mode"
                        else -> statusLine
                    }
                    val type = when (h["host-type"]) { "PS5" -> "PlayStation 5"; "PS4" -> "PlayStation 4"; else -> "PlayStation" }
                    val fw = h["system-version"]?.let { " · system $it" } ?: ""
                    seen += addr
                    emit(FoundDevice(addr, h["host-name"] ?: type, type, listOf(state + fw), setOf("PlayStation discovery")))
                }
            }
        }
        status("ps", if (seen.isEmpty()) "PlayStation probe: no answer (none here, or fully powered off)" else "PlayStation probe: found ${seen.size}")
    }

    private fun ssdp(link: LanLink, emit: (FoundDevice) -> Unit, status: (String, String) -> Unit) {
        val group = InetAddress.getByName("239.255.255.250")
        val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n").toByteArray()
        val locations = ConcurrentHashMap<String, String>()
        val ips = mutableSetOf<String>()
        DatagramSocket().use { sock ->
            sock.bindTo(link)
            sock.soTimeout = 400
            repeat(3) {
                runCatching { sock.send(DatagramPacket(msg, msg.size, group, 1900)) }
                Thread.sleep(150)
            }
            receiveFor(sock, 4000) { text, addr ->
                val h = Devices.parseHeaders(text)
                val server = h["server"].orEmpty()
                val kind = Devices.guessSsdpKind(server, h["st"].orEmpty())
                ips += addr
                emit(FoundDevice(addr, kind, kind, listOf(server), setOf("SSDP / UPnP")))
                h["location"]?.let { locations.putIfAbsent(addr, it) }
            }
        }
        status("ssdp", "SSDP / UPnP: ${ips.size} devices answered" + if (locations.isNotEmpty()) ", reading their names…" else "")
        var named = 0
        for ((addr, loc) in locations.entries.take(30)) {
            val xml = runCatching { httpGet(link, loc, 2500) }.getOrNull() ?: continue
            val friendly = Devices.xmlTag(xml, "friendlyName")
            val maker = Devices.xmlTag(xml, "manufacturer")
            val model = Devices.xmlTag(xml, "modelName")
            val type = Devices.xmlTag(xml, "deviceType").orEmpty()
            val kind = Devices.guessSsdpKind(listOfNotNull(maker, model).joinToString(" "), type)
            if (friendly != null) named++
            emit(
                FoundDevice(
                    addr, friendly ?: "", kind,
                    listOfNotNull(listOfNotNull(maker, model).joinToString(" ").ifBlank { null }),
                    setOf("SSDP / UPnP"),
                )
            )
        }
        status("ssdp", "SSDP / UPnP: ${ips.size} devices answered, $named named themselves")
    }

    private fun httpGet(link: LanLink, url: String, timeout: Int): String {
        val u = URL(url)
        val conn = (link.network?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
        conn.connectTimeout = timeout
        conn.readTimeout = timeout
        try {
            return conn.inputStream.bufferedReader().use { it.readText().take(64_000) }
        } finally {
            conn.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun hostOf(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= 34) {
            info.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress?.let { return it }
        }
        return (info.host as? Inet4Address)?.hostAddress
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? = withTimeoutOrNull(3000) {
        suspendCancellableCoroutine { cont ->
            try {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) { if (cont.isActive) cont.resume(null) }
                    override fun onServiceResolved(si: NsdServiceInfo) { if (cont.isActive) cont.resume(si) }
                })
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private suspend fun mdns(ctx: Context, emit: (FoundDevice) -> Unit, status: (String, String) -> Unit) = coroutineScope {
        val nsd = ctx.getSystemService(NsdManager::class.java) ?: return@coroutineScope
        val queue = Channel<Pair<NsdServiceInfo, String>>(Channel.UNLIMITED)
        val announced = AtomicInteger()
        val failed = AtomicInteger()
        var resolved = 0

        val resolver = launch {
            for ((info, kind) in queue) {
                val r = resolve(nsd, info)
                val ip = r?.let { hostOf(it) }.orEmpty()
                val attrs = r?.attributes.orEmpty()
                fun attr(k: String) = attrs[k]?.let { String(it, Charsets.UTF_8) }?.takeIf { it.isNotBlank() }
                val name = attr("fn") ?: info.serviceName
                val model = attr("md") ?: attr("model") ?: attr("am") ?: attr("ty")
                if (ip.isNotBlank()) resolved++
                emit(FoundDevice(ip, name, kind, listOfNotNull(model, info.serviceType?.trim('.')), setOf("mDNS / Bonjour")))
            }
        }

        // Android allows ~10 discovery requests at once per app, so search in small batches.
        for (batch in serviceTypes.chunked(6)) {
            val started = mutableListOf<NsdManager.DiscoveryListener>()
            for ((type, kind) in batch) {
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onServiceFound(info: NsdServiceInfo) {
                        announced.incrementAndGet()
                        queue.trySend(info to kind)
                    }
                    override fun onServiceLost(info: NsdServiceInfo) {}
                    override fun onDiscoveryStarted(serviceType: String) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { failed.incrementAndGet() }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                }
                if (runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }.isSuccess) started += listener
            }
            try {
                delay(3500)
            } finally {
                started.forEach { l -> runCatching { nsd.stopServiceDiscovery(l) } }
            }
            status("mdns", "mDNS / Bonjour: ${announced.get()} services so far…")
        }
        queue.close()
        if (withTimeoutOrNull(15_000) { resolver.join() } == null) resolver.cancel()
        status(
            "mdns",
            "mDNS / Bonjour: ${announced.get()} services announced, $resolved with an address" +
                if (failed.get() > 0) " (${failed.get()} searches refused by Android)" else ""
        )
    }

    fun tcpProbe(link: LanLink?, ip: String, port: Int, timeoutMs: Int): Probe {
        val sock: Socket = link?.network?.socketFactory?.createSocket() ?: Socket()
        return try {
            sock.connect(InetSocketAddress(ip, port), timeoutMs)
            Probe.Open
        } catch (e: ConnectException) {
            val m = e.message.orEmpty()
            if ("ECONNREFUSED" in m || "refused" in m.lowercase()) Probe.Refused else Probe.Silent
        } catch (e: SocketTimeoutException) {
            Probe.Silent
        } catch (e: IOException) {
            Probe.Silent
        } finally {
            runCatching { sock.close() }
        }
    }

    private fun reverseName(ip: String): String? = runCatching {
        InetAddress.getByName(ip).canonicalHostName
    }.getOrNull()?.takeIf { it != ip && it.isNotBlank() }?.substringBefore('.')

    private suspend fun sweep(link: LanLink, emit: (FoundDevice) -> Unit, status: (String, String) -> Unit, progress: (Float) -> Unit) {
        val hosts = Subnet.sweepHosts(link.ip, link.prefix)
        if (hosts.isEmpty()) {
            status("sweep", "Network sweep: network too small to sweep")
            return
        }
        status("sweep", "Network sweep: checking ${hosts.size} addresses…")
        val sem = Semaphore(48)
        val done = AtomicInteger()
        val alive = AtomicInteger()
        coroutineScope {
            for (h in hosts) launch(Dispatchers.IO) {
                sem.withPermit {
                    val ip = Subnet.toStr(h)
                    var found = false
                    var openPort: Int? = null
                    for (p in Ports.sweep) {
                        when (tcpProbe(link, ip, p, 300)) {
                            Probe.Open -> { found = true; openPort = p; break }
                            Probe.Refused -> { found = true; break }
                            Probe.Silent -> {}
                        }
                    }
                    if (found) {
                        alive.incrementAndGet()
                        val host = reverseName(ip)
                        val kind = host?.let { Devices.guessFromHostname(it) } ?: Devices.guessFromPort(openPort)
                        emit(
                            FoundDevice(
                                ip, host ?: ip, kind,
                                listOfNotNull(openPort?.let { "Answers on ${Ports.name(it)} (port $it)" }),
                                setOf("Network sweep"), openPort = openPort,
                            )
                        )
                    }
                    progress(done.incrementAndGet().toFloat() / hosts.size)
                }
            }
        }
        status("sweep", "Network sweep: ${hosts.size} addresses checked, ${alive.get()} answered")
    }

    // ---------- Tools for one device ----------

    suspend fun openPorts(ctx: Context, ip: String): List<Int> = withContext(Dispatchers.IO) {
        val link = lanLink(ctx.applicationContext)
        coroutineScope {
            Ports.common.map { (port, _) ->
                async { if (tcpProbe(link, ip, port, 800) == Probe.Open) port else null }
            }.mapNotNull { it.await() }
        }
    }

    /** Times a TCP handshake a few times (works even where ICMP ping is blocked). Null = no answer. */
    suspend fun ping(ctx: Context, ip: String, port: Int, tries: Int = 5): List<Long?> = withContext(Dispatchers.IO) {
        val link = lanLink(ctx.applicationContext)
        List(tries) {
            val t0 = System.nanoTime()
            val r = tcpProbe(link, ip, port, 1500)
            val ms = (System.nanoTime() - t0) / 1_000_000
            delay(200)
            if (r == Probe.Silent) null else ms
        }
    }
}
