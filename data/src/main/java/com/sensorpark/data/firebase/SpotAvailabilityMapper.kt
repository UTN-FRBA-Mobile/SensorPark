package com.sensorpark.data.firebase

import com.sensorpark.domain.model.SpotAvailability
import java.time.Instant

/** Un lugar como lo trae el snapshot: su id, sus datos y la hora de su último cambio. */
internal data class SpotDocument(
    val id: String,
    val data: Map<String, Any?>,
    val updatedAt: Instant?,
)

/**
 * Los lugares de un estacionamiento, ordenados por código. Un lugar con tipo o estado
 * desconocido se descarta (ver [spotFrom]).
 */
internal fun spotAvailabilityFrom(documents: List<SpotDocument>, isFromCache: Boolean): SpotAvailability =
    SpotAvailability(
        spots = documents.mapNotNull { spotFrom(it.id, it.data) }.sortedBy { it.code },
        isFromCache = isFromCache,
        lastUpdate = documents.mapNotNull { it.updatedAt }.maxOrNull(),
    )
