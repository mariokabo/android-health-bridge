package com.airhealth.bridge.network

import com.airhealth.bridge.models.VitalReading
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WebhookApi {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun sendVitals(
        webhookUrl: String,
        token: String,
        travelerId: String,
        reading: VitalReading
    ): Result<String> {
        return runCatching {
            val payload = JSONObject().apply {
                put("action", "addVitals")
                put("token", token)
                put("travelerId", travelerId)
                put("heartRate", reading.heartRate)
                put("spo2", reading.spo2)
                put("steps", reading.steps)
                put("respiration", reading.respiration)
                put("battery", reading.battery)
                put("timestamp", reading.timestampIso)
                put("source", "android-health-connect")
            }

            val request = Request.Builder()
                .url(webhookUrl)
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: $body")
                }
                if (!body.contains("\"success\":true", ignoreCase = true)) {
                    error("Webhook failed: $body")
                }
                body
            }
        }
    }
}
