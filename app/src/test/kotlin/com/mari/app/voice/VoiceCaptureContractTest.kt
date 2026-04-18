package com.mari.app.voice

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceCaptureContractTest {

    private val contract = VoiceCaptureContract()

    @Test
    fun `parseResult returns Cancelled when resultCode is not OK`() {
        val result = contract.parseResult(Activity.RESULT_CANCELED, null)
        assertThat(result).isEqualTo(VoiceResult.Cancelled)
    }

    @Test
    fun `parseResult returns Cancelled when intent is null with OK code`() {
        val result = contract.parseResult(Activity.RESULT_OK, null)
        assertThat(result).isEqualTo(VoiceResult.Empty)
    }

    @Test
    fun `parseResult returns Empty when results list is empty`() {
        val intent = Intent().apply {
            putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf())
        }
        val result = contract.parseResult(Activity.RESULT_OK, intent)
        assertThat(result).isEqualTo(VoiceResult.Empty)
    }

    @Test
    fun `parseResult returns Empty when recognized text is blank`() {
        val intent = Intent().apply {
            putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("   "))
        }
        val result = contract.parseResult(Activity.RESULT_OK, intent)
        assertThat(result).isEqualTo(VoiceResult.Empty)
    }

    @Test
    fun `parseResult returns Success with trimmed text`() {
        val intent = Intent().apply {
            putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("  buy milk  "))
        }
        val result = contract.parseResult(Activity.RESULT_OK, intent)
        assertThat(result).isEqualTo(VoiceResult.Success("buy milk"))
    }

    @Test
    fun `parseResult uses first result only`() {
        val intent = Intent().apply {
            putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("first", "second"))
        }
        val result = contract.parseResult(Activity.RESULT_OK, intent)
        assertThat(result).isEqualTo(VoiceResult.Success("first"))
    }

    @Test
    fun `createIntent fires ACTION_RECOGNIZE_SPEECH with correct extras`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val intent = contract.createIntent(context, Unit)
        assertThat(intent.action).isEqualTo(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        assertThat(intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
            .isEqualTo(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        assertThat(intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0)).isEqualTo(1)
    }
}
