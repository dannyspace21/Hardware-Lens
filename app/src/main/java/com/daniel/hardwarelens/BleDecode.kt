package com.daniel.hardwarelens

import java.util.UUID

/** What a Bluetooth advertisement contains, in plain Kotlin types (testable without Android). */
class BleAdvert(
    val name: String?,
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    val serviceUuids16: List<Int> = emptyList(),
    val serviceData16: Map<Int, ByteArray> = emptyMap(),
)

/** A best guess at what kind of thing is advertising, e.g. "AirTag / Find My". */
data class BleKind(val label: String, val category: String)

object BleDecode {
    private val companies = mapOf(
        0x004C to "Apple", 0x0006 to "Microsoft", 0x0075 to "Samsung", 0x00E0 to "Google",
        0x012D to "Sony", 0x0087 to "Garmin", 0x0157 to "Amazfit", 0x038F to "Xiaomi",
        0x0059 to "Nordic Semi", 0x02E5 to "Espressif", 0x0171 to "Amazon", 0x009E to "Bose",
        0x0499 to "Ruuvi", 0x02FF to "Silicon Labs", 0x000F to "Broadcom", 0x0002 to "Intel",
        0x001D to "Qualcomm", 0x0131 to "Cypress", 0x0822 to "Adafruit", 0x0078 to "Nike",
        0x0397 to "LEGO", 0x0154 to "Pebble", 0x0003 to "IBM", 0x000A to "Qualcomm (CSR)",
        0x0046 to "MediaTek", 0x05A7 to "Sonos",
    )

    fun companyName(id: Int): String = companies[id] ?: "Maker ID 0x%04X".format(id)

    /** "0000180d-0000-1000-8000-00805f9b34fb" -> 0x180D, anything else -> null. */
    fun short16(uuid: UUID): Int? {
        val s = uuid.toString().lowercase()
        return if (s.startsWith("0000") && s.endsWith("-0000-1000-8000-00805f9b34fb")) s.substring(4, 8).toInt(16) else null
    }

    private val services = mapOf(
        0xFD5A to BleKind("Samsung SmartTag", "Tracker"),
        0xFEED to BleKind("Tile tracker", "Tracker"),
        0xFEEC to BleKind("Tile tracker", "Tracker"),
        0xFEAA to BleKind("Eddystone beacon", "Beacon"),
        0xFE2C to BleKind("Google Fast Pair device", "Audio / accessory"),
        0xFD6F to BleKind("Exposure-notification beacon", "Phone"),
        0x180D to BleKind("Heart-rate monitor", "Health & fitness"),
        0x1812 to BleKind("Keyboard / mouse / controller", "Input device"),
        0x1826 to BleKind("Fitness machine", "Health & fitness"),
        0x1816 to BleKind("Cycling speed sensor", "Health & fitness"),
        0x1818 to BleKind("Cycling power meter", "Health & fitness"),
        0x1810 to BleKind("Blood-pressure monitor", "Health & fitness"),
        0x1808 to BleKind("Glucose meter", "Health & fitness"),
        0x181A to BleKind("Environmental sensor", "Sensor"),
        0x181D to BleKind("Weight scale", "Health & fitness"),
        0xFE95 to BleKind("Xiaomi / Mi Home device", "Smart home"),
        0xFE9F to BleKind("Google device", "Other"),
        0xFE07 to BleKind("Sonos speaker", "Audio / accessory"),
        0xFE03 to BleKind("Amazon Echo / Alexa device", "Smart home"),
    )

    private val appleTypes = mapOf(
        0x12 to BleKind("AirTag / Find My device", "Tracker"),
        0x07 to BleKind("AirPods / Beats", "Audio / accessory"),
        0x10 to BleKind("iPhone / iPad / Mac", "Phone"),
        0x0C to BleKind("Apple device (Handoff)", "Phone"),
        0x09 to BleKind("AirPlay device", "Audio / accessory"),
        0x05 to BleKind("Apple device (AirDrop)", "Phone"),
        0x0F to BleKind("Apple device", "Phone"),
        0x16 to BleKind("Apple device", "Phone"),
    )

    fun classify(a: BleAdvert): BleKind? {
        a.manufacturerData[0x004C]?.let { d ->
            if (d.size >= 2 && d[0] == 0x02.toByte() && d[1] == 0x15.toByte()) return BleKind("iBeacon", "Beacon")
            val type = if (d.isNotEmpty()) d[0].toInt() and 0xFF else -1
            return appleTypes[type] ?: BleKind("Apple device", "Phone")
        }
        a.manufacturerData[0x0006]?.let { d ->
            return if (d.isNotEmpty() && d[0] == 0x03.toByte()) BleKind("Windows Swift Pair accessory", "Input device")
            else BleKind("Windows PC / Microsoft device", "Computer")
        }
        for (u in a.serviceUuids16 + a.serviceData16.keys) services[u]?.let { return it }
        val n = a.name?.lowercase().orEmpty()
        when {
            n.isBlank() -> {}
            listOf("watch", "band", "fit", "garmin", "polar").any { it in n } -> return BleKind("Watch / fitness band", "Health & fitness")
            listOf("buds", "airpods", "headphone", "earbud", "wh-", "wf-", "jbl", "bose", "beats").any { it in n } ->
                return BleKind("Headphones / earbuds", "Audio / accessory")
            listOf("tv", "bravia", "roku").any { it in n } -> return BleKind("TV", "Smart home")
            listOf("keyboard", "mouse", "controller", "gamepad").any { it in n } -> return BleKind("Keyboard / mouse / controller", "Input device")
        }
        a.manufacturerData.keys.firstOrNull()?.let { return BleKind("${companyName(it)} device", "Other") }
        return null
    }
}
