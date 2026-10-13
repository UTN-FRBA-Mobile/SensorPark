package com.sensorpark.domain.model

enum class AuthErrorReason {
    INVALID_CREDENTIALS,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    NO_CONNECTION,
    UNKNOWN,
}

/** Error de ingreso o registro, ya traducido para que la UI no conozca al proveedor de Auth. */
class AuthException(val reason: AuthErrorReason, cause: Throwable? = null) : Exception(reason.name, cause)
