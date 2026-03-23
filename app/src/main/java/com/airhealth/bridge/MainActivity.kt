package com.airhealth.bridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.airhealth.bridge.health.HealthConnectManager
import com.airhealth.bridge.models.VitalReading
import com.airhealth.bridge.sync.PendingSyncStore
import com.airhealth.bridge.worker.WorkScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var healthManager: HealthConnectManager
    private lateinit var permissionLauncher: ActivityResultLauncher<Set<String>>
    private var permissionCallback: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        healthManager = HealthConnectManager(this)
        permissionLauncher = registerForActivityResult(healthManager.permissionContract()) { granted ->
            permissionCallback?.invoke(granted.containsAll(healthManager.requiredPermissions))
        }

        setContent {
            MaterialTheme {
                BridgeScreen(
                    prefs = AppPreferences(this),
                    healthManager = healthManager,
                    onRequestPermissions = { callback ->
                        permissionCallback = callback
                        permissionLauncher.launch(healthManager.requiredPermissions)
                    }
                )
            }
        }
    }
}

@Composable
private fun BridgeScreen(
    prefs: AppPreferences,
    healthManager: HealthConnectManager,
    onRequestPermissions: ((Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val queueStore = remember { PendingSyncStore(context) }

    var webhookUrl by remember { mutableStateOf(prefs.webhookUrl) }
    var apiToken by remember { mutableStateOf(prefs.apiToken) }
    var travelerId by remember { mutableStateOf(prefs.travelerId) }
    var intervalMinutes by remember { mutableLongStateOf(prefs.syncIntervalMinutes) }

    var hasPermissions by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf(prefs.lastSyncStatus.ifBlank { "جاهز للإعداد" }) }
    var lastReading by remember { mutableStateOf<VitalReading?>(prefs.getLastReading()) }
    var pendingCount by remember { mutableStateOf(queueStore.size()) }
    var lastSyncAt by remember { mutableStateOf(prefs.lastSyncAtIso) }

    LaunchedEffect(Unit) {
        hasPermissions = runCatching { healthManager.hasAllPermissions() }.getOrDefault(false)
        pendingCount = queueStore.size()
        lastReading = prefs.getLastReading()
        statusText = prefs.lastSyncStatus
        lastSyncAt = prefs.lastSyncAtIso
    }

    val sdkStatus = healthManager.sdkStatus()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("AirHealth", style = MaterialTheme.typography.headlineSmall)
        Text("Developer: Maru Falts")
        Text("ينسخ العلامات الحيوية من Health Connect إلى Google Apps Script بشكل تلقائي.")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("حالة Health Connect: ${sdkStatusLabel(sdkStatus)}")
                Text("الصلاحيات: ${if (hasPermissions) "مفعلة ✅" else "غير مفعلة ❌"}")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        onRequestPermissions { granted ->
                            hasPermissions = granted
                            statusText = if (granted) "تم منح الصلاحيات" else "الصلاحيات ناقصة"
                        }
                    }) {
                        Text("طلب الصلاحيات")
                    }

                    Button(onClick = { openHealthConnectStore(context) }) {
                        Text("تثبيت/تحديث Health Connect")
                    }
                }
            }
        }

        OutlinedTextField(
            value = travelerId,
            onValueChange = { travelerId = it },
            label = { Text("Traveler ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = webhookUrl,
            onValueChange = { webhookUrl = it },
            label = { Text("Webhook URL (Apps Script)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = apiToken,
            onValueChange = { apiToken = it },
            label = { Text("API Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = intervalMinutes.toString(),
            onValueChange = { intervalMinutes = it.toLongOrNull()?.coerceAtLeast(5L) ?: 5L },
            label = { Text("Sync interval (minutes, min 5)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                prefs.travelerId = travelerId
                prefs.webhookUrl = webhookUrl
                prefs.apiToken = apiToken
                prefs.syncIntervalMinutes = intervalMinutes
                prefs.autoSyncEnabled = true
                WorkScheduler.schedulePeriodic(context, intervalMinutes)
                statusText = "تم الحفظ وتفعيل المزامنة الدورية"
                Toast.makeText(context, "Saved + Auto Sync Enabled", Toast.LENGTH_SHORT).show()
            }) {
                Text("حفظ + تفعيل Auto Sync")
            }

            Button(onClick = {
                prefs.travelerId = travelerId
                prefs.webhookUrl = webhookUrl
                prefs.apiToken = apiToken
                prefs.syncIntervalMinutes = intervalMinutes
                WorkScheduler.syncNow(context)
                statusText = "تم إرسال مزامنة فورية"
                pendingCount = queueStore.size()
            }) {
                Text("Sync Now")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("آخر قراءة محفوظة")
                Text("الوقت: ${lastReading?.timestampIso ?: "-"}")
                Text("Heart Rate: ${lastReading?.heartRate ?: "-"}")
                Text("SpO2: ${lastReading?.spo2 ?: "-"}")
                Text("Steps: ${lastReading?.steps ?: "-"}")
                Text("Respiration: ${lastReading?.respiration ?: "-"}")
                Text("Battery: ${lastReading?.battery ?: "-"}")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("حالة المزامنة")
                Text("الرسالة: $statusText")
                Text("آخر وقت مزامنة: ${lastSyncAt.ifBlank { "-" }}")
                Text("الطابور المحلي: $pendingCount")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val quick = runCatching { healthManager.readLatestVitals() }.getOrNull()
                            if (quick != null) {
                                prefs.saveLastReading(quick)
                                lastReading = quick
                                statusText = "تم تحديث آخر قراءة من Health Connect"
                            } else {
                                statusText = "فشل قراءة بيانات Health Connect"
                            }
                        }
                    }) {
                        Text("قراءة الآن (بدون إرسال)")
                    }

                    Button(onClick = {
                        pendingCount = queueStore.size()
                        statusText = prefs.lastSyncStatus
                        lastSyncAt = prefs.lastSyncAtIso
                        lastReading = prefs.getLastReading()
                    }) {
                        Text("تحديث الطابور")
                    }
                }
            }
        }

        Button(onClick = {
            scope.launch {
                val ok = runCatching { healthManager.hasAllPermissions() }.getOrDefault(false)
                hasPermissions = ok
                statusText = if (ok) "الصلاحيات مكتملة" else "الصلاحيات غير مكتملة"
                pendingCount = queueStore.size()
                lastReading = prefs.getLastReading()
                lastSyncAt = prefs.lastSyncAtIso
            }
        }) {
            Text("تحديث الحالة")
        }

        Text("الحالة: $statusText")
    }
}

private fun openHealthConnectStore(context: android.content.Context) {
    val packageName = HealthConnectManager.HEALTH_CONNECT_PACKAGE
    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))

    runCatching { context.startActivity(marketIntent) }
        .onFailure { context.startActivity(webIntent) }
}

private fun sdkStatusLabel(status: Int): String {
    return when (status) {
        HealthConnectClient.SDK_AVAILABLE -> "متاح"
        HealthConnectClient.SDK_UNAVAILABLE -> "غير متاح على الجهاز"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "يحتاج تحديث"
        else -> "غير معروف ($status)"
    }
}
