package com.example.nagivator.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MapLibreStyleHelperTest {

    @Test
    fun `styleUrlFor returns OpenFreeMap liberty for day and dark for night`() {
        assertEquals(MapLibreStyleHelper.DAY_STYLE_URL, MapLibreStyleHelper.styleUrlFor(false))
        assertEquals(MapLibreStyleHelper.NIGHT_STYLE_URL, MapLibreStyleHelper.styleUrlFor(true))
        assertNotEquals(MapLibreStyleHelper.DAY_STYLE_URL, MapLibreStyleHelper.NIGHT_STYLE_URL)
    }
}
