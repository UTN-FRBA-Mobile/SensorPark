package com.sensorpark.domain.repository

import com.sensorpark.domain.model.AuthException
import com.sensorpark.domain.model.Rol
import com.sensorpark.domain.model.Sesion
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Emite la sesión iniciada, o null si no hay. */
    val sesion: Flow<Sesion?>

    /** @throws AuthException */
    suspend fun ingresar(email: String, password: String)

    /** @throws AuthException */
    suspend fun registrar(nombre: String, email: String, password: String)

    fun salir()

    /** Rol del usuario, o null mientras el backend todavía no lo asignó. */
    fun rol(uid: String): Flow<Rol?>
}
