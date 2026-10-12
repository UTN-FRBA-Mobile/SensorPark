package com.sensorpark.app.model

/**
 * Rol del usuario. Viaja como custom claim `rol` en el token de Auth y lo asigna
 * únicamente el backend (ver functions/index.js); los nombres tienen que coincidir.
 */
enum class Rol {
    CONDUCTOR,
    ADMIN_ESTACIONAMIENTO,
    ADMIN;

    companion object {
        fun desde(valor: Any?): Rol? = entries.firstOrNull { it.name == valor }
    }
}
