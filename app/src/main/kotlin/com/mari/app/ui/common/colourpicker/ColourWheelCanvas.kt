package com.mari.app.ui.common.colourpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mari.app.ui.common.ColorUtils
import kotlin.math.max
import kotlin.math.min

private val HueSpectrum = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)

private enum class ColourPickerHitKind { HUE_BAR, SAT_VALUE, ALPHA_BAR, NONE }

private data class ColourPickerLayout(
    val hueRect: Rect,
    val satValueRect: Rect,
    val alphaRect: Rect?,
)

private fun buildColourPickerLayout(width: Float, height: Float, showAlphaBar: Boolean): ColourPickerLayout {
    val gutter = min(width, height) * 0.05f
    val sliderThickness = max(22f, width * 0.085f)
    val hueHeight = max(22f, height * 0.085f)
    val squareTop = gutter + hueHeight + gutter
    val availableHeight = (height - squareTop - gutter).coerceAtLeast(0f)
    val squareSize = min(
        availableHeight,
        width - gutter * 2f - if (showAlphaBar) sliderThickness + gutter else 0f,
    ).coerceAtLeast(0f)
    val squareLeft = gutter
    val hueRect = Rect(
        left = gutter,
        top = gutter,
        right = width - gutter,
        bottom = gutter + hueHeight,
    )
    val satValueRect = Rect(
        left = squareLeft,
        top = squareTop,
        right = squareLeft + squareSize,
        bottom = squareTop + squareSize,
    )
    val alphaRect = if (showAlphaBar) {
        Rect(
            left = satValueRect.right + gutter,
            top = squareTop,
            right = satValueRect.right + gutter + sliderThickness,
            bottom = squareTop + squareSize,
        )
    } else {
        null
    }
    return ColourPickerLayout(hueRect = hueRect, satValueRect = satValueRect, alphaRect = alphaRect)
}

private fun hueFromPosition(position: Offset, layout: ColourPickerLayout): Float {
    val width = layout.hueRect.width.coerceAtLeast(1f)
    return (((position.x - layout.hueRect.left) / width) * 360f).coerceIn(0f, 360f)
}

private fun saturationValueFromPosition(position: Offset, layout: ColourPickerLayout): Pair<Float, Float> {
    val saturation = ((position.x - layout.satValueRect.left) / layout.satValueRect.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val value = (1f - ((position.y - layout.satValueRect.top) / layout.satValueRect.height.coerceAtLeast(1f))).coerceIn(0f, 1f)
    return saturation to value
}

private fun alphaFromPosition(position: Offset, layout: ColourPickerLayout): Int {
    val rect = layout.alphaRect ?: return 255
    val alpha = (1f - ((position.y - rect.top) / rect.height.coerceAtLeast(1f))).coerceIn(0f, 1f)
    return (alpha * 255f).toInt().coerceIn(0, 255)
}

private suspend fun AwaitPointerEventScope.handleContinuousDrag(pointerId: PointerId, onPosition: (Offset) -> Unit) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
        onPosition(change.position)
        change.consume()
        if (!change.pressed) break
    }
}

private fun DrawScope.drawCheckerboard(rect: Rect) {
    val cell = 10.dp.toPx()
    var y = rect.top
    var row = 0
    while (y < rect.bottom) {
        var x = rect.left
        var column = row % 2
        while (x < rect.right) {
            drawRect(
                color = if (column % 2 == 0) Color(0xFFD7D7D7) else Color.White,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(
                    width = min(cell, rect.right - x),
                    height = min(cell, rect.bottom - y),
                ),
            )
            x += cell
            column++
        }
        y += cell
        row++
    }
}

private fun DrawScope.drawSliderThumb(center: Offset, vertical: Boolean, accent: Color) {
    val width = if (vertical) 18.dp.toPx() else 6.dp.toPx()
    val height = if (vertical) 6.dp.toPx() else 18.dp.toPx()
    drawRoundRect(
        color = accent,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = androidx.compose.ui.geometry.Size(width, height),
        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        style = Stroke(width = 1.2.dp.toPx()),
    )
}

