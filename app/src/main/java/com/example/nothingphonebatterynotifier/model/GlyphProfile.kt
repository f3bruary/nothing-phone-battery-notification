package com.example.nothingphonebatterynotifier.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class GlyphProfile(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("name") val name: String,
    @SerializedName("batteryThreshold") val batteryThreshold: Int,
    @SerializedName("blinkDurationMs") val blinkDurationMs: Long,
    @SerializedName("blinkGapMs") val blinkGapMs: Long = 100,
    @SerializedName("repeatCount") val repeatCount: Int = 1,
    @SerializedName("intervalMs") val intervalMs: Long,
    @SerializedName("enabled") val enabled: Boolean = true
)
