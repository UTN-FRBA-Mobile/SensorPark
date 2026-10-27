package com.sensorpark.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sensorpark.domain.model.ActiveGeofence
import com.sensorpark.domain.model.Coordinates
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * La tabla de geocercas con Room de verdad, en memoria. Es lo que permite volver a registrarlas
 * después de reiniciar el teléfono (T20), así que tiene que guardar y devolver todo tal cual.
 */
@RunWith(AndroidJUnit4::class)
class GeofenceDaoTest {
    private lateinit var db: SensorParkDatabase
    private lateinit var dao: GeofenceDao

    private val geofence = ActiveGeofence(
        reservationId = "res-1",
        parkingLotName = "UTN Medrano",
        spotCode = "07",
        center = Coordinates(-34.5984, -58.4200),
        radiusMeters = 300,
        expiresAt = Instant.parse("2026-10-18T15:15:00Z"),
        arrivesAt = Instant.parse("2026-10-18T15:00:00Z"),
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, SensorParkDatabase::class.java).build()
        dao = db.geofences()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun guardaYDevuelveLaGeocercaTalCual() = runTest {
        dao.save(GeofenceEntity.from(geofence))

        assertEquals(geofence, dao.find("res-1")?.toDomain())
        assertEquals(listOf(geofence), dao.all().map { it.toDomain() })
    }

    @Test
    fun volverAGuardarLaMismaReservaLaReemplaza() = runTest {
        dao.save(GeofenceEntity.from(geofence))
        dao.save(GeofenceEntity.from(geofence.copy(spotCode = "09")))

        assertEquals(listOf("09"), dao.all().map { it.spotCode })
    }

    @Test
    fun borrarSacaSoloEsaReserva() = runTest {
        dao.save(GeofenceEntity.from(geofence))
        dao.save(GeofenceEntity.from(geofence.copy(reservationId = "res-2")))

        dao.delete("res-1")

        assertNull(dao.find("res-1"))
        assertEquals(listOf("res-2"), dao.all().map { it.reservationId })
    }
}