@Composable
fun ColourPickerCanvas(
    hue: Float,
    saturation: Float,
    value: Float,
    alpha: Int,
    showAlphaBar: Boolean,
    onHueChange: (Float) -> Unit,
    onSaturationValueChange: (Float, Float) -> Unit,
    onAlphaChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val latestOnHueChange by rememberUpdatedState(onHueChange)
    val latestOnSaturationValueChange by rememberUpdatedState(onSaturationValueChange)
    val latestOnAlphaChange by rememberUpdatedState(onAlphaChange)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(showAlphaBar, canvasSize) {
                if (canvasSize == IntSize.Zero) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val layout = buildColourPickerLayout(
                        width = canvasSize.width.toFloat(),
                        height = canvasSize.height.toFloat(),
                        showAlphaBar = showAlphaBar,
                    )
                    when {
                        layout.hueRect.contains(down.position) -> {
                            latestOnHueChange(hueFromPosition(down.position, layout))
                            handleContinuousDrag(down.id) { position ->
                                latestOnHueChange(hueFromPosition(position, layout))
                            }
                        }

                        layout.satValueRect.contains(down.position) -> {
                            val (initialSaturation, initialValue) = saturationValueFromPosition(down.position, layout)
                            latestOnSaturationValueChange(initialSaturation, initialValue)
                            handleContinuousDrag(down.id) { position ->
                                val (nextSaturation, nextValue) = saturationValueFromPosition(position, layout)
                                latestOnSaturationValueChange(nextSaturation, nextValue)
                            }
                        }

                        layout.alphaRect?.contains(down.position) == true -> {
                            latestOnAlphaChange(alphaFromPosition(down.position, layout))
                            handleContinuousDrag(down.id) { position ->
                                latestOnAlphaChange(alphaFromPosition(position, layout))
                            }
                        }
                    }
                }
            },
    ) {
        val layout = buildColourPickerLayout(size.width, size.height, showAlphaBar)
        val hueOnly = ColorUtils.hsvToColor(hue, 1f, 1f)
        val currentColor = Color(android.graphics.Color.HSVToColor(alpha.coerceIn(0, 255), floatArrayOf(hue, saturation, value)))
        val borderColor = Color(0xFF252525)
        val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())

        drawRoundRect(
            brush = Brush.horizontalGradient(HueSpectrum),
            topLeft = layout.hueRect.topLeft,
            size = layout.hueRect.size,
            cornerRadius = corner,
        )
        drawRoundRect(
            color = borderColor,
            topLeft = layout.hueRect.topLeft,
            size = layout.hueRect.size,
            cornerRadius = corner,
            style = Stroke(width = 1.dp.toPx()),
        )

        drawRoundRect(
            color = hueOnly,
            topLeft = layout.satValueRect.topLeft,
            size = layout.satValueRect.size,
            cornerRadius = corner,
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)),
            topLeft = layout.satValueRect.topLeft,
            size = layout.satValueRect.size,
            cornerRadius = corner,
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            topLeft = layout.satValueRect.topLeft,
            size = layout.satValueRect.size,
            cornerRadius = corner,
        )
        drawRoundRect(
            color = borderColor,
            topLeft = layout.satValueRect.topLeft,
            size = layout.satValueRect.size,
            cornerRadius = corner,
            style = Stroke(width = 1.dp.toPx()),
        )

        layout.alphaRect?.let { alphaRect ->
            drawCheckerboard(alphaRect)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        currentColor.copy(alpha = 1f),
                        currentColor.copy(alpha = 0f),
                    ),
                ),
                topLeft = alphaRect.topLeft,
                size = alphaRect.size,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = borderColor,
                topLeft = alphaRect.topLeft,
                size = alphaRect.size,
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        val hueX = layout.hueRect.left + (hue.coerceIn(0f, 360f) / 360f) * layout.hueRect.width
        drawSliderThumb(
            center = Offset(hueX, layout.hueRect.center.y),
            vertical = false,
            accent = borderColor,
        )

        val satX = layout.satValueRect.left + saturation.coerceIn(0f, 1f) * layout.satValueRect.width
        val valueY = layout.satValueRect.top + (1f - value.coerceIn(0f, 1f)) * layout.satValueRect.height
        val selectorCenter = Offset(satX, valueY)
        drawCircle(
            color = Color.White,
            radius = 9.dp.toPx(),
            center = selectorCenter,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = borderColor,
            radius = 10.dp.toPx(),
            center = selectorCenter,
            style = Stroke(width = 1.dp.toPx()),
        )

        layout.alphaRect?.let { alphaRect ->
            val alphaY = alphaRect.top + (1f - (alpha.coerceIn(0, 255) / 255f)) * alphaRect.height
            drawSliderThumb(
                center = Offset(alphaRect.center.x, alphaY),
                vertical = true,
                accent = borderColor,
            )
        }
    }
}
