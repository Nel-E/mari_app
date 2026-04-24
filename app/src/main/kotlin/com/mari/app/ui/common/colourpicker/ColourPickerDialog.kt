package com.mari.app.ui.common.colourpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mari.app.ui.common.ColorUtils
import com.mari.app.ui.common.toHexString
import com.mari.app.ui.common.toRgbaHexString

@Composable
fun ColourPickerDialog(
    initialColorHex: String,
    title: String = "Pick colour",
    confirmLabel: String = "Done",
    showAlphaField: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initialColor = ColorUtils.parseHexOrFallback(initialColorHex, Color(0xFF607D8B))
    ColourPickerDialogInternal(
        initialColor = initialColor,
        title = title,
        confirmLabel = confirmLabel,
        showAlphaField = showAlphaField,
        showHexInput = true,
        onDismiss = onDismiss,
        onConfirm = { color ->
            onConfirm(if (showAlphaField) color.toRgbaHexString() else color.toHexString())
        },
    )
}

@Composable
private fun ColourPickerDialogInternal(
    initialColor: Color,
    title: String,
    confirmLabel: String,
    showAlphaField: Boolean,
    showHexInput: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val initialHsv = ColorUtils.toHsv(initialColor)
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember(initialColor) { mutableIntStateOf((initialColor.alpha * 255).toInt().coerceIn(0, 255)) }
    var editorInput by remember(initialColor) {
        mutableStateOf(if (showAlphaField) initialColor.toRgbaHexString() else initialColor.toHexString())
    }

    fun currentColor(): Color = Color(android.graphics.Color.HSVToColor(alpha, floatArrayOf(hue, saturation, value)))
    fun editorTextColor(color: Color): Color = if (color.luminance() > 0.45f) Color.Black else Color.White

    fun syncEditorFromState() {
        editorInput = if (showAlphaField) currentColor().toRgbaHexString() else currentColor().toHexString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.05f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFF5F5F5)),
                ) {
                    ColourPickerCanvas(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        alpha = alpha,
                        showAlphaBar = showAlphaField,
                        onHueChange = { nextHue ->
                            hue = nextHue
                            syncEditorFromState()
                        },
                        onSaturationValueChange = { nextSaturation, nextValue ->
                            saturation = nextSaturation
                            value = nextValue
                            syncEditorFromState()
                        },
                        onAlphaChange = { nextAlpha ->
                            alpha = nextAlpha
                            syncEditorFromState()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (showHexInput) {
                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = editorInput,
                        onValueChange = { raw ->
                            val normalized = ColorUtils.normalizeColorInput(raw, allowAlpha = showAlphaField)
                            editorInput = normalized
                            if (normalized.length == 7 || (showAlphaField && normalized.length == 9)) {
                                ColorUtils.parseHexOrNull(normalized)?.let { parsed ->
                                    val parsedHsv = ColorUtils.toHsv(parsed)
                                    hue = parsedHsv[0]
                                    saturation = parsedHsv[1]
                                    value = parsedHsv[2]
                                    if (showAlphaField) {
                                        alpha = (parsed.alpha * 255).toInt().coerceIn(0, 255)
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            textAlign = TextAlign.Center,
                            color = editorTextColor(currentColor()),
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = currentColor(),
                            unfocusedContainerColor = currentColor(),
                            disabledContainerColor = currentColor(),
                            focusedTextColor = editorTextColor(currentColor()),
                            unfocusedTextColor = editorTextColor(currentColor()),
                            cursorColor = editorTextColor(currentColor()),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(currentColor())
                    onDismiss()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
