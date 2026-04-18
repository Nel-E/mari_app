package com.mari.app.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContract

class VoiceCaptureContract : ActivityResultContract<Unit, VoiceResult>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): VoiceResult {
        if (resultCode != Activity.RESULT_OK) return VoiceResult.Cancelled
        val text = intent
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            ?: return VoiceResult.Empty
        return if (text.isBlank()) VoiceResult.Empty else VoiceResult.Success(text)
    }
}
