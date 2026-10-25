package com.sensorpark.data.offline

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sensorpark.data.local.SensorParkDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Los favoritos sin conexión (T19) con Room de verdad, en memoria, y un backend falso al que se le
 * puede cortar la red: lo que se marca se ve al instante y sube recién cuando vuelve la conexión.
 */
@RunWith(AndroidJUnit4::class)
class FavoritesRepositoryRoomTest {
    private lateinit var db: SensorParkDatabase
    private val remote = FakeRemote()
    private val scheduler = CountingScheduler()
    private lateinit var repository: FavoritesRepositoryRoom

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, SensorParkDatabase::class.java).build()
        repository = FavoritesRepositoryRoom(
            db.favorites(), remote, scheduler, Clock.fixed(Instant.parse("2026-10-25T15:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun sinRedElFavoritoSeVeAlInstanteYSubeCuandoVuelveLaConexion() = runTest {
        remote.online = false

        repository.setFavorite("utn-medrano", true)

        assertEquals(setOf("utn-medrano"), repository.favorites().first())
        assertEquals(1, scheduler.scheduled)
        assertFalse("sin red no se puede subir", repository.syncPending())
        assertTrue(remote.ids.isEmpty())

        remote.online = true
        assertTrue(repository.syncPending())
        assertEquals(setOf("utn-medrano"), remote.ids)
        assertTrue("ya no queda nada pendiente", db.favorites().pending().isEmpty())
    }

    @Test
    fun quitarUnFavoritoSinRedLoSacaYaYDelBackendDespues() = runTest {
        repository.setFavorite("abasto", true)
        repository.syncPending()
        remote.online = false

        repository.setFavorite("abasto", false)

        assertEquals(emptySet<String>(), repository.favorites().first())
        assertEquals(setOf("abasto"), remote.ids)
        remote.online = true
        assertTrue(repository.syncPending())
        assertTrue(remote.ids.isEmpty())
    }

    @Test
    fun alReinstalarTraeLosDelBackendSinPisarLosCambiosPendientes() = runTest {
        // Lo que había en el backend de antes de reinstalar.
        remote.ids += setOf("utn-medrano", "abasto")
        // Un cambio hecho en este teléfono que todavía no subió.
        remote.online = false
        repository.setFavorite("parque-centenario", true)
        remote.online = true

        assertEquals(setOf("utn-medrano", "abasto", "parque-centenario"), repository.favorites().first())
    }

    private class FakeRemote : FavoritesRemote {
        val ids = mutableSetOf<String>()
        var online = true

        override suspend fun fetch(): Set<String>? = if (online) ids.toSet() else null

        override suspend fun add(parkingLotId: String) {
            if (!online) throw IOException("sin red")
            ids += parkingLotId
        }

        override suspend fun remove(parkingLotId: String) {
            if (!online) throw IOException("sin red")
            ids -= parkingLotId
        }
    }

    private class CountingScheduler : FavoritesSyncScheduler {
        var scheduled = 0

        override fun schedule() {
            scheduled++
        }
    }
}
