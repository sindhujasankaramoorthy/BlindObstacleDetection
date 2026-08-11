package com.example.blindobstacledetection.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.blindobstacledetection.models.DetectedObject
import com.example.blindobstacledetection.ui.theme.ColorClose
import com.example.blindobstacledetection.ui.theme.ColorFar
import com.example.blindobstacledetection.ui.theme.ColorMedium
import com.example.blindobstacledetection.ui.theme.ColorVeryClose

@Composable
fun BoundingBoxOverlay(
    detectedObjects: List<DetectedObject>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Draw 3-Zone Guide Lines (Left, Center, Right)
        val zone1X = width / 3f
        val zone2X = (2 * width) / 3f
        val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(zone1X, 0f),
            end = Offset(zone1X, height),
            strokeWidth = 2.dp.toPx(),
            pathEffect = dashPathEffect
        )

        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(zone2X, 0f),
            end = Offset(zone2X, height),
            strokeWidth = 2.dp.toPx(),
            pathEffect = dashPathEffect
        )

        for (obj in detectedObjects) {
            val normBbox = obj.normBbox ?: continue
            if (normBbox.size < 4) continue

            val left = normBbox[0] * width
            val top = normBbox[1] * height
            val right = normBbox[2] * width
            val bottom = normBbox[3] * height

            val boxWidth = right - left
            val boxHeight = bottom - top

            val color = when (obj.distance) {
                "Very Close" -> ColorVeryClose
                "Close" -> ColorClose
                "Medium" -> ColorMedium
                else -> ColorFar
            }

            // Draw bounding box
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                style = Stroke(width = 4.dp.toPx())
            )

            // Label background & Text using nativeCanvas
            val labelText = "${obj.name.uppercase()} (${obj.direction}) - ${obj.distance}"
            val textPaint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.WHITE
                this.textSize = 36f
                this.isFakeBoldText = true
                this.isAntiAlias = true
            }

            val bgPaint = android.graphics.Paint().apply {
                this.color = when (obj.distance) {
                    "Very Close" -> android.graphics.Color.argb(220, 255, 23, 68)
                    "Close" -> android.graphics.Color.argb(220, 255, 145, 0)
                    "Medium" -> android.graphics.Color.argb(220, 200, 180, 0)
                    else -> android.graphics.Color.argb(220, 0, 168, 82)
                }
                this.isAntiAlias = true
            }

            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(labelText, 0, labelText.length, textBounds)

            val textPadding = 12f
            val bgRect = android.graphics.RectF(
                left,
                (top - textBounds.height() - (textPadding * 2)).coerceAtLeast(0f),
                left + textBounds.width() + (textPadding * 2),
                top.coerceAtLeast(textBounds.height() + (textPadding * 2))
            )

            drawContext.canvas.nativeCanvas.drawRoundRect(bgRect, 8f, 8f, bgPaint)
            drawContext.canvas.nativeCanvas.drawText(
                labelText,
                left + textPadding,
                bgRect.bottom - textPadding,
                textPaint
            )
        }
    }
}
