package com.mari.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class TimeValue(val hours: Int, val minutes: Int) {
    val totalMinutes: Int get() = hours * 60 + minutes

    fun format(): String = "%02d:%02d".format(hours, minutes)
}

/** 0h maps to top (270° in standard math). Full circle = 24h. */
fun timeToAngle(time: TimeValue): Float =
    (time.totalMinutes / (24f * 60f)) * 360f - 90f

/** Angle → snapped TimeValue (15-min steps). */
fun angleToTime(angleDeg: Float): TimeValue {
    val norm = ((angleDeg + 90f) % 360f + 360f) % 360f
    val totalMins = (((norm / 360f) * 24f * 60f) / 15f).roundToInt() * 15
    return TimeValue(hours = (totalMins / 60) % 24, minutes = totalMins % 60)
}

fun polarToOffset(angleDeg: Float, radius: Float, center: Offset): Offset {
    val rad = Math.toRadians(angleDeg.toDouble())
    return Offset(
        x = center.x + radius * cos(rad).toFloat(),
        y = center.y + radius * sin(rad).toFloat(),
    )
}

fun durationLabel(start: TimeValue, end: TimeValue): String {
    var diff = end.totalMinutes - start.totalMinutes
    if (diff < 0) diff += 24 * 60
    val h = diff / 60
    val m = diff % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

@Composable
fun CircularTimeRangePicker(
    modifier: Modifier = Modifier,
    initialStart: TimeValue = TimeValue(9, 0),
    initialEnd: TimeValue = TimeValue(17, 0),
    onRangeChanged: (start: TimeValue, end: TimeValue) -> Unit = { _, _ -> },
) {
    var startTime by remember { mutableStateOf(initialStart) }
    var endTime by remember { mutableStateOf(initialEnd) }
    var dragging by remember { mutableStateOf<String?>(null) }

    val trackColor = Color(0xFF1E1E2E)
    val arcStart = Color(0xFF4F8EF7)
    val arcEnd = Color(0xFFA78BFA)
    val handleBorder = Brush.linearGradient(listOf(arcStart, arcEnd))
    val handleFill = Color(0xFF13132A)
    val innerCircleColor = Color(0xFF0E0E1A)
    val tickMajor = Color(0xFF3A3A5C)
    val tickMinor = Color(0xFF252538)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            val boxSize = constraints.maxWidth.toFloat()
            val center = Offset(boxSize / 2f, boxSize / 2f)
            val trackRadius = boxSize * 0.42f
            val handleRadius = boxSize * 0.057f
            val innerRadius = boxSize * 0.335f

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val sAngle = timeToAngle(startTime)
                                val eAngle = timeToAngle(endTime)
                                val sPos = polarToOffset(sAngle, trackRadius, center)
                                val ePos = polarToOffset(eAngle, trackRadius, center)
                                val distToStart = (offset - sPos).getDistance()
                                val distToEnd = (offset - ePos).getDistance()
                                dragging = if (distToStart < distToEnd) "start" else "end"
                            },
                            onDrag = { change, _ ->
                                val pos = change.position
                                val dx = pos.x - center.x
                                val dy = pos.y - center.y
                                val angle = atan2(dy, dx) * (180f / PI.toFloat())
                                val time = angleToTime(angle)
                                if (dragging == "start") {
                                    startTime = time
                                    onRangeChanged(startTime, endTime)
                                } else {
                                    endTime = time
                                    onRangeChanged(startTime, endTime)
                                }
                            },
                            onDragEnd = { dragging = null },
                            onDragCancel = { dragging = null },
                        )
                    },
            ) {
                val startAngle = timeToAngle(startTime)
                val endAngle = timeToAngle(endTime)
                var sweepAngle = endAngle - startAngle
                if (sweepAngle < 0) sweepAngle += 360f

                drawCircle(
                    color = trackColor,
                    radius = trackRadius,
                    center = center,
                    style = Stroke(width = boxSize * 0.086f),
                )

                val tickCount = 96
                for (i in 0 until tickCount) {
                    val angle = Math.toRadians(((i.toFloat() / tickCount) * 360f - 90f).toDouble())
                    val isMajor = i % 4 == 0
                    val r1 = trackRadius - if (isMajor) boxSize * 0.042f else boxSize * 0.025f
                    val r2 = trackRadius - boxSize * 0.006f
                    val p1 = Offset(center.x + r1 * cos(angle).toFloat(), center.y + r1 * sin(angle).toFloat())
                    val p2 = Offset(center.x + r2 * cos(angle).toFloat(), center.y + r2 * sin(angle).toFloat())
                    drawLine(
                        color = if (isMajor) tickMajor else tickMinor,
                        start = p1,
                        end = p2,
                        strokeWidth = if (isMajor) 1.5f else 1f,
                    )
                }

                val arcBrush = Brush.sweepGradient(
                    colorStops = arrayOf(0f to arcStart, 1f to arcEnd),
                    center = center,
                )

                drawArc(
                    brush = arcBrush,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - trackRadius, center.y - trackRadius),
                    size = Size(trackRadius * 2, trackRadius * 2),
                    style = Stroke(width = boxSize * 0.086f, cap = StrokeCap.Round),
                    alpha = 0.3f,
                )

                drawArc(
                    brush = arcBrush,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - trackRadius, center.y - trackRadius),
                    size = Size(trackRadius * 2, trackRadius * 2),
                    style = Stroke(width = boxSize * 0.072f, cap = StrokeCap.Round),
                )

                drawCircle(color = innerCircleColor, radius = innerRadius, center = center)
                drawCircle(
                    color = Color(0xFF1A1A2E),
                    radius = innerRadius,
                    center = center,
                    style = Stroke(width = 1f),
                )

                val startPos = polarToOffset(startAngle, trackRadius, center)
                val endPos = polarToOffset(endAngle, trackRadius, center)

                drawCircle(color = handleFill, radius = handleRadius, center = startPos)
                drawCircle(brush = handleBorder, radius = handleRadius, center = startPos, style = Stroke(width = 2.5f))

                drawCircle(color = handleFill, radius = handleRadius, center = endPos)
                drawCircle(brush = handleBorder, radius = handleRadius, center = endPos, style = Stroke(width = 2.5f))

                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(255, 74, 74, 106)
                        textSize = boxSize * 0.034f
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.MONOSPACE
                        isAntiAlias = true
                    }
                    listOf(0, 6, 12, 18).forEach { h ->
                        val angle = (h / 24f) * 360f - 90f
                        val pos = polarToOffset(angle, trackRadius - boxSize * 0.086f, center)
                        canvas.nativeCanvas.drawText(
                            h.toString(),
                            pos.x,
                            pos.y - (paint.descent() + paint.ascent()) / 2,
                            paint,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = "START", fontSize = 10.sp, letterSpacing = 2.sp, color = Color(0xFF5A5A8A), fontWeight = FontWeight.Light)
                Text(text = startTime.format(), fontSize = 22.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.width(56.dp).height(1.dp).background(Color(0xFF2A2A4A)))
                Spacer(Modifier.height(4.dp))
                Text(text = "END", fontSize = 10.sp, letterSpacing = 2.sp, color = Color(0xFF5A5A8A), fontWeight = FontWeight.Light)
                Text(text = endTime.format(), fontSize = 22.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Duration: ${durationLabel(startTime, endTime)}",
            fontSize = 14.sp,
            color = Color(0xFF7EB8FF),
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                Triple("Start", startTime, Color(0xFF4F8EF7)),
                Triple("End", endTime, Color(0xFFA78BFA)),
            ).forEach { (label, time, color) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF13132A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = label.uppercase(), fontSize = 9.sp, letterSpacing = 2.sp, color = Color(0xFF5A5A8A))
                        Spacer(Modifier.height(2.dp))
                        Text(text = time.format(), fontSize = 15.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0F)
@Composable
private fun CircularTimeRangePickerPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularTimeRangePicker()
        }
    }
}
