package com.sensorpark.domain.usecase

import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.DataErrorReason
import com.sensorpark.domain.model.DataResult
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.SpotType
import com.sensorpark.domain.repository.ParkingLotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNearbyParkingLotsUseCaseTest {
    // Los mismos estacionamientos que la semilla (functions/seed.js).
    private val utnMedrano = lot("utn-medrano", -34.5984, -58.4200, freeSpots = 0)
    private val abasto = lot(
        "abasto", -34.6037, -58.4108,
        capacityByType = mapOf(SpotType.STANDARD to 10, SpotType.MOTORCYCLE to 2),
        freeSpotsByType = mapOf(SpotType.STANDARD to 3),
    )
    private val parqueCentenario = lot(
        "parque-centenario", -34.6065, -58.4355,
        capacityByType = mapOf(SpotType.STANDARD to 10, SpotType.MOTORCYCLE to 2),
        freeSpotsByType = mapOf(SpotType.MOTORCYCLE to 1),
    )
    private val all = listOf(parqueCentenario, utnMedrano, abasto)
    private val atUtn = Coordinates(-34.5984, -58.4200)

    private fun search(
        radiusMeters: Double = SearchNearbyParkingLotsUseCase.DEFAULT_RADIUS_METERS,
        filter: ParkingLotFilter = ParkingLotFilter(),
    ) = SearchNearbyParkingLotsUseCase.nearby(all, atUtn, radiusMeters, filter).map { it.parkingLot.id }

    @Test
    fun `ordena del mas cercano al mas lejano`() {
        assertEquals(listOf("utn-medrano", "abasto", "parque-centenario"), search())
    }

    @Test
    fun `deja afuera los que estan mas lejos que el radio`() {
        assertEquals(listOf("utn-medrano", "abasto"), search(radiusMeters = 1_500.0))
    }

    @Test
    fun `con favoritos muestra solo esos, aunque esten fuera del radio`() {
        val favorites = ParkingLotFilter(favoriteIds = setOf("parque-centenario"))
        assertEquals(listOf("parque-centenario"), search(radiusMeters = 1_500.0, filter = favorites))
        // Los demás filtros siguen valiendo sobre los favoritos.
        assertEquals(
            emptyList<String>(),
            search(filter = favorites.copy(spotType = SpotType.STANDARD, onlyWithFreeSpots = true)),
        )
        assertEquals(emptyList<String>(), search(filter = ParkingLotFilter(favoriteIds = emptySet())))
    }

    @Test
    fun `informa la distancia de cada uno`() {
        val nearby = SearchNearbyParkingLotsUseCase.nearby(all, atUtn, 2_000.0, ParkingLotFilter())
        assertEquals(0.0, nearby[0].distanceMeters, 0.0)
        assertEquals(1_030.0, nearby[1].distanceMeters, 15.0)
    }

    @Test
    fun `solo con lugares libres`() {
        assertEquals(listOf("abasto", "parque-centenario"), search(filter = ParkingLotFilter(onlyWithFreeSpots = true)))
    }

    @Test
    fun `por tipo, los que tienen lugares de ese tipo aunque esten ocupados`() {
        assertEquals(
            listOf("abasto", "parque-centenario"),
            search(filter = ParkingLotFilter(spotType = SpotType.MOTORCYCLE)),
        )
    }

    @Test
    fun `por tipo y solo libres, los que tienen libre uno de ese tipo`() {
        assertEquals(
            listOf("parque-centenario"),
            search(filter = ParkingLotFilter(spotType = SpotType.MOTORCYCLE, onlyWithFreeSpots = true)),
        )
    }

    @Test
    fun `sin estacionamientos en el radio devuelve una lista vacia`() {
        val inCordoba = Coordinates(-31.4, -64.18)
        val farAway = SearchNearbyParkingLotsUseCase.nearby(all, inCordoba, 2_000.0, ParkingLotFilter())
        assertTrue(farAway.isEmpty())
    }

    @Test
    fun `un error del repositorio llega tal cual`() = runTest {
        val failing = object : ParkingLotRepository {
            override fun parkingLots(): Flow<DataResult<List<ParkingLot>>> =
                flowOf(DataResult.Failure(DataErrorReason.NO_CONNECTION))
            override fun parkingLot(id: String): Flow<DataResult<ParkingLot>> = error("no se usa")
        }

        assertEquals(
            DataResult.Failure(DataErrorReason.NO_CONNECTION),
            SearchNearbyParkingLotsUseCase(failing)(atUtn).first(),
        )
    }

    private fun lot(
        id: String,
        lat: Double,
        lng: Double,
        freeSpots: Int = 3,
        capacityByType: Map<SpotType, Int> = mapOf(SpotType.STANDARD to 10),
        freeSpotsByType: Map<SpotType, Int> = mapOf(SpotType.STANDARD to freeSpots),
    ) = ParkingLot(
        id = id,
        name = id,
        address = "",
        location = Coordinates(lat, lng),
        capacity = capacityByType.values.sum(),
        freeSpots = freeSpotsByType.values.sum(),
        capacityByType = capacityByType,
        freeSpotsByType = freeSpotsByType,
    )
}
