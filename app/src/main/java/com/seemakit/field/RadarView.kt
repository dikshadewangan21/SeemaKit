package com.seemakit.field

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * Stakeout Target Radar HUD for SeemaKit.
 * Visually guides the surveyor to relocated corner pegs with concentric range rings,
 * directional needle, and instant target lock feedback (< 0.5m).
 */
@Composable
fun StakeoutRadar(
    distanceMeters: Double,
    bearingDegrees: Double,
    modifier: Modifier = Modifier
) {
    val isOnTarget = distanceMeters <= 0.5

    // Pulsing target lock animation when surveyor arrives on peg
    val infiniteTransition = rememberInfiniteTransition(label = "radar_lock")
    val lockRadius by infiniteTransition.animateFloat(
        initialValue = 20f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lock_radius"
    )
    val lockAlpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lock_alpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B1926))
            .border(1.dp, Color(0xFF1E3A52), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val maxR = min(cx, cy) - 20f

            // Concentric Range Rings
            val r10 = maxR
            val r5 = maxR * 0.6f
            val r1 = maxR * 0.25f

            val ringColor = Color(0xFF1E3A52)
            drawCircle(color = ringColor, radius = r10, center = Offset(cx, cy), style = Stroke(width = 2f))
            drawCircle(color = ringColor, radius = r5, center = Offset(cx, cy), style = Stroke(width = 2f))
            drawCircle(
                color = if (isOnTarget) Color(0xFF138808) else ringColor,
                radius = r1,
                center = Offset(cx, cy),
                style = Stroke(width = if (isOnTarget) 4f else 2f)
            )

            // Crosshair lines
            drawLine(color = ringColor, start = Offset(cx - maxR, cy), end = Offset(cx + maxR, cy), strokeWidth = 1.5f)
            drawLine(color = ringColor, start = Offset(cx, cy - maxR), end = Offset(cx, cy + maxR), strokeWidth = 1.5f)

            // Ring distance labels
            val ringPaint = Paint().apply {
                color = Color(0xFF64748B).toArgb()
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
            }
            drawContext.canvas.nativeCanvas.drawText("10m", cx + r10 - 35f, cy - 8f, ringPaint)
            drawContext.canvas.nativeCanvas.drawText("5m", cx + r5 - 28f, cy - 8f, ringPaint)
            drawContext.canvas.nativeCanvas.drawText("1m", cx + r1 - 25f, cy - 8f, ringPaint)

            // North label on top
            drawContext.canvas.nativeCanvas.drawText(
                "N",
                cx,
                cy - maxR - 4f,
                Paint().apply {
                    color = Color(0xFF94A3B8).toArgb()
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }
            )

            // Directional pointer needle
            val needleColor = when {
                isOnTarget -> Color(0xFF138808)
                distanceMeters <= 5.0 -> Color(0xFFE66710) // Saffron close approach
                else -> Color(0xFF0284C7)                  // Sky blue search needle
            }

            rotate(degrees = bearingDegrees.toFloat(), pivot = Offset(cx, cy)) {
                // Arrow pointing in the direction of the target bearing
                val arrowPath = Path().apply {
                    moveTo(cx, cy - maxR + 10f)
                    lineTo(cx - 14f, cy - maxR + 38f)
                    lineTo(cx, cy - maxR + 28f)
                    lineTo(cx + 14f, cy - maxR + 38f)
                    close()
                }
                drawPath(arrowPath, color = needleColor)

                // Line connecting center to arrow
                drawLine(
                    color = needleColor.copy(alpha = 0.6f),
                    start = Offset(cx, cy),
                    end = Offset(cx, cy - maxR + 28f),
                    strokeWidth = 3f
                )
            }

            // Center target pin or arrival lock
            if (isOnTarget) {
                // Expanding green shockwave
                drawCircle(
                    color = Color(0xFF138808).copy(alpha = lockAlpha),
                    radius = r1 * (lockRadius / 25f),
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0xFF138808),
                    radius = 16f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.White,
                    radius = 7f,
                    center = Offset(cx, cy)
                )
            } else {
                drawCircle(
                    color = Color(0xFFE66710),
                    radius = 10f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}
