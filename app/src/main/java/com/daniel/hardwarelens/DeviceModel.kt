package com.daniel.hardwarelens

/** Something found on the local network. Several discovery methods can find the same device. */
data class FoundDevice(
    val ip: String,
    val name: String,
    val kind: String,
    val details: List<String> = emptyList(),
    val sources: Set<String> = emptySet(),
    val openPort: Int? = null,
    val isGateway: Boolean = false,
    val isMe: Boolean = false,
    val mac: String? = null,
    val macSource: String? = null,
) {
    val key: String get() = ip.ifBlank { "name:$name" }
}

object Devices {
    private val genericKinds = setOf("Network device", "UPnP device", "Web-enabled device", "Device")
    private val placeholderNames = setOf("Router", "Network device", "UPnP device", "Device")
    private val ipRegex = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    fun isConsole(kind: String) = kind.startsWith("PS") || "PlayStation" in kind || "Xbox" in kind

    fun kindRank(kind: String): Int = when {
        isConsole(kind) -> 3
        kind in genericKinds || kind.isBlank() -> 0
        kind == "Router" -> 2
        else -> 1
    }

    fun nameRank(name: String): Int = when {
        name.isBlank() -> 0
        ipRegex.matches(name) || name in placeholderNames -> 1
        else -> 2
    }

    fun merge(a: FoundDevice, b: FoundDevice) = FoundDevice(
        ip = a.ip.ifBlank { b.ip },
        name = if (nameRank(b.name) > nameRank(a.name)) b.name else a.name,
        kind = if (kindRank(b.kind) > kindRank(a.kind)) b.kind else a.kind,
        details = (a.details + b.details).filter { it.isNotBlank() }.distinct().take(8),
        sources = a.sources + b.sources,
        openPort = a.openPort ?: b.openPort,
        isGateway = a.isGateway || b.isGateway,
        isMe = a.isMe || b.isMe,
        mac = a.mac ?: b.mac,
        macSource = if (a.mac != null) a.macSource else b.macSource,
    )

    /** Sort order for the list: you, your router, game consoles, then everything else by kind. */
    val displayOrder: Comparator<FoundDevice> = compareBy<FoundDevice>(
        { !it.isMe }, { !it.isGateway }, { !isConsole(it.kind) }, { kindRank(it.kind) == 0 }, { it.kind }, { it.name },
    )

    fun parseHeaders(text: String): Map<String, String> =
        text.lineSequence().mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()

    fun xmlTag(xml: String, tag: String): String? =
        Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
            ?.replace("&amp;", "&")?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&apos;", "'")?.replace("&quot;", "\"")
            ?.trim()?.takeIf { it.isNotBlank() }

    fun guessSsdpKind(server: String, st: String): String {
        val s = "$server $st".lowercase()
        return when {
            "xbox" in s -> "Xbox"
            "playstation" in s || "ps4" in s || "ps5" in s -> "PlayStation"
            "roku" in s -> "Roku"
            "sonos" in s -> "Sonos speaker"
            "internetgatewaydevice" in s || "wanipconnection" in s || "router" in s -> "Router"
            "ipbridge" in s || "hue" in s -> "Philips Hue bridge"
            "webos" in s || "lg electronics" in s -> "LG TV"
            "samsung" in s || "tizen" in s -> "Samsung TV / device"
            "chromecast" in s || "google" in s -> "Chromecast / Google device"
            "printer" in s || "epson" in s || "brother" in s || "canon" in s -> "Printer"
            "mediarenderer" in s || "dial" in s || "tv" in s -> "TV / media player"
            "mediaserver" in s || "nas" in s || "synology" in s -> "Media server / NAS"
            "windows" in s -> "Windows PC"
            else -> "UPnP device"
        }
    }

    fun guessFromPort(port: Int?): String = when (port) {
        62078 -> "iPhone / iPad"
        8008, 8009 -> "Chromecast / Google device"
        9295 -> "PlayStation"
        7000 -> "Apple / AirPlay device"
        445, 139 -> "PC / file server"
        554 -> "Camera / video device"
        22 -> "Computer / server"
        631, 9100 -> "Printer"
        else -> "Network device"
    }

    fun guessFromHostname(host: String): String? {
        val h = host.lowercase()
        return when {
            "iphone" in h -> "iPhone"
            "ipad" in h -> "iPad"
            "macbook" in h || "imac" in h || "mac-mini" in h || "macmini" in h -> "Mac"
            "appletv" in h || "apple-tv" in h -> "Apple TV"
            "ps5" in h || "ps4" in h || "playstation" in h -> "PlayStation"
            "xbox" in h -> "Xbox"
            "nintendo" in h || "switch" in h -> "Nintendo Switch"
            "chromecast" in h || "google-home" in h || "nest" in h -> "Chromecast / Google device"
            "galaxy" in h || "android" in h || "pixel" in h || "oneplus" in h -> "Android phone"
            h.startsWith("desktop-") || h.startsWith("laptop-") -> "Windows PC"
            "printer" in h || h.startsWith("hp") || "epson" in h || "brother" in h || "canon" in h -> "Printer"
            "roku" in h -> "Roku"
            "echo" in h || "amazon" in h || "kindle" in h || "fire" in h -> "Amazon device"
            "-tv" in h || "tv-" in h || "smarttv" in h || "bravia" in h -> "TV"
            "router" in h || "gateway" in h -> "Router"
            else -> null
        }
    }
}
