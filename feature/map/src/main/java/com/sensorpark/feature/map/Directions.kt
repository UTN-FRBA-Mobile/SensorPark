package com.sensorpark.feature.map

import com.sensorpark.domain.model.Coordinates
import java.util.Locale

/**
 * El link de "Cómo llegar". Es la URL universal de Google Maps: con la app instalada la abre en
 * modo navegación, y si no, abre el navegador. Se arma con punto decimal aunque el teléfono esté en
 * español, que usaría coma.
 */
internal fun directionsUrl(destination: Coordinates): String =
    String.format(
        Locale.ROOT,
        "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=driving",
        destination.latitude,
        destination.longitude,
    )
