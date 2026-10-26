package com.sensorpark.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.sensorpark.core.ui.components.availabilityColor
import com.sensorpark.core.ui.theme.SensorParkColors
import com.sensorpark.core.ui.theme.StatusColor

/**
 * Íconos de los marcadores: un círculo con los lugares libres, del color de la disponibilidad.
 * Se guardan por color, cantidad y selección. Hay que pedirlos dentro del contenido de
 * `GoogleMap`, cuando la fábrica de íconos ya está lista.
 */
internal class MarkerIcons(private val density: Float, private val colors: SensorParkColors) {
    private data class Key(val color: StatusColor, val free: Int, val selected: Boolean)

    private val cache = HashMap<Key, BitmapDescriptor>()

    fun icon(free: Int, capacity: Int, selected: Boolean): BitmapDescriptor {
        val key = Key(availabilityColor(free, capacity, colors), free.coerceAtLeast(0), selected)
        return cache.getOrPut(key) {
            BitmapDescriptorFactory.fromBitmap(draw(key.free.toString(), key.color, selected))
        }
    }

    private fun draw(text: String, color: StatusColor, selected: Boolean): Bitmap {
        val size = ((if (selected) SELECTED_DP else NORMAL_DP) * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = (if (selected) 3.5f else 2.5f) * density
        val radius = size / 2f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.container.toArgb() }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.content.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        canvas.drawCircle(radius, radius, radius - stroke, fill)
        canvas.drawCircle(radius, radius, radius - stroke, border)

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.content.toArgb()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = size * 0.42f
        }
        canvas.drawText(text, radius, radius - (label.descent() + label.ascent()) / 2f, label)
        return bitmap
    }

    private companion object {
        const val NORMAL_DP = 44
        const val SELECTED_DP = 56
    }
}
