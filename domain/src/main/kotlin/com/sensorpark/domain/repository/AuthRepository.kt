package com.sensorpark.domain.repository

import com.sensorpark.domain.model.AuthException
import com.sensorpark.domain.model.Role
import com.sensorpark.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Emite la sesión iniciada, o null si no hay. */
    val session: Flow<Session?>

    /** @throws AuthException */
    suspend fun signIn(email: String, password: String)

    /** @throws AuthException */
    suspend fun signUp(name: String, email: String, password: String)

    fun signOut()

    /** Rol del usuario, o null mientras el backend todavía no lo asignó. */
    fun role(uid: String): Flow<Role?>
}
