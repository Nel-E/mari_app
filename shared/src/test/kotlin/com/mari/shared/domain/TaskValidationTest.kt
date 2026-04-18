package com.mari.shared.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaskValidationTest {

    @Test
    fun `blank description fails`() {
        assertThat(TaskValidation.validateDescription("").isFailure).isTrue()
        assertThat(TaskValidation.validateDescription("   ").isFailure).isTrue()
    }

    @Test
    fun `valid description succeeds`() {
        val result = TaskValidation.validateDescription("Buy milk")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("Buy milk")
    }

    @Test
    fun `description is trimmed`() {
        val result = TaskValidation.validateDescription("  hello  ")
        assertThat(result.getOrNull()).isEqualTo("hello")
    }

    @Test
    fun `description over 500 chars fails`() {
        val long = "a".repeat(501)
        assertThat(TaskValidation.validateDescription(long).isFailure).isTrue()
    }

    @Test
    fun `description of exactly 500 chars passes`() {
        val exactly500 = "a".repeat(500)
        assertThat(TaskValidation.validateDescription(exactly500).isSuccess).isTrue()
    }
}
