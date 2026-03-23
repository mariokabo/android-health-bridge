package com.airhealth.bridge

import android.content.Context
import com.airhealth.bridge.models.VitalReading
import org.json.JSONObject

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBHOOK_URL, value.trim()).apply()

    var apiToken: String
        get() = prefs.getString(KEY_API_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_TOKEN, value.trim()).apply()

    var travelerId: String
        get() = prefs.getString(KEY_TRAVELER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TRAVELER_ID, value.trim()).apply()

    var syncIntervalMinutes: Long
        get() = prefs.getLong(KEY_SYNC_INTERVAL, 15L)
        set(value) = prefs.edit().putLong(KEY_SYNC_INTERVAL, value.coerceAtLeast(15L)).apply()

    var lastSyncStatus: String
        get() = prefs.getString(KEY_LAST_SYNC_STATUS, "لم تتم مزامنة بعد") ?: "لم تتم مزامنة بعد"
        set(value) = prefs.edit().putString(KEY_LAST_SYNC_STATUS, value).apply()

    var lastSyncAtIso: String
        get() = prefs.getString(KEY_LAST_SYNC_AT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SYNC_AT, value).apply()

    fun saveLastReading(reading: VitalReading) {
        val json = JSONObject().apply {
            put("timestampIso", reading.timestampIso)
            put("heartRate", reading.heartRate)
            put("spo2", reading.spo2)
            put("steps", reading.steps)
            put("respiration", reading.respiration)
            put("battery", reading.battery)
        }
        prefs.edit().putString(KEY_LAST_READING_JSON, json.toString()).apply()
    }

    fun getLastReading(): VitalReading? {
        val raw = prefs.getString(KEY_LAST_READING_JSON, "") ?: ""
        if (raw.isBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            VitalReading(
                timestampIso = json.optString("timestampIso", ""),
                heartRate = json.optIntOrNull("heartRate"),
                spo2 = json.optIntOrNull("spo2"),
                steps = json.optLongOrNull("steps"),
                respiration = json.optIntOrNull("respiration"),
                battery = json.optIntOrNull("battery")
            )
        }.getOrNull()
    }

    companion object {
        private const val PREFS_NAME = "airhealth_bridge_prefs"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_TRAVELER_ID = "traveler_id"
        private const val KEY_SYNC_INTERVAL = "sync_interval"
        private const val KEY_LAST_READING_JSON = "last_reading_json"
        private const val KEY_LAST_SYNC_STATUS = "last_sync_status"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return runCatching { getInt(key) }.getOrNull()
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return runCatching { getLong(key) }.getOrNull()
}
