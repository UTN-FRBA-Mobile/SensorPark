package com.sensorpark.data.firebase

import com.google.firebase.firestore.GeoPoint
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.ParkingLot
import com.sensorpark.domain.model.Schedule
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import java.time.LocalTime
import java.time.format.DateTimeParseException

// Conversión de documentos de Firestore a modelos de domain. Recibe el mapa de datos y no el
// DocumentSnapshot para poder probarla sin Firestore. Un campo faltante o con otro tipo no rompe
// la lectura: toma un valor por defecto. Sin nombre o sin ubicación, el documento se descarta.

internal fun parkingLotFrom(id: String, data: Map<String, Any?>): ParkingLot? {
    val name = data[ParkingLotFields.NAME] as? String ?: return null
    val location = data[ParkingLotFields.LOCATION] as? GeoPoint ?: return null

    return ParkingLot(
        id = id,
        name = name,
        address = data[ParkingLotFields.ADDRESS] as? String ?: "",
        location = Coordinates(location.latitude, location.longitude),
        schedule = scheduleFrom(data[ParkingLotFields.SCHEDULE]),
        toleranceMinutes = int(data[ParkingLotFields.TOLERANCE_MINUTES]),
        geofenceRadiusMeters = int(data[ParkingLotFields.GEOFENCE_RADIUS_METERS]),
        hourlyRate = int(data[ParkingLotFields.HOURLY_RATE]),
        adminUids = (data[ParkingLotFields.ADMIN_UIDS] as? List<*>).orEmpty().filterIsInstance<String>(),
        capacity = int(data[ParkingLotFields.CAPACITY]),
        freeSpots = int(data[ParkingLotFields.FREE_SPOTS]),
        capacityByType = countsByType(data[ParkingLotFields.CAPACITY_BY_TYPE]),
        freeSpotsByType = countsByType(data[ParkingLotFields.FREE_SPOTS_BY_TYPE]),
    )
}

/** Un lugar. Si el tipo o el estado no son conocidos, se descarta. */
internal fun spotFrom(id: String, data: Map<String, Any?>): Spot? {
    val type = SpotType.from(data[SpotFields.TYPE]) ?: return null
    val status = SpotStatus.from(data[SpotFields.STATUS]) ?: return null
    return Spot(
        id = id,
        code = data[SpotFields.CODE] as? String ?: id,
        type = type,
        status = status,
        priorityCategory = (data[SpotFields.PRIORITY_CATEGORY] as? String)?.let { Category.from(it) },
    )
}

private fun scheduleFrom(value: Any?): Schedule? {
    val map = value as? Map<*, *> ?: return null
    val opensAt = map[ParkingLotFields.OPENS_AT] as? String ?: return null
    val closesAt = map[ParkingLotFields.CLOSES_AT] as? String ?: return null
    return try {
        Schedule(LocalTime.parse(opensAt), LocalTime.parse(closesAt))
    } catch (e: DateTimeParseException) {
        null
    }
}

/** Firestore devuelve los enteros como Long. */
private fun int(value: Any?): Int = (value as? Number)?.toInt() ?: 0

private fun counts(value: Any?): Map<String, Int> =
    (value as? Map<*, *>).orEmpty()
        .mapNotNull { (key, count) -> (key as? String)?.let { it to int(count) } }
        .toMap()

/** Un tipo de lugar que la app no conoce (por ejemplo, agregado en una versión más nueva) se ignora. */
private fun countsByType(value: Any?): Map<SpotType, Int> =
    counts(value).mapNotNull { (type, count) -> SpotType.from(type)?.let { it to count } }.toMap()
