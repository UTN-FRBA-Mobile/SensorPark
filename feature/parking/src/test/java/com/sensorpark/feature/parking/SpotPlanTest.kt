package com.sensorpark.feature.parking

import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotPlanTest {
    @Test
    fun laCeldaConservaTodoLoQueDibujaYVuelveAlLugar() {
        val spot = Spot("s-9", "09", SpotType.PRIORITY, SpotStatus.FREE, Category.REDUCED_MOBILITY)

        val cell = spot.toCell()

        assertEquals("s-9", cell.id)
        assertEquals("09", cell.code)
        assertEquals(Category.REDUCED_MOBILITY, cell.category)
        assertEquals(spot, cell.toSpot())
    }

    @Test
    fun soloUnLugarLibreSePuedeReservar() {
        SpotStatus.entries.forEach { status ->
            val cell = Spot("s", "01", SpotType.STANDARD, status).toCell()
            assertEquals(status == SpotStatus.FREE, cell.isFree)
        }
    }

    @Test
    fun dosCeldasConLosMismosDatosSonIguales() {
        // Es lo que permite a Compose saltearse la celda cuando llega una lista nueva sin cambios en ese lugar.
        val first = Spot("s", "01", SpotType.STANDARD, SpotStatus.OCCUPIED).toCell()
        val second = Spot("s", "01", SpotType.STANDARD, SpotStatus.OCCUPIED).toCell()
        val changed = Spot("s", "01", SpotType.STANDARD, SpotStatus.FREE).toCell()

        assertTrue(first == second)
        assertFalse(first == changed)
    }

    @Test
    fun unLugarPreferencialSoloQuedaHabilitadoParaQuienTieneSuCategoria() {
        val pregnantSpot = Spot("s-9", "09", SpotType.PRIORITY, SpotStatus.FREE, Category.PREGNANT)
        val mobilitySpot = Spot("s-10", "10", SpotType.PRIORITY, SpotStatus.FREE, Category.REDUCED_MOBILITY)
        val standardSpot = Spot("s-1", "01", SpotType.STANDARD, SpotStatus.FREE)

        assertFalse(pregnantSpot.toCell(Category.STANDARD).eligible)
        assertFalse(pregnantSpot.toCell(Category.REDUCED_MOBILITY).eligible)
        assertTrue(pregnantSpot.toCell(Category.PREGNANT).eligible)

        assertFalse(mobilitySpot.toCell(Category.STANDARD).eligible)
        assertFalse(mobilitySpot.toCell(Category.PREGNANT).eligible)
        assertTrue(mobilitySpot.toCell(Category.REDUCED_MOBILITY).eligible)

        assertTrue(standardSpot.toCell(Category.STANDARD).eligible)
    }
}
