# AirHealth Android Bridge

Android app (Kotlin) to read vitals from Health Connect and send them automatically to your Google Apps Script webhook.

## Features
- Read latest vitals from Health Connect:
  - Heart Rate
  - SpO2
  - Steps
  - Respiratory Rate
- Send payload to your existing Apps Script endpoint (`action=addVitals`)
- Periodic background sync via WorkManager
- Manual "Sync Now"
- Local retry queue (offline-safe): failed sends are queued and retried automatically
- Latest reading preview inside app (with sync status + queue size)

## Project Path
- Android project root: `android-health-bridge/`

## Quick Start (Android Studio)
1. Open Android Studio.
2. Open folder: `android-health-bridge`.
3. Let Gradle sync.
4. Build and run on your phone.

## First App Setup (inside the app)
1. Install/update Health Connect from Play Store.
2. Tap **طلب الصلاحيات** and allow all requested permissions.
3. Fill:
   - Traveler ID (e.g. `TRV_12345`)
   - Webhook URL (from your Apps Script web app)
   - API Token (`SECRET_TOKEN` value from apps-script.gs)
  - Interval (minimum 5 min)
4. Tap **حفظ + تفعيل Auto Sync**.
5. Optional: tap **Sync Now** once to test immediate send.

## Payload Sent
```json
{
  "action": "addVitals",
  "token": "...",
  "travelerId": "TRV_12345",
  "heartRate": 82,
  "spo2": 97,
  "steps": 4210,
  "respiration": 16,
  "battery": 78,
  "timestamp": "2026-03-23T10:12:45.000Z",
  "source": "android-health-connect"
}
```

## Notes
- Sync every 5 minutes is supported via chained one-time work.
- Android battery optimization/doze may delay some runs on certain devices.
- If your watch writes data to Health Connect, this app will pick it up in each sync window.
- For most devices, Health Connect package is:
  - `com.google.android.apps.healthdata`

## Optional GitHub CI Build
Add Gradle wrapper then use GitHub Actions to build APK automatically:
1. Generate wrapper once locally:
   - `gradle wrapper`
2. Push to GitHub.
3. Use workflow in `.github/workflows/android-build.yml`.

## No Android Studio? Build from GitHub only
You can build APK fully from GitHub web UI:

1. Push this folder to GitHub repo root (or keep as subfolder).
2. Open repo on GitHub > **Actions**.
3. Run workflow **Android Build** (button: *Run workflow*).
4. Wait until success.
5. Download artifact: **app-debug-apk**.
6. Transfer `app-debug.apk` to phone and install.

Phone install notes:
- Enable **Install unknown apps** for your browser/files app.
- Open APK and install.

