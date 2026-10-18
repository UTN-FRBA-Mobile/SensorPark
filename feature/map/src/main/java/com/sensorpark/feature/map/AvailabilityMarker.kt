package com.sensorpark.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.sensorpark.core.ui.theme.SensorParkColors
import com.sensorpark.core.ui.theme.StatusColor

/**
 * El marcador de un estacionamiento: una pastilla con los lugares libres, en los mismos colores que
 * la de la lista (verde si hay, ámbar si queda un cuarto o menos, rojo si está completo), con una
 * punta abajo que marca el lugar exacto.
 *
 * Se dibuja a mano con Canvas en vez de con `MarkerComposable`: ese arma una composición de Compose
 * aparte por cada marcador, y con la versión de Compose del proyecto revienta al desmontarse, o sea
 * cada vez que se sale de la pestaña Buscar.
 *
 * Tiene que llamarse una vez que el mapa cargó (dentro del contenido de `GoogleMap`): antes de eso
 * `BitmapDescriptorFactory` no está inicializada.
 */
internal fun availabilityMarker(
    free: Int,
    capacity: Int,
    colors: SensorParkColors,
    density: Float,
    fullLabel: String,
    freeLabel: String,
): BitmapDescriptor =
    BitmapDescriptorFactory.fromBitmap(availabilityMarkerBitmap(free, capacity, colors, density, fullLabel, freeLabel))

/**
 * El dibujo del marcador, separado de Maps para poder probarlo sin key y sin mapa cargado: sólo
 * usa Canvas.
 */
internal fun availabilityMarkerBitmap(
    free: Int,
    capacity: Int,
    colors: SensorParkColors,
    density: Float,
    fullLabel: String,
    freeLabel: String,
): Bitmap {
    val status: StatusColor = when {
        free <= 0 -> colors.negative
        free * 4 <= capacity -> colors.warning
        else -> colors.positive
    }
    val text = if (free <= 0) fullLabel else freeLabel

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = status.content.toArgb()
        textSize = 14 * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val padH = 12 * density
    val padV = 6 * density
    val pointer = 7 * density
    val outline = 1.5f * density

    val textWidth = textPaint.measureText(text)
    val metrics = textPaint.fontMetrics
    val textHeight = metrics.descent - metrics.ascent
    val width = (textWidth + padH * 2 + outline * 2).toInt()
    val bodyHeight = textHeight + padV * 2
    val height = (bodyHeight + pointer + outline * 2).toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val body = RectF(outline, outline, width - outline, bodyHeight + outline)
    val radius = bodyHeight / 2

    // La punta, centrada abajo: es la que toca el punto del mapa.
    val tip = Path().apply {
        moveTo(width / 2f - pointer, body.bottom - 1)
        lineTo(width / 2f, body.bottom + pointer)
        lineTo(width / 2f + pointer, body.bottom - 1)
        close()
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = status.container.toArgb() }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = status.content.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = outline
    }
    canvas.drawRoundRect(body, radius, radius, fill)
    canvas.drawRoundRect(body, radius, radius, border)
    canvas.drawPath(tip, fill)

    canvas.drawText(text, body.left + padH, body.top + padV - metrics.ascent, textPaint)
    return bitmap
}
