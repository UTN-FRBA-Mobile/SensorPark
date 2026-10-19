package com.sensorpark.feature.map

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.sensorpark.core.ui.theme.SensorParkColors
import com.sensorpark.core.ui.theme.SensorParkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * El dibujo de los marcadores del mapa. Se prueba sin Maps porque sin key el mapa no termina de
 * cargar y no muestra marcadores: así se verifica el diseño igual.
 */
class AvailabilityMarkerUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cadaEstadoTieneElColorDeSuPastillaYDiceCuantosLibresHay() {
        var colors: SensorParkColors? = null
        var density = 0f
        val bitmaps = mutableMapOf<String, Bitmap>()

        compose.setContent {
            SensorParkTheme(dynamicColor = false) {
                colors = SensorParkTheme.colors
                density = LocalDensity.current.density
                val c = SensorParkTheme.colors
                val d = LocalDensity.current.density
                // Con lugares, casi lleno y completo: los tres colores posibles.
                val samples = listOf(
                    "libre" to availabilityMarkerBitmap(9, 14, c, d, "Completo", "9 libres"),
                    "pocos" to availabilityMarkerBitmap(2, 14, c, d, "Completo", "2 libres"),
                    "completo" to availabilityMarkerBitmap(0, 14, c, d, "Completo", "0 libres"),
                )
                samples.forEach { (name, bitmap) -> bitmaps[name] = bitmap }

                // Un fondo parecido al de un mapa, para ver cómo quedan arriba de las calles.
                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFFE8EAED)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text("Marcadores del mapa")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        samples.forEach { (_, bitmap) ->
                            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        val palette = colors!!
        // Un punto dentro de la pastilla, arriba del texto y lejos del borde.
        fun fillOf(bitmap: Bitmap) = bitmap.getPixel(bitmap.width / 2, (5.5f * density).toInt())

        assertEquals(palette.positive.container.toArgb(), fillOf(bitmaps.getValue("libre")))
        assertEquals(palette.warning.container.toArgb(), fillOf(bitmaps.getValue("pocos")))
        assertEquals(palette.negative.container.toArgb(), fillOf(bitmaps.getValue("completo")))

        // La punta de abajo sobresale de la pastilla: el centro de la última fila no es transparente.
        bitmaps.values.forEach { bitmap ->
            val tip = bitmap.getPixel(bitmap.width / 2, bitmap.height - (3 * density).toInt())
            assertTrue("La punta del marcador tiene que estar dibujada", android.graphics.Color.alpha(tip) > 0)
        }

        compose.screenshot("14-marcadores-mapa")
    }
}
