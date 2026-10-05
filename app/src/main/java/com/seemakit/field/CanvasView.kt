package com.seemakit.field

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * 2D Interactive Cadastral Map Visualizer for SeemaKit.
 * Displays field boundary polygon, numbered corner pegs with distances,
 * a North orientation compass, and real-time live rover location marker.
 */
@Composable
fun ParcelCanvas(
    corners: List<Corner>,
    roverFix: Fix?,
    modifier: Modifier = Modifier
) {
    // Pulsing animation for live rover marker
    val infiniteTransition = rememberInfiniteTransition(label = "rover_pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF1F8F1))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val canvasW = size.width
            val canvasH = size.height

            // 1. Draw subtle coordinate grid background
            val gridSpacing = 40.dp.toPx()
            var gx = 0f
            while (gx < canvasW) {
                drawLine(
                    color = Color(0xFFD7E5D7),
                    start = Offset(gx, 0f),
                    end = Offset(gx, canvasH),
                    strokeWidth = 1f
                )
                gx += gridSpacing
            }
            var gy = 0f
            while (gy < canvasH) {
                drawLine(
                    color = Color(0xFFD7E5D7),
                    start = Offset(0f, gy),
                    end = Offset(canvasW, gy),
                    strokeWidth = 1f
                )
                gy += gridSpacing
            }

            // 2. Draw North Arrow in top-right
            drawNorthArrow(canvasW - 25.dp.toPx(), 30.dp.toPx())

            if (corners.isEmpty() && roverFix == null) {
                drawContext.canvas.nativeCanvas.drawText(
                    "Waiting for GPS or corners...",
                    canvasW / 2,
                    canvasH / 2,
                    Paint().apply {
                        color = android.graphics.Color.GRAY
                        textAlign = Paint.Align.CENTER
                        textSize = 34f
                        typeface = Typeface.DEFAULT_BOLD
                    }
                )
                return@Canvas
            }

            // Collect all points to compute bounding box
            val allPts = mutableListOf<Pair<Double, Double>>()
            corners.forEach { allPts.add(it.lat to it.lon) }
            roverFix?.let { allPts.add(it.lat to it.lon) }

            val minLat = allPts.minOf { it.first }
            val maxLat = allPts.maxOf { it.first }
            val minLon = allPts.minOf { it.second }
            val maxLon = allPts.maxOf { it.second }

            val midLat = (minLat + maxLat) / 2.0
            val cosLat = cos(Math.toRadians(midLat))
            val dLonDeg = max(maxLon - minLon, 0.00008)
            val dLatDeg = max(maxLat - minLat, 0.00008)
            val effLonSpan = dLonDeg * cosLat
            val effLatSpan = dLatDeg

            val pad = 40f
            val usableW = max(canvasW - pad * 2, 10f)
            val usableH = max(canvasH - pad * 2, 10f)

            val scale = min(usableW / effLonSpan, usableH / effLatSpan)
            val offsetX = pad + (usableW - effLonSpan * scale) / 2.0
            val offsetY = pad + (usableH - effLatSpan * scale) / 2.0

            fun project(lat: Double, lon: Double): Offset {
                val px = (offsetX + (lon - minLon) * cosLat * scale).toFloat()
                val py = (canvasH - (offsetY + (lat - minLat) * scale)).toFloat()
                return Offset(px, py)
            }

            // 3. Draw Polygon Boundary if corners >= 2
            if (corners.size >= 2) {
                val path = Path()
                val firstPt = project(corners[0].lat, corners[0].lon)
                path.moveTo(firstPt.x, firstPt.y)
                for (i in 1 until corners.size) {
                    val pt = project(corners[i].lat, corners[i].lon)
                    path.lineTo(pt.x, pt.y)
                }

                if (corners.size >= 3) {
                    path.close()
                    // Fill parcel area with semi-transparent green
                    drawPath(
                        path = path,
                        color = Color(0x354CAF50)
                    )
                }

                // Draw perimeter boundary line
                drawPath(
                    path = path,
                    color = Color(0xFF2E7D32),
                    style = Stroke(width = 4.dp.toPx())
                )

                // 4. Draw Distance annotations along each boundary segment
                val textPaint = Paint().apply {
                    color = Color(0xFF1B5E20).toArgb()
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }

                for (i in corners.indices) {
                    val j = (i + 1) % corners.size
                    if (j == 0 && corners.size < 3) break // don't close segment if < 3
                    val p1 = project(corners[i].lat, corners[i].lon)
                    val p2 = project(corners[j].lat, corners[j].lon)
                    val d = Geo.dist(corners[i].lat, corners[i].lon, corners[j].lat, corners[j].lon)
                    val midX = (p1.x + p2.x) / 2
                    val midY = (p1.y + p2.y) / 2 - 8f
                    drawContext.canvas.nativeCanvas.drawText("${"%.1f".format(d)}m", midX, midY, textPaint)
                }
            }

            // 5. Draw Numbered Corner Pegs
            corners.forEach { c ->
                val pt = project(c.lat, c.lon)
                // Outer ring
                drawCircle(
                    color = if (c.isGcp) Color(0xFFE65100) else Color(0xFF1B5E20),
                    radius = 14.dp.toPx(),
                    center = pt
                )
                // Inner white circle
                drawCircle(
                    color = Color.White,
                    radius = 11.dp.toPx(),
                    center = pt
                )
                // Text label
                drawContext.canvas.nativeCanvas.drawText(
                    "#${c.seq}",
                    pt.x,
                    pt.y + 10f,
                    Paint().apply {
                        color = if (c.isGcp) Color(0xFFE65100).toArgb() else Color(0xFF1B5E20).toArgb()
                        textSize = 26f
                        typeface = Typeface.DEFAULT_BOLD
                        textAlign = Paint.Align.CENTER
                    }
                )
            }

            // 6. Draw Live Rover Marker
            roverFix?.let { rf ->
                val rpt = project(rf.lat, rf.lon)

                // Pulsing wave
                drawCircle(
                    color = Color(0xFF0288D1).copy(alpha = pulseAlpha),
                    radius = pulseRadius,
                    center = rpt
                )

                // Solid center pin
                drawCircle(
                    color = Color(0xFF0288D1),
                    radius = 9.dp.toPx(),
                    center = rpt
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = rpt
                )

                // Rover tag
                drawContext.canvas.nativeCanvas.drawText(
                    "ROVER",
                    rpt.x,
                    rpt.y - 18.dp.toPx(),
                    Paint().apply {
                        color = Color(0xFF01579B).toArgb()
                        textSize = 22f
                        typeface = Typeface.DEFAULT_BOLD
                        textAlign = Paint.Align.CENTER
                    }
                )
            }
        }
    }
}

/**
 * Draws a clean surveyor North Arrow compass on the Canvas.
 */
private fun DrawScope.drawNorthArrow(cx: Float, cy: Float) {
    val northPath = Path().apply {
        moveTo(cx, cy - 24f)
        lineTo(cx - 10f, cy + 12f)
        lineTo(cx, cy + 6f)
        close()
    }
    val southPath = Path().apply {
        moveTo(cx, cy - 24f)
        lineTo(cx + 10f, cy + 12f)
        lineTo(cx, cy + 6f)
        close()
    }
    drawPath(northPath, color = Color(0xFFD32F2F)) // Red North needle
    drawPath(southPath, color = Color(0xFF757575)) // Grey needle

    drawContext.canvas.nativeCanvas.drawText(
        "N",
        cx,
        cy - 30f,
        Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
    )
}
