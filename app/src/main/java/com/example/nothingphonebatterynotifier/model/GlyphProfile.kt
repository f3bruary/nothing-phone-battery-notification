package com.example.nothingphonebatterynotifier.model

import java.util.UUID

data class GlyphProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val batteryThreshold: Int,
    val blinkDurationMs: Long,
    val blinkGapMs: Long = 100,
    val repeatCount: Int = 1,
    val intervalMs: Long,
    val enabled: Boolean = true
)
