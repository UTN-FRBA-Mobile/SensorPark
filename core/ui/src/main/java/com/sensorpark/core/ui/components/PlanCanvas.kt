package com.sensorpark.core.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sensorpark.core.ui.R
import com.sensorpark.core.ui.theme.SensorParkTheme
import com.sensorpark.domain.model.Category
import com.sensorpark.domain.model.ParkingLevel
import com.sensorpark.domain.model.Spot
import com.sensorpark.domain.model.SpotPlanPosition
import com.sensorpark.domain.model.SpotStatus
import com.sensorpark.domain.model.SpotType
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Resultado de comprimir y codificar en Base64 la imagen del plano de un piso. */
data class EncodedPlanImage(
    val base64: String,
    val width: Int,
    val height: Int,
)

/**
 * Lee una imagen de [uri], la escala para que su lado mayor no supere [maxDimension] px y la
 * comprime en JPEG para guardarla en Firestore dentro del documento del nivel.
 */
fun encodePlanImage(
    context: Context,
    uri: Uri,
    maxDimension: Int = 1200,
    quality: Int = 78,
): EncodedPlanImage? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        }
        if (decoded == null) {
            null
        } else {
            val largest = max(decoded.width, decoded.height)
            val scaled = if (largest > maxDimension) {
                val ratio = maxDimension.toFloat() / largest.toFloat()
                val targetW = (decoded.width * ratio).roundToInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else {
                decoded
            }
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            EncodedPlanImage(base64 = base64, width = scaled.width, height = scaled.height)
        }
    }
} catch (_: Exception) {
    null
}

