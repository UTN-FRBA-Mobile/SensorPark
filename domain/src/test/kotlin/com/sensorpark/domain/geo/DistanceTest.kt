package com.sensorpark.domain.geo

import com.sensorpark.domain.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceTest {
    private val utnMedrano = Coordinates(-34.5984, -58.4200)
    private val abasto = Coordinates(-34.6037, -58.4108)

    @Test
    fun `un grado de latitud son unos 111 km`() {
        assertEquals(111_195.0, distanceMeters(Coordinates(0.0, 0.0), Coordinates(1.0, 0.0)), 1.0)
    }

    @Test
    fun `de un punto a si mismo hay cero metros`() {
        assertEquals(0.0, distanceMeters(utnMedrano, utnMedrano), 0.0)
    }

    @Test
    fun `de UTN Medrano al Abasto hay alrededor de un kilometro, en los dos sentidos`() {
        assertEquals(1_030.0, distanceMeters(utnMedrano, abasto), 15.0)
        assertEquals(distanceMeters(utnMedrano, abasto), distanceMeters(abasto, utnMedrano), 1e-6)
    }
}
