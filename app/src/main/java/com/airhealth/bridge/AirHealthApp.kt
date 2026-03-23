package com.airhealth.bridge

import android.app.Application
import androidx.work.Configuration

class AirHealthApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
