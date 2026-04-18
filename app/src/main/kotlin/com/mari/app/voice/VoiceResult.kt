package com.mari.app.voice

sealed interface VoiceResult {
    data class Success(val text: String) : VoiceResult
    data object Empty : VoiceResult
    data object Cancelled : VoiceResult
    data class Error(val reason: String) : VoiceResult
}
