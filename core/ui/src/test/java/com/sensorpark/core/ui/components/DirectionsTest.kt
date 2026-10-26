package com.sensorpark.core.ui.components

import com.sensorpark.core.ui.theme.LightStatusColors
import com.sensorpark.domain.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionsTest {
    private val medrano = Coordinates(-34.5984, -58.42)

    @Test
    fun primeroPideLaNavegacionDeGoogleMapsYDespuesLasAlternativas() {
        val uris = directionsUris(medrano, "UTN Medrano")

        assertEquals("google.navigation:q=-34.598400,-58.420000&mode=d", uris[0])
        assertTrue(uris[1].startsWith("geo:-34.598400,-58.420000?q="))
        assertTrue(uris[2].startsWith("https://www.google.com/maps/dir/"))
        assertEquals(3, uris.size)
    }

    @Test
    fun elNombreVaCodificadoYElPuntoConPuntoDecimalSinImportarElIdioma() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-AR"))
        try {
            val geo = directionsUris(medrano, "Playa Abasto & Co.")[1]

            assertEquals("geo:-34.598400,-58.420000?q=-34.598400,-58.420000(Playa%20Abasto%20%26%20Co.)", geo)
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun elColorDeDisponibilidadEsVerdeAmbarOrojo() {
        val colors = LightStatusColors

        assertEquals(colors.positive, availabilityColor(free = 10, capacity = 14, colors = colors))
        assertEquals(colors.warning, availabilityColor(free = 3, capacity = 14, colors = colors))
        assertEquals(colors.warning, availabilityColor(free = 1, capacity = 4, colors = colors))
        assertEquals(colors.negative, availabilityColor(free = 0, capacity = 14, colors = colors))
    }
}
