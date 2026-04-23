package com.mari.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateLatestDto(
    @SerialName("component") val component: String,
    @SerialName("track") val track: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_size_bytes") val fileSizeBytes: Long,
    @SerialName("sha256") val sha256: String,
    @SerialName("released_at") val releasedAt: String,
    @SerialName("notification_title") val notificationTitle: String,
    @SerialName("notification_text") val notificationText: String,
    @SerialName("changelog") val changelog: String? = null,
    @SerialName("min_installed_version_code") val minInstalledVersionCode: Int? = null,
)

@Serializable
data class AppUpdateReleaseNoteDto(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("released_at") val releasedAt: String,
    @SerialName("features") val features: List<String> = emptyList(),
    @SerialName("upgrades") val upgrades: List<String> = emptyList(),
    @SerialName("fixes") val fixes: List<String> = emptyList(),
)
