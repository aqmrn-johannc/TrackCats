package com.trackcats.app

import android.app.Application
import com.trackcats.app.data.Store
import com.trackcats.app.work.Alerts

class TrackCatsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Alerts.createChannel(this)
        // Scheduling can fail in unusual process states (and in tests); the app works without it.
        runCatching { Alerts.schedule(this) }
    }
}
