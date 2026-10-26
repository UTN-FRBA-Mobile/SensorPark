package com.sensorpark.core.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sensorpark.domain.model.Coordinates
import java.net.URLEncoder
import java.util.Locale

private const val MAPS_PACKAGE = "com.google.android.apps.maps"

/** Los destinos de la ruta, del más específico al más general: Google Maps, `geo:` y la web. */
internal fun directionsUris(destination: Coordinates, label: String): List<String> {
    val point = String.format(Locale.US, "%.6f,%.6f", destination.latitude, destination.longitude)
    return listOf(
        "google.navigation:q=$point&mode=d",
        "geo:$point?q=$point(${URLEncoder.encode(label, "UTF-8").replace("+", "%20")})",
        "https://www.google.com/maps/dir/?api=1&destination=$point&travelmode=driving",
    )
}

/** Abre la ruta hasta [destination] con un Intent implícito. Devuelve false si ninguna app pudo. */
fun Context.openDirections(destination: Coordinates, label: String): Boolean {
    val uris = directionsUris(destination, label)
    uris.forEachIndexed { index, uri ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        if (index == 0) intent.setPackage(MAPS_PACKAGE)
        if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // Se prueba con la siguiente.
        }
    }
    return false
}
