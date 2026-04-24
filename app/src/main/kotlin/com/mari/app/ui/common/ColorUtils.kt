package com.mari.app.ui.common

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

object ColorUtils {
    fun parseHexOrNull(hex: String?): Color? {
        return parseColorIntOrNull(hex)?.let(::Color)
    }

    fun parseHexOrFallback(hex: String?, fallback: Color = Color.Gray): Color {
        return parseHexOrNull(hex) ?: fallback
    }

    fun parseColorIntOrNull(hex: String?): Int? {
        val digits = normalizeHexDigits(hex)
        if (digits.length != 6 && digits.length != 8) return null
        return runCatching {
            if (digits.length == 6) {
                val rgb = digits.toLong(16).toInt()
                android.graphics.Color.argb(
                    255,
                    (rgb shr 16) and 0xFF,
                    (rgb shr 8) and 0xFF,
                    rgb and 0xFF,
                )
            } else {
                val rgba = digits.toLong(16)
                val red = ((rgba shr 24) and 0xFF).toInt()
                val green = ((rgba shr 16) and 0xFF).toInt()
                val blue = ((rgba shr 8) and 0xFF).toInt()
                val alpha = (rgba and 0xFF).toInt()
                android.graphics.Color.argb(alpha, red, green, blue)
            }
        }.getOrNull()
    }

    fun toHexString(color: Color, includeAlpha: Boolean = false): String {
        val red = (color.red * 255).roundToInt().coerceIn(0, 255)
        val green = (color.green * 255).roundToInt().coerceIn(0, 255)
        val blue = (color.blue * 255).roundToInt().coerceIn(0, 255)
        val alpha = (color.alpha * 255).roundToInt().coerceIn(0, 255)
        return if (includeAlpha) {
            "#%02X%02X%02X%02X".format(red, green, blue, alpha)
        } else {
            "#%02X%02X%02X".format(red, green, blue)
        }
    }

    fun toArgbInt(color: Color): Int {
        return android.graphics.Color.argb(
            (color.alpha * 255).roundToInt().coerceIn(0, 255),
            (color.red * 255).roundToInt().coerceIn(0, 255),
            (color.green * 255).roundToInt().coerceIn(0, 255),
            (color.blue * 255).roundToInt().coerceIn(0, 255),
        )
    }

    fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
        return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    fun toHsv(color: Color): FloatArray {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgbInt(color), hsv)
        return hsv
    }

    fun normalizeColorInput(input: String, allowAlpha: Boolean = false): String {
        val maxDigits = if (allowAlpha) 8 else 6
        return "#${normalizeHexDigits(input).take(maxDigits)}"
    }

    private fun normalizeHexDigits(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return buildString {
            for (char in input.trim().uppercase()) {
                if (char.isDigit() || char in 'A'..'F') append(char)
            }
        }
    }
}

fun Color.toHexString(): String = ColorUtils.toHexString(this)

fun Color.toRgbaHexString(): String = ColorUtils.toHexString(this, includeAlpha = true)
