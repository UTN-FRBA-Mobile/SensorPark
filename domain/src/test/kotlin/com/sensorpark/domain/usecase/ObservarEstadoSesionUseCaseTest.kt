package com.sensorpark.domain.usecase

import com.sensorpark.domain.model.EstadoSesion
import com.sensorpark.domain.model.Rol
import com.sensorpark.domain.model.Sesion
import com.sensorpark.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservarEstadoSesionUseCaseTest {
    private val ana = Sesion(uid = "ana", email = "ana@ejemplo.com")

    // Ejemplo de repositorio falso: es lo que permite probar sin Firebase.
    private class AuthRepositoryFalso(sesion: Sesion?, rol: Rol?) : AuthRepository {
        override val sesion = MutableStateFlow(sesion)
        val rolActual = MutableStateFlow(rol)

        override suspend fun ingresar(email: String, password: String) = Unit
        override suspend fun registrar(nombre: String, email: String, password: String) = Unit
        override fun salir() { sesion.value = null }
        override fun rol(uid: String): Flow<Rol?> = rolActual
    }

    @Test
    fun `sin sesion emite SinSesion`() = runTest {
        val observar = ObservarEstadoSesionUseCase(AuthRepositoryFalso(sesion = null, rol = null))

        assertEquals(EstadoSesion.SinSesion, observar().first())
    }

    @Test
    fun `con sesion emite primero sin rol y despues el rol asignado`() = runTest {
        val observar = ObservarEstadoSesionUseCase(AuthRepositoryFalso(sesion = ana, rol = Rol.CONDUCTOR))

        assertEquals(
            listOf(EstadoSesion.Activa(ana, rol = null), EstadoSesion.Activa(ana, Rol.CONDUCTOR)),
            observar().take(2).toList(),
        )
    }
}
