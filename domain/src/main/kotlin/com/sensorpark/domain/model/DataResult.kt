package com.sensorpark.domain.model

enum class DataErrorReason {
    NOT_FOUND,
    PERMISSION_DENIED,
    NO_CONNECTION,
    UNKNOWN,
}

/**
 * Resultado de una lectura de datos. Los repositorios lo devuelven en lugar de dejar pasar las
 * excepciones del proveedor, así la UI puede mostrar cada error sin conocer a Firestore.
 */
sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>

    data class Failure(val reason: DataErrorReason) : DataResult<Nothing>
}

inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(value))
    is DataResult.Failure -> this
}
