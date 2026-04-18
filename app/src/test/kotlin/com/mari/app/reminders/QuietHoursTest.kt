package com.mari.app.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class QuietHoursTest {

    private val window = QuietWindow(startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
    private val dayWindow = QuietWindow(startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)

    // --- overnight window (22:00–07:00) ---

    @Test
    fun `overnight - time inside window after midnight is suppressed`() {
        assertTrue(QuietHours.isSuppressed(LocalTime.of(2, 0), window))
    }

    @Test
    fun `overnight - time inside window before midnight is suppressed`() {
        assertTrue(QuietHours.isSuppressed(LocalTime.of(23, 30), window))
    }

    @Test
    fun `overnight - time at window start is suppressed`() {
        assertTrue(QuietHours.isSuppressed(LocalTime.of(22, 0), window))
    }

    @Test
    fun `overnight - time at window end is not suppressed`() {
        assertFalse(QuietHours.isSuppressed(LocalTime.of(7, 0), window))
    }

    @Test
    fun `overnight - midday is not suppressed`() {
        assertFalse(QuietHours.isSuppressed(LocalTime.of(12, 0), window))
    }

    @Test
    fun `overnight - one minute before start is not suppressed`() {
        assertFalse(QuietHours.isSuppressed(LocalTime.of(21, 59), window))
    }

    @Test
    fun `overnight - one minute before end is suppressed`() {
        assertTrue(QuietHours.isSuppressed(LocalTime.of(6, 59), window))
    }

    // --- same-day window (09:00–17:00) ---

    @Test
    fun `day window - time inside is suppressed`() {
        assertTrue(QuietHours.isSuppressed(LocalTime.of(13, 0), dayWindow))
    }

    @Test
    fun `day window - time at start is suppressed`() {
        assertTrue(QuietHours.isSuppressed(LocalTime.of(9, 0), dayWindow))
    }

    @Test
    fun `day window - time at end is not suppressed`() {
        assertFalse(QuietHours.isSuppressed(LocalTime.of(17, 0), dayWindow))
    }

    @Test
    fun `day window - time before start is not suppressed`() {
        assertFalse(QuietHours.isSuppressed(LocalTime.of(8, 59), dayWindow))
    }

    @Test
    fun `day window - time after end is not suppressed`() {
        assertFalse(QuietHours.isSuppressed(LocalTime.of(18, 0), dayWindow))
    }
}
