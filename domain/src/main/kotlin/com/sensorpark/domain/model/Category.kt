package com.sensorpark.domain.model

/**
 * Categoría de prioridad del conductor. La declara él mismo, de buena fe, al registrarse o desde
 * Preferencias, y define qué lugares preferenciales puede usar.
 */
enum class Category {
    /** Sin prioridad. */
    STANDARD,

    /** Embarazada. */
    PREGNANT,

    /** Movilidad reducida o discapacidad. */
    REDUCED_MOBILITY;

    companion object {
        fun from(value: Any?): Category = entries.firstOrNull { it.name == value } ?: STANDARD
    }
}
