package com.airhealth.bridge.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.airhealth.bridge.AppPreferences
import com.airhealth.bridge.health.HealthConnectManager
import com.airhealth.bridge.models.VitalReading
import com.airhealth.bridge.network.WebhookApi
import com.airhealth.bridge.sync.PendingSyncStore
import java.time.Instant

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)
        val queue = PendingSyncStore(applicationContext)
        val webhook = prefs.webhookUrl
        val token = prefs.apiToken
        val travelerId = prefs.travelerId

        if (webhook.isBlank() || token.isBlank() || travelerId.isBlank()) {
            prefs.lastSyncStatus = "فشل: إعدادات ناقصة (Webhook/Token/Traveler)"
            return Result.failure()
        }

        val manager = HealthConnectManager(applicationContext)
        if (!manager.hasAllPermissions()) {
            prefs.lastSyncStatus = "مؤجل: الصلاحيات غير مكتملة"
            return Result.retry()
        }

        val reading = runCatching { manager.readLatestVitals() }
            .getOrElse {
                prefs.lastSyncStatus = "مؤجل: فشل قراءة Health Connect"
                return Result.retry()
            }

        prefs.saveLastReading(reading)
        queue.enqueue(reading)

        val pending = queue.getAll()
        val unsent = flushQueue(
            pending = pending,
            webhook = webhook,
            token = token,
            travelerId = travelerId
        )

        return if (unsent.isEmpty()) {
            queue.saveAll(emptyList())
            prefs.lastSyncStatus = "تمت المزامنة بنجاح (${pending.size} قياس)"
            prefs.lastSyncAtIso = Instant.now().toString()
            Result.success()
        } else {
            queue.saveAll(unsent)
            prefs.lastSyncStatus = "مؤجل: بقي ${unsent.size} قياس في الطابور"
            prefs.lastSyncAtIso = Instant.now().toString()
            Result.retry()
        }
    }

    private fun flushQueue(
        pending: List<VitalReading>,
        webhook: String,
        token: String,
        travelerId: String
    ): List<VitalReading> {
        if (pending.isEmpty()) return emptyList()

        for (i in pending.indices) {
            val reading = pending[i]
            val sent = WebhookApi.sendVitals(
                webhookUrl = webhook,
                token = token,
                travelerId = travelerId,
                reading = reading
            )

            if (sent.isFailure) {
                return pending.drop(i)
            }
        }
        return emptyList()
    }
}
