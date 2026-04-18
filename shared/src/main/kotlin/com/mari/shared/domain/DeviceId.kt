package com.mari.shared.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DeviceId {
    @SerialName("phone") PHONE,
    @SerialName("watch") WATCH,
}
