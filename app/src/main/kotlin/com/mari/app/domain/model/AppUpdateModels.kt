package com.mari.app.domain.model

data class AppUpdateInfo(
    val track: String,
    val component: String,
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val releasedAt: String,
    val notificationTitle: String,
    val notificationText: String,
    val changelog: String?,
    val downloadUrl: String,
    val minInstalledVersionCode: Int? = null,
)

data class AppUpdateReleaseNote(
    val versionCode: Int,
    val versionName: String,
    val releasedAt: String,
    val features: List<String>,
    val upgrades: List<String>,
    val fixes: List<String>,
)

data class AppUpdateLocalState(
    val autoCheckEnabled: Boolean = true,
    val track: UpdateTrack = UpdateTrack.STABLE,
    val checkIntervalHours: Int = 6,
    val lastCheckAt: String? = null,
    val availableUpdate: AppUpdateInfo? = null,
    val releaseNotes: List<AppUpdateReleaseNote> = emptyList(),
    val lastNotifiedVersionCode: Int? = null,
    val lastAcknowledgedVersionCode: Int? = null,
)

enum class UpdateTrack(val wire: String) {
    STABLE("stable"),
    BETA("beta"),
}
