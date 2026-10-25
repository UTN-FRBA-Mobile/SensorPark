package com.sensorpark.data.firebase

import com.sensorpark.domain.model.Role
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoleResolutionTest {
    @Test
    fun `vale el rol del claim cuando se puede leer`() = runTest {
        assertEquals(Role.PARKING_ADMIN, resolveRole("DRIVER") { Role.PARKING_ADMIN })
    }

    @Test
    fun `sin red ni token vigente usa el rol copiado en el documento`() = runTest {
        assertEquals(Role.DRIVER, resolveRole("DRIVER") { null })
    }

    @Test
    fun `un rol desconocido en el documento no inventa uno`() = runTest {
        assertNull(resolveRole("SUPERHEROE") { null })
    }
}
