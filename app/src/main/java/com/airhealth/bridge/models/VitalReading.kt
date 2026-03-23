package com.airhealth.bridge.models

data class VitalReading(
    val timestampIso: String,
    val heartRate: Int?,
    val spo2: Int?,
    val steps: Long?,
    val respiration: Int?,
    val battery: Int?
)
