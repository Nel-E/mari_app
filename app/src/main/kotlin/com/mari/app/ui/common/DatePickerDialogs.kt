package com.mari.app.ui.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSingleDatePickerDialog(
    currentIsoDate: String?,
    minimumIsoDate: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val todayMillis = LocalDate.now()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
    val minimumMillis = parseIsoDateToMillis(minimumIsoDate)
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = maxOf(
            parseIsoDateToMillis(currentIsoDate) ?: todayMillis,
            minimumMillis ?: Long.MIN_VALUE,
        ),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = maxOf(
                        pickerState.selectedDateMillis ?: todayMillis,
                        minimumMillis ?: Long.MIN_VALUE,
                    )
                    onConfirm(millisToIsoDate(selected))
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

fun parseIsoDateToMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

fun millisToIsoDate(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}
