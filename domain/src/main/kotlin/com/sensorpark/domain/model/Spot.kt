package com.sensorpark.domain.model

enum class SpotType {
    STANDARD,

    /** Preferencial. */
    PRIORITY,
    MOTORCYCLE,

    /** Con cargador para autos eléctricos. */
    EV_CHARGING;

    companion object {
        fun from(value: Any?): SpotType? = entries.firstOrNull { it.name == value }
    }
}

enum class SpotStatus {
    FREE,
    OCCUPIED,
    RESERVED,
    OUT_OF_SERVICE;

    companion object {
        fun from(value: Any?): SpotStatus? = entries.firstOrNull { it.name == value }
    }
}

/** Un lugar de un estacionamiento. `code` es el que se ve pintado en el piso, por ejemplo "A-01". */
data class Spot(
    val id: String,
    val code: String,
    val type: SpotType,
    val status: SpotStatus,
)
