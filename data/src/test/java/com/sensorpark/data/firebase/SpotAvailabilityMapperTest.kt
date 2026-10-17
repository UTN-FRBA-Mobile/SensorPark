package com.sensorpark.data.firebase

import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SpotAvailabilityMapperTest {
    private fun doc(id: String, code: String, status: String, updatedAt: Instant? = null, type: String = "STANDARD") =
        SpotDocument(id, mapOf("code" to code, "type" to type, "status" to status), updatedAt)

    @Test
    fun ordenaLosLugaresPorCodigo() {
        val result = spotAvailabilityFrom(
            listOf(doc("c", "03", "FREE"), doc("a", "01", "OCCUPIED"), doc("b", "02", "RESERVED")),
            isFromCache = false,
        )

        assertEquals(listOf("01", "02", "03"), result.spots.map { it.code })
        assertEquals(listOf(SpotStatus.OCCUPIED, SpotStatus.RESERVED, SpotStatus.FREE), result.spots.map { it.status })
    }

    @Test
    fun descartaLosLugaresConTipoOEstadoDesconocido() {
        val result = spotAvailabilityFrom(
            listOf(doc("a", "01", "FREE"), doc("b", "02", "ROTO"), doc("c", "03", "FREE", type = "HELICOPTERO")),
            isFromCache = false,
        )

        assertEquals(listOf("01"), result.spots.map { it.code })
    }

    @Test
    fun laHoraDeLaUltimaActualizacionEsLaMasReciente() {
        val result = spotAvailabilityFrom(
            listOf(
                doc("a", "01", "FREE", Instant.parse("2026-10-17T14:00:00Z")),
                doc("b", "02", "FREE", Instant.parse("2026-10-17T14:05:00Z")),
                doc("c", "03", "FREE", null),
            ),
            isFromCache = false,
        )

        assertEquals(Instant.parse("2026-10-17T14:05:00Z"), result.lastUpdate)
    }

    @Test
    fun sinNingunaHoraLaUltimaActualizacionEsNull() {
        assertNull(spotAvailabilityFrom(listOf(doc("a", "01", "FREE")), isFromCache = false).lastUpdate)
    }

    @Test
    fun indicaSiLosDatosVienenDeLaCache() {
        assertTrue(spotAvailabilityFrom(listOf(doc("a", "01", "FREE")), isFromCache = true).isFromCache)
        assertFalse(spotAvailabilityFrom(listOf(doc("a", "01", "FREE")), isFromCache = false).isFromCache)
    }

    @Test
    fun conservaLaCategoriaDeLosPreferenciales() {
        val priority = SpotDocument(
            "p", mapOf("code" to "09", "type" to "PRIORITY", "status" to "FREE", "priorityCategory" to "PREGNANT"), null,
        )

        val spot = spotAvailabilityFrom(listOf(priority), isFromCache = false).spots.single()

        assertEquals(SpotType.PRIORITY, spot.type)
        assertEquals(Category.PREGNANT, spot.priorityCategory)
    }

    @Test
    fun sinLugaresLaListaEstaVacia() {
        assertTrue(spotAvailabilityFrom(emptyList(), isFromCache = false).spots.isEmpty())
    }
}
