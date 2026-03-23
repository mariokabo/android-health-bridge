package com.airhealth.bridge.health

import android.content.Context
import android.os.BatteryManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.airhealth.bridge.models.VitalReading
import java.time.Instant

class HealthConnectManager(private val context: Context) {
    private val healthClient by lazy { HealthConnectClient.getOrCreate(context) }

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class)
    )

    fun sdkStatus(): Int {
        return HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = healthClient.permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun readLatestVitals(windowHours: Long = 12): VitalReading {
        val now = Instant.now()
        val start = now.minusSeconds(windowHours * 3600)

        val heart = latestHeartRate(start, now)
        val spo2 = latestSpo2(start, now)
        val respiration = latestRespiration(start, now)
        val steps = summedSteps(start, now)
        val battery = phoneBatteryPercent()

        return VitalReading(
            timestampIso = now.toString(),
            heartRate = heart,
            spo2 = spo2,
            steps = steps,
            respiration = respiration,
            battery = battery
        )
    }

    private suspend fun latestHeartRate(start: Instant, end: Instant): Int? {
        val res = healthClient.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        val latestRecord = res.records.maxByOrNull { it.endTime }
        val latestSample = latestRecord?.samples?.maxByOrNull { it.time }
        return latestSample?.beatsPerMinute?.let { parseIntAny_(it) }
    }

    private suspend fun latestSpo2(start: Instant, end: Instant): Int? {
        val res = healthClient.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        val latest = res.records.maxByOrNull { it.time }
        return latest?.percentage?.let { parseSpo2Percent_(it) }
    }

    private suspend fun latestRespiration(start: Instant, end: Instant): Int? {
        val res = healthClient.readRecords(
            ReadRecordsRequest(
                recordType = RespiratoryRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        val latest = res.records.maxByOrNull { it.time }
        return latest?.rate?.let { parseIntAny_(it) }
    }

    private suspend fun summedSteps(start: Instant, end: Instant): Long? {
        val res = healthClient.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        val total = res.records.sumOf { it.count }
        return total
    }

    private fun phoneBatteryPercent(): Int? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (raw in 0..100) raw else null
    }

    companion object {
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    }

    private fun parseIntAny_(value: Any?): Int? {
        val v = value ?: return null
        return when (v) {
            is Int -> v
            is Long -> v.toInt()
            is Float -> v.toInt()
            is Double -> v.toInt()
            is Number -> v.toInt()
            else -> Regex("-?\\d+(?:\\.\\d+)?").find(v.toString())
                ?.value
                ?.toDoubleOrNull()
                ?.toInt()
        }
    }

    private fun parseSpo2Percent_(value: Any?): Int? {
        val d = parseDoubleAny_(value) ?: return null
        val pct = if (d <= 1.0) d * 100.0 else d
        return pct.toInt().coerceIn(0, 100)
    }

    private fun parseDoubleAny_(value: Any?): Double? {
        val v = value ?: return null
        return when (v) {
            is Double -> v
            is Float -> v.toDouble()
            is Long -> v.toDouble()
            is Int -> v.toDouble()
            is Number -> v.toDouble()
            else -> Regex("-?\\d+(?:\\.\\d+)?").find(v.toString())
                ?.value
                ?.toDoubleOrNull()
        }
    }
}
