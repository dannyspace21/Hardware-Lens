package com.daniel.hardwarelens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatTest {
    @Test fun unitsConvert() {
        Locale.setDefault(Locale.US)
        assertEquals("10.0 m", Fmt.distance(10.0, Units.Metric))
        assertEquals("32.8 ft", Fmt.distance(10.0, Units.Imperial))
        assertEquals("98.6 °F", Fmt.temp(37.0, Units.Imperial))
        assertEquals("36.0 km/h", Fmt.speed(10.0, Units.Metric))
    }

    @Test fun cardinalDirections() {
        assertEquals("N", cardinal(0f))
        assertEquals("N", cardinal(350f))
        assertEquals("NE", cardinal(45f))
        assertEquals("S", cardinal(180f))
        assertEquals("NW", cardinal(315f))
    }

    @Test fun gnssNames() {
        assertEquals("Galileo", Gnss.shortName(6))
        assertEquals("L5/E5a/B2a", Gnss.bandName(1176.45f))
        assertEquals("L1/E1/B1", Gnss.bandName(1575.42f))
    }
}
