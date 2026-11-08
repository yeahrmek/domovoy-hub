package ru.domovoy.panel

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance

/** Effects share the bitmap's square coordinates; the hardware never changes scale or position. */
@Composable
internal fun HumidifierWorkingEffects(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val mist = if (dark) Color(0xFFE8EDF2) else Color(0xFF748494)
    val light = MaterialTheme.colorScheme.primaryFixedDim
    Canvas(modifier) {
        val side = size.minDimension

        // Soft overlapping vapour above the lid, with a wider, fainter plume as it rises.
        fun vapour(x: Float, y: Float, radius: Float, flatten: Float, opacity: Float) {
            val center = Offset(side * x, side * y)
            scale(1f, flatten, center) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(mist.copy(alpha = opacity), mist.copy(alpha = 0f)), center, side * radius),
                    radius = side * radius,
                    center = center,
                )
            }
        }
        vapour(0.50f, 0.295f, 0.15f, 0.30f, 0.42f)
        vapour(0.42f, 0.255f, 0.12f, 0.40f, 0.34f)
        vapour(0.57f, 0.240f, 0.13f, 0.42f, 0.32f)
        vapour(0.48f, 0.210f, 0.12f, 0.43f, 0.26f)
        vapour(0.36f, 0.190f, 0.12f, 0.45f, 0.18f)
        vapour(0.61f, 0.175f, 0.14f, 0.40f, 0.17f)
        vapour(0.49f, 0.140f, 0.15f, 0.38f, 0.12f)

        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(light.copy(alpha = 0f), light.copy(alpha = 0.30f), light.copy(alpha = 0f)),
                startX = side * 0.315f,
                endX = side * 0.381f,
            ),
            topLeft = Offset(side * 0.315f, side * 0.526f),
            size = Size(side * 0.066f, side * 0.241f),
            cornerRadius = CornerRadius(side * 0.025f),
        )
        drawRoundRect(
            color = light,
            topLeft = Offset(side * 0.342f, side * 0.534f),
            size = Size(side * 0.012f, side * 0.221f),
            cornerRadius = CornerRadius(side * 0.006f),
        )
    }
}