/** Decodifica la imagen Base64 de un nivel. Si no es una imagen válida, devuelve `null`. */
fun decodePlanImageBitmap(base64: String): ImageBitmap? = try {
    if (base64.isBlank()) {
        null
    } else {
        val clean = base64.substringAfter("base64,", base64)
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
} catch (_: Exception) {
    null
}

private val PlanBoxShape = RoundedCornerShape(16.dp)
private val PinSize = 34.dp
private val PlanArLocale = Locale.forLanguageTag("es-AR")

/**
 * Visor e interactivo del plano de un piso ([level]). Muestra la imagen del nivel y, encima, un
 * círculo con el código por cada lugar ubicado en ese piso.
 *
 * - Para el conductor (`editable = false`): permite hacer zoom y tocar un círculo libre para reservarlo.
 * - Para el administrador (`editable = true`): además permite tocar la imagen ([onTapImage]) para
 *   ubicar el lugar seleccionado y arrastrar los círculos ([onMoveSpot]) para reubicarlos.
 */
@Composable
fun ParkingLevelPlanBox(
    level: ParkingLevel,
    spots: List<Spot>,
    modifier: Modifier = Modifier,
    selectedSpotId: String? = null,
    editable: Boolean = false,
    isSpotEligible: (Spot) -> Boolean = { true },
    onSpotClick: (Spot) -> Unit = {},
    onTapImage: ((x: Float, y: Float) -> Unit)? = null,
    onMoveSpot: ((Spot, x: Float, y: Float) -> Unit)? = null,
) {
    val bitmap = remember(level.imageBase64) { decodePlanImageBitmap(level.imageBase64) }
    val aspect = remember(level.imageWidth, level.imageHeight) {
        (level.imageWidth.toFloat() / level.imageHeight.coerceAtLeast(1).toFloat()).coerceIn(0.5f, 2.2f)
    }

    var scale by remember(level.id) { mutableFloatStateOf(1f) }
    var pan by remember(level.id) { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val currentOnTapImage by rememberUpdatedState(onTapImage)
    val currentOnSpotClick by rememberUpdatedState(onSpotClick)
    val currentOnMoveSpot by rememberUpdatedState(onMoveSpot)
    val pinRadiusPx = with(LocalDensity.current) { (PinSize / 2).toPx() }
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(PlanBoxShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, PlanBoxShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PlanBoxShape)
            .onSizeChanged { boxSize = it },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(level.id, editable) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val newScale = (scale * zoomChange).coerceIn(1f, 3.5f)
                        val maxX = (size.width * (newScale - 1f)) / 2f
                        val maxY = (size.height * (newScale - 1f)) / 2f
                        scale = newScale
                        pan = if (newScale <= 1.01f) {
                            Offset.Zero
                        } else {
                            Offset(
                                x = (pan.x + panChange.x).coerceIn(-maxX, maxX),
                                y = (pan.y + panChange.y).coerceIn(-maxY, maxY),
                            )
                        }
                    }
                }
                .then(
                    if (editable && currentOnTapImage != null) {
                        Modifier.pointerInput(level.id, scale, pan, boxSize) {
                            detectTapGestures { tapOffset ->
                                if (boxSize.width > 0 && boxSize.height > 0) {
                                    val centerX = boxSize.width / 2f
                                    val centerY = boxSize.height / 2f
                                    val contentX = (tapOffset.x - centerX - pan.x) / scale + centerX
                                    val contentY = (tapOffset.y - centerY - pan.y) / scale + centerY
                                    val normX = (contentX / boxSize.width).coerceIn(0.04f, 0.96f)
                                    val normY = (contentY / boxSize.height).coerceIn(0.04f, 0.96f)
                                    currentOnTapImage?.invoke(normX, normY)
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                },
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = level.name,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Fondo de respaldo cuando la imagen es sintética o está en pruebas.
                Canvas(Modifier.fillMaxSize()) {
                    val step = size.width / 8f
                    var x = step
                    while (x < size.width) {
                        drawLine(gridLineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += step
                    }
                    var y = step
                    while (y < size.height) {
                        drawLine(gridLineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += step
                    }
                }
            }

            if (boxSize.width > 0 && boxSize.height > 0) {
                spots.forEach { spot ->
                    val pos = spot.planPosition ?: return@forEach
                    key(spot.id) {
                        SpotPlanCirclePin(
                            spot = spot,
                            xFraction = pos.x,
                            yFraction = pos.y,
                            boxSize = boxSize,
                            pinRadiusPx = pinRadiusPx,
                            selected = spot.id == selectedSpotId,
                            editable = editable,
                            eligible = editable || isSpotEligible(spot),
                            onClick = { currentOnSpotClick(spot) },
                            onDrag = { newX, newY -> currentOnMoveSpot?.invoke(spot, newX, newY) },
                        )
                    }
                }
            }
        }

        if (scale > 1.05f) {
            Surface(
                onClick = {
                    scale = 1f
                    pan = Offset.Zero
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.plan_reset_zoom),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SpotPlanCirclePin(
    spot: Spot,
    xFraction: Float,
    yFraction: Float,
    boxSize: IntSize,
    pinRadiusPx: Float,
    selected: Boolean,
    editable: Boolean,
    eligible: Boolean,
    onClick: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    val statusLabel = spot.status.label()
    val typeLabel = if (spot.type != SpotType.STANDARD) spot.type.label() else null
    val baseDescription = stringResource(R.string.plan_spot_content_description, spot.code, statusLabel.lowercase(PlanArLocale))
    val description = remember(baseDescription, typeLabel) {
        buildString {
            append(baseDescription)
            if (typeLabel != null) append(", ").append(typeLabel)
        }
    }

    var dragX by remember(xFraction) { mutableFloatStateOf(xFraction) }
    var dragY by remember(yFraction) { mutableFloatStateOf(yFraction) }
    val currentOnDrag by rememberUpdatedState(onDrag)

    SpotPlanPinMarker(
        spot = spot,
        selected = selected,
        eligible = eligible,
        modifier = Modifier
            .offset {
                val px = (dragX * boxSize.width - pinRadiusPx).roundToInt()
                val py = (dragY * boxSize.height - pinRadiusPx).roundToInt()
                IntOffset(px, py)
            }
            .clickable(
                enabled = editable || spot.status == SpotStatus.FREE,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (editable && boxSize.width > 0 && boxSize.height > 0) {
                    Modifier.pointerInput(spot.id, boxSize) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            dragX = (dragX + dragAmount.x / boxSize.width).coerceIn(0.04f, 0.96f)
                            dragY = (dragY + dragAmount.y / boxSize.height).coerceIn(0.04f, 0.96f)
                            currentOnDrag(dragX, dragY)
                        }
                    }
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    )
}

/**
 * Marcador circular de un lugar en el plano con su número centrado y, cuando no es estándar, un
 * pequeño badge superpuesto en la esquina superior derecha indicando su categoría.
 */
@Composable
fun SpotPlanPinMarker(
    spot: Spot,
    selected: Boolean,
    modifier: Modifier = Modifier,
    eligible: Boolean = true,
) {
    val statusColor = spotVisualColor(
        status = spot.status,
        type = spot.type,
        colors = SensorParkTheme.colors,
        eligible = eligible,
    )
    val isBlockedPriority = !eligible && spot.status == SpotStatus.FREE && spot.type == SpotType.PRIORITY
    val borderStroke = if (selected) 3.dp else 2.dp
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else statusColor.content
    val badge = spot.type.planBadgeStyle(dimmed = isBlockedPriority)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(PinSize),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(statusColor.container, CircleShape)
                .then(
                    if (isBlockedPriority) {
                        Modifier.drawWithContent {
                            drawContent()
                            val inset = size.width * 0.16f
                            drawLine(
                                color = statusColor.content,
                                start = Offset(inset, size.height - inset),
                                end = Offset(size.width - inset, inset),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .border(borderStroke, borderColor, CircleShape),
        ) {
            Text(
                text = spot.code,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                ),
                color = statusColor.content,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }

        if (badge != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(15.dp)
                    .background(badge.backgroundColor, CircleShape)
                    .border(1.dp, Color.White, CircleShape),
            ) {
                Icon(
                    imageVector = badge.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

private data class SpotTypeBadgeStyle(
    val backgroundColor: Color,
    val icon: ImageVector,
)

private val AccessibleBadgeColor = Color(0xFF0288D1)
private val BlockedBadgeColor = Color(0xFF607D8B)
private val ElectricBadgeColor = Color(0xFFFFA000)
private val MotorcycleBadgeColor = Color(0xFF7B1FA2)

private fun SpotType.planBadgeStyle(dimmed: Boolean = false): SpotTypeBadgeStyle? = when (this) {
    SpotType.STANDARD -> null
    SpotType.PRIORITY -> SpotTypeBadgeStyle(
        backgroundColor = if (dimmed) BlockedBadgeColor else AccessibleBadgeColor,
        icon = Icons.AutoMirrored.Filled.Accessible,
    )
    SpotType.EV_CHARGING -> SpotTypeBadgeStyle(
        backgroundColor = ElectricBadgeColor,
        icon = Icons.Filled.Bolt,
    )
    SpotType.MOTORCYCLE -> SpotTypeBadgeStyle(
        backgroundColor = MotorcycleBadgeColor,
        icon = Icons.Filled.TwoWheeler,
    )
}

@Preview(name = "Marcadores por categoría - Claro", showBackground = true)
@Preview(
    name = "Marcadores por categoría - Oscuro",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun SpotPlanPinMarkerPreview() {
    val samplePos = SpotPlanPosition(levelId = "pb", x = 0.5f, y = 0.5f)
    val previewSpots = listOf(
        Spot(id = "1", code = "01", status = SpotStatus.FREE, type = SpotType.STANDARD, planPosition = samplePos),
        Spot(
            id = "2",
            code = "02",
            status = SpotStatus.FREE,
            type = SpotType.PRIORITY,
            priorityCategory = Category.REDUCED_MOBILITY,
            planPosition = samplePos,
        ),
        Spot(id = "3", code = "03", status = SpotStatus.FREE, type = SpotType.EV_CHARGING, planPosition = samplePos),
        Spot(id = "4", code = "04", status = SpotStatus.FREE, type = SpotType.MOTORCYCLE, planPosition = samplePos),
        Spot(id = "5", code = "05", status = SpotStatus.OCCUPIED, type = SpotType.PRIORITY, planPosition = samplePos),
    )

    SensorParkTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            previewSpots.forEachIndexed { index, spot ->
                SpotPlanPinMarker(
                    spot = spot,
                    selected = index == 1,
                )
            }
        }
    }
}
