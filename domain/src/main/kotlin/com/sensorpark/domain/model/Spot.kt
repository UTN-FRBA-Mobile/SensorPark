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

/** Posición relativa de un lugar dentro del plano de un piso ([levelId]), con `x` e `y` entre `0f` y `1f`. */
data class SpotPlanPosition(
    val levelId: String,
    val x: Float,
    val y: Float,
) {
    val isValid: Boolean get() = levelId.isNotBlank() && x in 0f..1f && y in 0f..1f
}

/** Un lugar de un estacionamiento. `code` es el que se ve pintado en el piso, por ejemplo "07". */
data class Spot(
    val id: String,
    val code: String,
    val type: SpotType,
    val status: SpotStatus,
    /** Sólo en lugares [SpotType.PRIORITY]: la categoría que puede usarlo. */
    val priorityCategory: Category? = null,
    /** Piso y coordenadas donde está marcado este lugar en el plano, o `null` si aún no se ubicó. */
    val planPosition: SpotPlanPosition? = null,
) {
    /** Si un conductor con [userCategory] cumple la categoría requerida para elegir este lugar. */
    fun canBeReservedBy(userCategory: Category): Boolean =
        type != SpotType.PRIORITY ||
            if (priorityCategory != null && priorityCategory != Category.STANDARD) {
                userCategory == priorityCategory
            } else {
                userCategory != Category.STANDARD
            }
}
