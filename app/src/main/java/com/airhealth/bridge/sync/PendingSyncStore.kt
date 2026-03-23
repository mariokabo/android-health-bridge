package com.airhealth.bridge.sync

import android.content.Context
import com.airhealth.bridge.models.VitalReading
import org.json.JSONArray
import org.json.JSONObject

class PendingSyncStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun enqueue(reading: VitalReading) {
        val current = getAll().toMutableList()
        current += reading
        saveAll(current.takeLast(MAX_QUEUE_SIZE))
    }

    fun getAll(): List<VitalReading> {
        val raw = prefs.getString(KEY_QUEUE_JSON, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        VitalReading(
                            timestampIso = obj.optString("timestampIso", ""),
                            heartRate = obj.optIntOrNull("heartRate"),
                            spo2 = obj.optIntOrNull("spo2"),
                            steps = obj.optLongOrNull("steps"),
                            respiration = obj.optIntOrNull("respiration"),
                            battery = obj.optIntOrNull("battery")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun size(): Int = getAll().size

    fun saveAll(items: List<VitalReading>) {
        val arr = JSONArray()
        items.forEach { reading ->
            arr.put(
                JSONObject().apply {
                    put("timestampIso", reading.timestampIso)
                    put("heartRate", reading.heartRate)
                    put("spo2", reading.spo2)
                    put("steps", reading.steps)
                    put("respiration", reading.respiration)
                    put("battery", reading.battery)
                }
            )
        }
        prefs.edit().putString(KEY_QUEUE_JSON, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "airhealth_bridge_prefs"
        private const val KEY_QUEUE_JSON = "pending_sync_queue"
        private const val MAX_QUEUE_SIZE = 200
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
